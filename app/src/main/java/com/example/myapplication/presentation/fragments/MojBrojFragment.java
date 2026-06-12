package com.example.myapplication.presentation.fragments;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private static final long ROUND_DURATION_MS  = 60_000;
    private static final long STOP_AUTO_DELAY_MS = 5_000;

    private GameViewModel vm;
    private String gameId;
    private String myUsername;

    private TextView     tvStatus;
    private TextView     tvTimer;
    private MaterialButton btnStopTarget;
    private TextView     tvTarget;
    private MaterialButton btnStopNumbers;
    private LinearLayout  layoutExprInput;
    private TextView       tvExpression;
    private TextView       tvExprError;
    private LinearLayout   layoutNumButtons;
    private MaterialButton btnBackspace;
    private MaterialButton btnClearExpr;
    private MaterialButton btnSubmitExpr;
    private TextView       tvSubmittedResult;

    private final StringBuilder expression       = new StringBuilder();
    private final List<Integer>  usedNumberIndices = new ArrayList<>();

    private String  activePhase      = null;
    private boolean isRoundOwner     = false;
    private boolean targetRevealed   = false;
    private boolean numbersRevealed  = false;
    private boolean submitted        = false;
    private boolean scoringDone      = false;
    private int     targetNumber     = 0;
    private List<Integer> availableNumbers = new ArrayList<>();

    // The absolute state lock to prevent database echo loops
    private final Set<String> completedPhases = new HashSet<>();

    private CountDownTimer roundTimer;
    private CountDownTimer stopAutoTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float SHAKE_THRESHOLD = 12f;
    private long lastShakeTime = 0;

    public MojBrojFragment() {
        super(R.layout.fragment_moj_broj);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm         = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId     = getArguments().getString("gameId");
        myUsername = getArguments().getString("myUsername");
        int roundNumber = getArguments() != null ? getArguments().getInt("roundNumber", 1) : 1;

        tvStatus          = view.findViewById(R.id.tvMojBrojStatus);
        tvTimer           = view.findViewById(R.id.tvMojBrojTimer);
        btnStopTarget     = view.findViewById(R.id.btnStopTarget);
        tvTarget          = view.findViewById(R.id.tvTarget);
        btnStopNumbers    = view.findViewById(R.id.btnStopNumbers);
        layoutExprInput   = view.findViewById(R.id.layoutExprInput);
        tvExpression      = view.findViewById(R.id.tvExpression);
        tvExprError       = view.findViewById(R.id.tvExprError);
        layoutNumButtons  = view.findViewById(R.id.layoutNumButtons);
        btnBackspace      = view.findViewById(R.id.btnBackspace);
        btnClearExpr      = view.findViewById(R.id.btnClearExpr);
        btnSubmitExpr     = view.findViewById(R.id.btnSubmitExpr);
        tvSubmittedResult = view.findViewById(R.id.tvSubmittedResult);

        tvStatus.setText("Moj broj  •  Runda " + roundNumber + "/2");

        btnStopTarget.setOnClickListener(v -> onStopTarget());
        btnStopNumbers.setOnClickListener(v -> onStopNumbers());
        btnSubmitExpr.setOnClickListener(v -> submitExpression());
        btnBackspace.setOnClickListener(v -> onBackspace());
        btnClearExpr.setOnClickListener(v -> onClearExpr());
        view.findViewById(R.id.btnOpPlus).setOnClickListener(v -> appendToExpr("+"));
        view.findViewById(R.id.btnOpMinus).setOnClickListener(v -> appendToExpr("-"));
        view.findViewById(R.id.btnOpMul).setOnClickListener(v -> appendToExpr("*"));
        view.findViewById(R.id.btnOpDiv).setOnClickListener(v -> appendToExpr("/"));
        view.findViewById(R.id.btnOpOpenParen).setOnClickListener(v -> appendToExpr("("));
        view.findViewById(R.id.btnOpCloseParen).setOnClickListener(v -> appendToExpr(")"));

        sensorManager  = (SensorManager) requireContext().getSystemService(android.content.Context.SENSOR_SERVICE);
        accelerometer  = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        // Unified Observer
        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room != null) handleStateUpdate(room);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelRoundTimer();
        cancelStopAutoTimer();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        double magnitude = Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH;
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 1000) {
                lastShakeTime = now;
                if (!targetRevealed) onStopTarget();
                else if (!numbersRevealed) onStopNumbers();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // --- MASTER STATE CONTROLLER ---
    private void handleStateUpdate(GameRoom room) {
        if (!"mojbroj".equals(room.getCurrentMinigameType())) return;
        String phase = room.getRoundPhase();
        if (phase == null) return;

        if ("MINIGAME_DONE".equals(phase)) {
            cancelRoundTimer();
            cancelStopAutoTimer();
            showWaiting("Kraj runde!");
            return;
        }

        if (completedPhases.contains(phase)) return;

        if (!phase.equals(activePhase)) {
            if (isPlayablePhase(phase)) {
                startRound(room, phase);
            } else {
                showWaiting("Čekaj...");
            }
            return;
        }

        if (activePhase != null && activePhase.equals(phase)) {
            processMidRoundEvents(room);
        }
    }

    private boolean isPlayablePhase(String phase) {
        return phase.equals("P1_TURN") || phase.equals("P2_TURN");
    }

    private void startRound(GameRoom room, String phase) {
        activePhase     = phase;
        targetRevealed  = false;
        numbersRevealed = false;
        submitted       = false;
        scoringDone     = false;
        availableNumbers.clear();
        usedNumberIndices.clear();
        expression.setLength(0);
        targetNumber    = 0;

        isRoundOwner = phase.equals("P1_TURN")
                ? myUsername.equals(room.getPlayerOne())
                : myUsername.equals(room.getPlayerTwo());

        tvTarget.setVisibility(View.GONE);
        tvTarget.setText("---");
        btnStopNumbers.setVisibility(View.GONE);
        layoutExprInput.setVisibility(View.GONE);
        tvSubmittedResult.setVisibility(View.GONE);
        tvTimer.setVisibility(View.INVISIBLE);

        String currentRoundText = phase.equals("P1_TURN") ? "Runda 1/2" : "Runda 2/2";
        if (isRoundOwner) {
            tvStatus.setText("Moj broj  •  " + currentRoundText + "  (Biraš brojeve!)");
            btnStopTarget.setVisibility(View.VISIBLE);
            btnStopTarget.setEnabled(true);
            scheduleAutoStopTarget();
        } else {
            tvStatus.setText("Moj broj  •  " + currentRoundText + "  (Protivnik bira)");
            btnStopTarget.setVisibility(View.GONE);
        }
    }

    private void processMidRoundEvents(GameRoom room) {
        if (!isRoundOwner && targetNumber == 0) {
            Object t = room.getMojBrojTarget();
            if (t != null && ((Number) t).intValue() != 0) {
                targetNumber = ((Number) t).intValue();
                showTarget(targetNumber);
            }
        }

        if (!isRoundOwner && targetNumber != 0 && !numbersRevealed) {
            Object nums = room.getMojBrojNumbers();
            if (nums != null && !((List<?>) nums).isEmpty()) {
                List<Long> raw = (List<Long>) nums;
                availableNumbers.clear();
                for (Long n : raw) availableNumbers.add(n.intValue());
                numbersRevealed = true;
                showNumbers();
                startRoundTimer();
            }
        }

        if (submitted && !scoringDone) {
            Boolean p1Done = room.getMojBrojP1Submitted();
            Boolean p2Done = room.getMojBrojP2Submitted();
            if (Boolean.TRUE.equals(p1Done) && Boolean.TRUE.equals(p2Done)) {
                scoringDone = true;
                completedPhases.add(activePhase);

                // HOST ELECTION: Only the round owner calculates points & advances the phase
                if (isRoundOwner) {
                    scoreAndAdvance(room);
                } else {
                    tvStatus.setText("Računanje rezultata...");
                }
            }
        }
    }

    private void onStopTarget() {
        if (targetRevealed || !isRoundOwner) return;
        targetRevealed = true;
        cancelStopAutoTimer();
        btnStopTarget.setVisibility(View.GONE);

        targetNumber = vm.generateMojBrojTarget();
        vm.publishMojBrojTarget(gameId, targetNumber);
        showTarget(targetNumber);
        btnStopNumbers.setVisibility(View.VISIBLE);
        btnStopNumbers.setEnabled(true);
        tvStatus.setText("Pritisni STOP za brojeve! (ili protresi)");
        scheduleAutoStopNumbers();
    }

    private void showTarget(int target) {
        btnStopTarget.setVisibility(View.GONE);
        tvTarget.setText(String.valueOf(target));
        tvTarget.setVisibility(View.VISIBLE);
    }

    private void onStopNumbers() {
        if (numbersRevealed || !isRoundOwner) return;
        numbersRevealed = true;
        cancelStopAutoTimer();
        btnStopNumbers.setVisibility(View.GONE);

        availableNumbers = vm.generateMojBrojNumbers();
        vm.publishMojBrojNumbers(gameId, availableNumbers);
        showNumbers();
        startRoundTimer();
    }

    private void showNumbers() {
        layoutNumButtons.removeAllViews();
        for (int i = 0; i < availableNumbers.size(); i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            btn.setLayoutParams(lp);
            btn.setText(String.valueOf(availableNumbers.get(i)));
            btn.setTextSize(13f);
            btn.setPadding(dp(2), dp(4), dp(2), dp(4));
            btn.setTag(idx);
            btn.setOnClickListener(v -> onNumberButtonClicked(btn, idx));
            layoutNumButtons.addView(btn);
        }

        expression.setLength(0);
        tvExpression.setText("");
        tvExprError.setVisibility(View.GONE);
        layoutExprInput.setVisibility(View.VISIBLE);
        tvTimer.setVisibility(View.VISIBLE);
        tvStatus.setText("Pronađi broj!");
    }

    private void onNumberButtonClicked(MaterialButton btn, int idx) {
        if (usedNumberIndices.contains(idx)) return;
        if (expression.length() > 0 && Character.isDigit(expression.charAt(expression.length() - 1))) return;
        usedNumberIndices.add(idx);
        btn.setEnabled(false);
        btn.setAlpha(0.4f);
        expression.append(availableNumbers.get(idx));
        tvExpression.setText(expression.toString());
        tvExprError.setVisibility(View.GONE);
    }

    private void appendToExpr(String token) {
        expression.append(token);
        tvExpression.setText(expression.toString());
        tvExprError.setVisibility(View.GONE);
    }

    private void onBackspace() {
        if (expression.length() == 0) return;
        expression.deleteCharAt(expression.length() - 1);
        tvExpression.setText(expression.toString());
        tvExprError.setVisibility(View.GONE);
        recomputeUsedButtons();
    }

    private void recomputeUsedButtons() {
        List<Integer> inUse = new ArrayList<>();
        String cur = expression.toString();
        StringBuilder num = new StringBuilder();
        for (char c : cur.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (num.length() > 0) {
                    inUse.add(Integer.parseInt(num.toString()));
                    num.setLength(0);
                }
            }
        }
        if (num.length() > 0) inUse.add(Integer.parseInt(num.toString()));

        usedNumberIndices.clear();
        List<Integer> pool = new ArrayList<>(availableNumbers);
        for (int n : inUse) {
            int poolIdx = pool.indexOf(n);
            if (poolIdx >= 0) {
                usedNumberIndices.add(poolIdx);
                pool.set(poolIdx, null);
            }
        }

        for (int i = 0; i < layoutNumButtons.getChildCount(); i++) {
            MaterialButton b = (MaterialButton) layoutNumButtons.getChildAt(i);
            boolean used = usedNumberIndices.contains(i);
            b.setEnabled(!used);
            b.setAlpha(used ? 0.4f : 1f);
        }
    }

    private void onClearExpr() {
        expression.setLength(0);
        usedNumberIndices.clear();
        tvExpression.setText("");
        tvExprError.setVisibility(View.GONE);
        for (int i = 0; i < layoutNumButtons.getChildCount(); i++) {
            MaterialButton b = (MaterialButton) layoutNumButtons.getChildAt(i);
            b.setEnabled(true);
            b.setAlpha(1f);
        }
    }

    private void scheduleAutoStopTarget() {
        cancelStopAutoTimer();
        stopAutoTimer = new CountDownTimer(STOP_AUTO_DELAY_MS, STOP_AUTO_DELAY_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() { onStopTarget(); }
        }.start();
    }

    private void scheduleAutoStopNumbers() {
        cancelStopAutoTimer();
        stopAutoTimer = new CountDownTimer(STOP_AUTO_DELAY_MS, STOP_AUTO_DELAY_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() { onStopNumbers(); }
        }.start();
    }

    private void cancelStopAutoTimer() {
        if (stopAutoTimer != null) { stopAutoTimer.cancel(); stopAutoTimer = null; }
    }

    private void startRoundTimer() {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(ROUND_DURATION_MS, 500) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText((ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                tvTimer.setText("0s");
                if (!submitted) submitExpression();
            }
        }.start();
    }

    private void cancelRoundTimer() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void submitExpression() {
        if (submitted) return;

        String expr = expression.toString().trim();
        int result = 0;

        if (!expr.isEmpty()) {
            try {
                result = (int) evaluate(expr);
            } catch (Exception e) {
                tvExprError.setText("Neispravan izraz!");
                tvExprError.setVisibility(View.VISIBLE);
                return;
            }
        }

        submitted = true;
        cancelRoundTimer();
        cancelStopAutoTimer();

        layoutExprInput.setVisibility(View.GONE);
        tvSubmittedResult.setText(expr.isEmpty() ? "Nisi unio izraz" : "Tvoj rezultat: " + result);
        tvSubmittedResult.setVisibility(View.VISIBLE);
        tvStatus.setText("Čekaj protivnika...");

        boolean isP1 = myUsername.equals(vm.gameRoom.getValue() != null
                ? vm.gameRoom.getValue().getPlayerOne() : "");
        vm.submitMojBrojResult(gameId, isP1, result);
    }

    private void scoreAndAdvance(GameRoom room) {
        int target = ((Number) room.getMojBrojTarget()).intValue();

        int p1Res =  room.getMojBrojP1Result();
        int p2Res =  room.getMojBrojP2Result();

        int p1Diff = Math.abs(target - p1Res);
        int p2Diff = Math.abs(target - p2Res);

        int pts1 = 0;
        int pts2 = 0;

        // Give points to whoever is closer. Tie gives both players points.
        if (p1Res != 0 && p2Res != 0) {
            if (p1Diff < p2Diff) pts1 = (p1Diff == 0) ? 20 : 15;
            else if (p2Diff < p1Diff) pts2 = (p2Diff == 0) ? 20 : 15;
            else { pts1 = (p1Diff == 0) ? 20 : 15; pts2 = (p1Diff == 0) ? 20 : 15; }
        } else if (p1Res != 0) {
            pts1 = (p1Diff == 0) ? 20 : 15;
        } else if (p2Res != 0) {
            pts2 = (p2Diff == 0) ? 20 : 15;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("playerOneScore", room.getPlayerOneScore() + pts1);
        updates.put("playerTwoScore", room.getPlayerTwoScore() + pts2);
        updates.put("playerOneRoundScore", room.getPlayerOneRoundScore() + pts1);
        updates.put("playerTwoRoundScore", room.getPlayerTwoRoundScore() + pts2);

        if (activePhase.equals("P1_TURN")) {
            updates.put("roundPhase", "P2_TURN");

            // Wipe the round state clean for Player 2
            updates.put("mojBrojP1Submitted", false);
            updates.put("mojBrojP2Submitted", false);
            updates.put("mojBrojP1Result", 0);
            updates.put("mojBrojP2Result", 0);
            updates.put("mojBrojTarget", 0);
            updates.put("mojBrojNumbers", new ArrayList<>());
        } else {
            updates.put("roundPhase", "MINIGAME_DONE");
        }

        vm.advancePhase(gameId, updates);
    }

    private double evaluate(String expr) {
        expr = expr.replaceAll("\\s+", "");
        return parseExpr(new int[]{0}, expr);
    }

    private double parseExpr(int[] pos, String s) {
        double result = parseTerm(pos, s);
        while (pos[0] < s.length() && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) {
            char op = s.charAt(pos[0]++);
            double t = parseTerm(pos, s);
            result = op == '+' ? result + t : result - t;
        }
        return result;
    }

    private double parseTerm(int[] pos, String s) {
        double result = parseFactor(pos, s);
        while (pos[0] < s.length() && (s.charAt(pos[0]) == '*' || s.charAt(pos[0]) == '/')) {
            char op = s.charAt(pos[0]++);
            double f = parseFactor(pos, s);
            if (op == '/' && f == 0) throw new ArithmeticException("div by zero");
            result = op == '*' ? result * f : result / f;
        }
        return result;
    }

    private double parseFactor(int[] pos, String s) {
        if (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
            pos[0]++;
            double v = parseExpr(pos, s);
            if (pos[0] < s.length() && s.charAt(pos[0]) == ')') pos[0]++;
            return v;
        }
        int start = pos[0];
        if (pos[0] < s.length() && s.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < s.length() && Character.isDigit(s.charAt(pos[0]))) pos[0]++;
        return Double.parseDouble(s.substring(start, pos[0]));
    }

    private void showWaiting(String message) {
        tvStatus.setText(message);
        tvTimer.setText("");
        tvTimer.setVisibility(View.INVISIBLE);
        btnStopTarget.setVisibility(View.GONE);
        btnStopNumbers.setVisibility(View.GONE);
        layoutExprInput.setVisibility(View.GONE);
        tvSubmittedResult.setVisibility(View.GONE);
        tvTarget.setVisibility(View.GONE);
    }

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}