package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkockoFragment extends Fragment {

    private static final String[] SYMBOLS = {"skocko", "kvadrat", "krug", "srce", "trougao", "zvezda"};
    private static final int[] SYMBOL_DRAWABLES = {
            R.drawable.skocko,
            R.drawable.square,
            R.drawable.circle,
            R.drawable.heart,
            R.drawable.triangle,
            R.drawable.star
    };

    private static final int CELL_SIZE_DP   = 64;
    private static final int CELL_MARGIN_DP = 5;
    private static final int BTN_SIZE_DP    = 44;

    private GameViewModel vm;
    private String gameId;
    private String myUsername;

    private TextView tvStatus;
    private TextView tvTimer;
    private GridLayout gridGuesses;
    private LinearLayout layoutSymbolPicker;
    private HorizontalScrollView scrollSymbolPicker;
    private TextView tvSelectSymbols;
    private MaterialButton btnConfirmGuess;

    private List<String> solution;
    private final List<String> currentGuess = new ArrayList<>();
    private final List<Map<String, Object>> localGuessHistory = new ArrayList<>();
    private int currentRow = 0;
    private boolean isMyTurn = false;
    private boolean isBonusMode = false;
    private int maxGuesses = 6;
    private boolean turnFinished = false;
    private String activePhase = null;
    private String lastStartedPhase = null;

    private CountDownTimer turnTimer;

    public SkockoFragment() {
        super(R.layout.fragment_skocko);
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId     = getArguments().getString("gameId");
        myUsername = getArguments().getString("myUsername");
        int roundNumber = getArguments() != null ? getArguments().getInt("roundNumber", 1) : 1;

        tvStatus          = view.findViewById(R.id.tvStatus);
        tvTimer           = view.findViewById(R.id.tvTimer);
        gridGuesses       = view.findViewById(R.id.gridGuesses);
        layoutSymbolPicker  = view.findViewById(R.id.layoutSymbolPicker);
        scrollSymbolPicker  = view.findViewById(R.id.scrollSymbolPicker);
        tvSelectSymbols     = view.findViewById(R.id.tvSelectSymbols);
        btnConfirmGuess     = view.findViewById(R.id.btnConfirmGuess);

        tvStatus.setText("Škocko  •  Runda " + roundNumber + "/2");

        setupSymbolPicker();
        btnConfirmGuess.setOnClickListener(v -> submitGuess());

        vm.currentPhase.observe(getViewLifecycleOwner(), phase -> {
            if (phase != null) onPhaseChanged(phase);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }



    private void onPhaseChanged(String phase) {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;
        if (!"skocko".equals(room.getCurrentMinigameType())) return;

        isBonusMode = phase.contains("BONUS");
        maxGuesses  = isBonusMode ? 1 : 6;
        isMyTurn    = isActivePlayer(phase, room);

        if (!isMyTurn) {
            lastStartedPhase = null;
            cancelTimer();
            if (phase.equals("MINIGAME_DONE")) {
                showWaiting("Kraj runde!");
            } else {
                showWaiting("Čekaj na protivnika...");
            }
            return;
        }

        if (!phase.equals(lastStartedPhase)) {
            lastStartedPhase = phase;
            loadSolutionThenStart(room);
        }
    }

    private boolean isActivePlayer(String phase, GameRoom room) {
        switch (phase) {
            case "P1_TURN":
            case "P1_BONUS": return myUsername.equals(room.getPlayerOne());
            case "P2_TURN":
            case "P2_BONUS": return myUsername.equals(room.getPlayerTwo());
            default:         return false;
        }
    }

    private void showWaiting(String message) {
        tvStatus.setText(message);
        tvTimer.setText("");
        tvTimer.setVisibility(View.INVISIBLE);
        tvSelectSymbols.setVisibility(View.GONE);
        scrollSymbolPicker.setVisibility(View.GONE);
        layoutSymbolPicker.setVisibility(View.GONE);
        btnConfirmGuess.setVisibility(View.GONE);
        gridGuesses.removeAllViews();
        currentGuess.clear();
        localGuessHistory.clear();
        currentRow = 0;
    }



    private void loadSolutionThenStart(GameRoom room) {
        tvStatus.setText("Učitavanje...");
        String entry = room.getMinigamePlaylist().get(room.getCurrentMinigameIndex());
        String docId = entry.contains(":") ? entry.split(":")[1] : entry;
        String solutionField = solutionFieldForPhase(room.getRoundPhase());

        vm.fetchSkockoSolution(docId, solutionField,
                sol -> {
                    solution = sol;
                    if(!isAdded()) return;
                    startTurn();
                },
                e -> {
                    if(!isAdded()) return;
            tvStatus.setText("Greška pri učitavanju!");}
        );
    }

    private String solutionFieldForPhase(String phase) {
        switch (phase) {
            case "P1_TURN":  return "p1Solution";
            case "P2_TURN":  return "p2Solution";
            case "P2_BONUS": return "p1Solution";
            case "P1_BONUS": return "p2Solution";
            default:         return "p1Solution";
        }
    }

    private void startTurn() {
        GameRoom room = vm.gameRoom.getValue();
        activePhase   = room != null ? room.getRoundPhase() : null;
        currentRow    = 0;
        turnFinished  = false;
        currentGuess.clear();
        localGuessHistory.clear();

        if (isBonusMode && room != null) {
            List<Map<String, Object>> history = activePhase.equals("P2_BONUS")
                    ? room.getP1GuessHistory()
                    : room.getP2GuessHistory();
            int historySize = (history != null) ? history.size() : 0;
            maxGuesses = historySize + 1;
        }

        setupGuessGrid();

        if (isBonusMode) {
            renderOpponentHistory(room);
        }

        tvStatus.setText(isBonusMode ? "Bonus potez!" : "Tvoj red!");
        tvTimer.setVisibility(View.VISIBLE);
        tvSelectSymbols.setVisibility(View.VISIBLE);
        scrollSymbolPicker.setVisibility(View.VISIBLE);
        layoutSymbolPicker.setVisibility(View.VISIBLE);
        btnConfirmGuess.setVisibility(View.VISIBLE);
        btnConfirmGuess.setEnabled(false);

        long duration = isBonusMode ? 10_000 : 30_000;
        startTimer(duration);
        if (solution != null && currentGuess.size() == 4){
            btnConfirmGuess.setEnabled(true);
        }
    }


    private void addSymbolToGuess(String symbol) {
        if (!isMyTurn || turnFinished) return;
        if (currentGuess.size() < 4) {
            currentGuess.add(symbol);
            renderCurrentRow();
            btnConfirmGuess.setEnabled(currentGuess.size() == 4 && solution != null);
        }
    }

    private void submitGuess() {
        if (currentGuess.size() != 4 || turnFinished) return;


        btnConfirmGuess.setEnabled(false);

        List<String> feedback = vm.calculateFeedback(currentGuess, solution);
        renderFeedback(currentRow, feedback);

        Map<String, Object> entry = new HashMap<>();
        entry.put("guess", new ArrayList<>(currentGuess));
        entry.put("feedback", new ArrayList<>(feedback));
        localGuessHistory.add(entry);

        boolean solved = isAllCorrect(feedback);
        currentRow++;
        currentGuess.clear();

        if (solved || currentRow >= maxGuesses) {
            turnFinished = true;
            cancelTimer();
            commitTurnToFirestore(solved);
        } else {    
            renderCurrentRow();
            btnConfirmGuess.setEnabled(false);
        }
    }

    private boolean isAllCorrect(List<String> feedback) {
        for (String f : feedback) if (!f.equals("CORRECT")) return false;
        return true;
    }


    private void commitTurnToFirestore(boolean solved) {
        if(!isAdded()) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || activePhase == null) return;

        String phase = activePhase;
        int score = scoreForGuess(currentRow, solved);
        String nextPhase = nextPhaseAfter(phase, solved);

        Map<String, Object> updates = new HashMap<>();
        updates.put("roundPhase", nextPhase);

        if (!isBonusMode) {
            if (phase.equals("P1_TURN")) {
                updates.put("playerOneRoundScore", score);
                updates.put("playerOneScore", room.getPlayerOneScore() + score);
                updates.put("p1GuessHistory", new ArrayList<>(localGuessHistory));
            } else {
                updates.put("playerTwoRoundScore", score);
                updates.put("playerTwoScore", room.getPlayerTwoScore() + score);
                updates.put("p2GuessHistory", new ArrayList<>(localGuessHistory));
            }
        } else {
            if (solved) {
                if (phase.equals("P1_BONUS")) {
                    updates.put("playerOneRoundScore", room.getPlayerOneRoundScore() + score);
                    updates.put("playerOneScore", room.getPlayerOneScore() + score);
                } else {
                    updates.put("playerTwoRoundScore", room.getPlayerTwoRoundScore() + score);
                    updates.put("playerTwoScore", room.getPlayerTwoScore() + score);
                }
            }
        }

        vm.advancePhase(gameId, updates);
    }

    private int scoreForGuess(int guessesUsed, boolean solved) {
        if (!solved) return 0;
        if (isBonusMode) return 10;
        if (guessesUsed <= 2) return 20;
        if (guessesUsed <= 4) return 15;
        return 10;
    }

    private String nextPhaseAfter(String phase, boolean solved) {
        switch (phase) {
            case "P1_TURN":  return solved ? "P2_TURN" : "P2_BONUS";
            case "P2_BONUS": return "P2_TURN";
            case "P2_TURN":  return solved ? "MINIGAME_DONE" : "P1_BONUS";
            case "P1_BONUS": return "MINIGAME_DONE";
            default:         return "MINIGAME_DONE";
        }
    }

    private void clearTurnState() {
        activePhase  = null;
        turnFinished = false;
        currentRow   = 0;
        currentGuess.clear();
        localGuessHistory.clear();
    }


    private void renderOpponentHistory(GameRoom room) {
        if (room == null) return;

        List<Map<String, Object>> history = activePhase.equals("P2_BONUS")
                ? room.getP1GuessHistory()
                : room.getP2GuessHistory();

        if (history == null || history.isEmpty()) return;

        for (int row = 0; row < history.size() && row < maxGuesses; row++) {
            Map<String, Object> entry = history.get(row);
            List<String> guess    = (List<String>) entry.get("guess");
            List<String> feedback = (List<String>) entry.get("feedback");

            if (guess == null || feedback == null) continue;

            for (int col = 0; col < 4; col++) {
                ImageView cell = (ImageView) gridGuesses.getChildAt(row * 4 + col);
                int idx = Arrays.asList(SYMBOLS).indexOf(guess.get(col));
                if (idx >= 0) cell.setImageResource(SYMBOL_DRAWABLES[idx]);
            }
            renderFeedback(row, feedback);
        }

        currentRow = history.size();
    }


    private void startTimer(long durationMs) {
        cancelTimer();
        turnTimer = new CountDownTimer(durationMs, 500) {
            @Override
            public void onTick(long ms) {
                if(!isAdded()) return;
                tvTimer.setText((ms / 1000) + "s");
            }

            @Override
            public void onFinish() {
                if(!isAdded()) return;
                tvTimer.setText("0s");
                if (!turnFinished) {
                    turnFinished = true;
                    commitTurnToFirestore(false);
                }
            }
        }.start();
    }

    private void cancelTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }



    private void setupGuessGrid() {
        gridGuesses.removeAllViews();
        gridGuesses.setColumnCount(4);
        gridGuesses.setRowCount(maxGuesses);

        int sizePx   = dp(CELL_SIZE_DP);
        int marginPx = dp(CELL_MARGIN_DP);

        for (int row = 0; row < maxGuesses; row++) {
            for (int col = 0; col < 4; col++) {
                ImageView cell = new ImageView(requireContext());
                GridLayout.LayoutParams p = new GridLayout.LayoutParams();
                p.width  = sizePx;
                p.height = sizePx;
                p.setMargins(marginPx, marginPx, marginPx, marginPx);
                cell.setLayoutParams(p);
                cell.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_cell_empty));
                cell.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.setPadding(dp(6), dp(6), dp(6), dp(6));
                gridGuesses.addView(cell);
            }
        }
    }

    private void setupSymbolPicker() {
        layoutSymbolPicker.removeAllViews();
        int sizePx   = dp(BTN_SIZE_DP);
        int marginPx = dp(6);

        for (int i = 0; i < SYMBOLS.length; i++) {
            ImageButton btn = new ImageButton(requireContext());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(sizePx, sizePx);
            p.setMargins(marginPx, 0, marginPx, 0);
            btn.setLayoutParams(p);
            btn.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_cell_filled));
            btn.setImageResource(SYMBOL_DRAWABLES[i]);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(dp(10), dp(10), dp(10), dp(10));
            btn.setElevation(dp(2));

            final String symbol = SYMBOLS[i];
            btn.setOnClickListener(v -> addSymbolToGuess(symbol));
            layoutSymbolPicker.addView(btn);
        }
    }

    private void renderCurrentRow() {
        if (currentRow < 0 || currentRow >= maxGuesses) return;
        for (int col = 0; col < 4; col++) {
            ImageView cell = (ImageView) gridGuesses.getChildAt(currentRow * 4 + col);
            if (col < currentGuess.size()) {
                int idx = Arrays.asList(SYMBOLS).indexOf(currentGuess.get(col));
                cell.setImageResource(SYMBOL_DRAWABLES[idx]);
                cell.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_cell_filled));
            } else {
                cell.setImageDrawable(null);
                cell.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_cell_empty));
            }
        }
    }

    private void renderFeedback(int row, List<String> feedback) {
        for (int col = 0; col < 4; col++) {
            ImageView cell = (ImageView) gridGuesses.getChildAt(row * 4 + col);
            int bgRes;
            switch (feedback.get(col)) {
                case "CORRECT": bgRes = R.drawable.bg_feedback_correct; break;
                case "PRESENT": bgRes = R.drawable.bg_feedback_present; break;
                default:        bgRes = R.drawable.bg_feedback_absent;  break;
            }
            cell.setBackground(ContextCompat.getDrawable(requireContext(), bgRes));
        }
    }

    private int dp(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
