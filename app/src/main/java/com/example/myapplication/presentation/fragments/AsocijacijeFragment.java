package com.example.myapplication.presentation.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

public class AsocijacijeFragment extends Fragment {

    private static final long ROUND_DURATION_MS = 120_000L;
    private static final String[] COLUMN_IDS = {"A", "B", "C", "D"};

    private static final class AssociationSet {
        final String[][] hints;
        final String[] columns;
        final String finalAnswer;

        AssociationSet(String[][] hints, String[] columns, String finalAnswer) {
            this.hints = hints;
            this.columns = columns;
            this.finalAnswer = finalAnswer;
        }
    }

    private final List<AssociationSet> data = Arrays.asList(
            new AssociationSet(
                    new String[][]{
                            {"More", "So", "Talasi", "Plaza"},
                            {"Zima", "Belo", "Pahulja", "Skijanje"},
                            {"Let", "Karta", "Aerodrom", "Pilot"},
                            {"Kofer", "Pasos", "Hotel", "Mapa"}
                    },
                    new String[]{"Obala", "Sneg", "Avion", "Putovanje"},
                    "Odmor"
            ),
            new AssociationSet(
                    new String[][]{
                            {"Nota", "Refren", "Album", "Koncert"},
                            {"Platno", "Boja", "Ram", "Galerija"},
                            {"Scena", "Glumac", "Publika", "Predstava"},
                            {"Papir", "Stih", "Rima", "Pesnik"}
                    },
                    new String[]{"Muzika", "Slika", "Pozoriste", "Pesma"},
                    "Umetnost"
            )
    );

    private GameViewModel vm;
    private String gameId;
    private String myUsername;
    private TextView tvTimer;
    private TextView tvStatus;
    private TextView tvScore;
    private GridLayout grid;
    private EditText etGuess;
    private Button btnGuessA;
    private Button btnGuessB;
    private Button btnGuessC;
    private Button btnGuessD;
    private Button btnGuessFinal;
    private Button btnSubmitGuess;
    private CountDownTimer roundTimer;
    private boolean finishing = false;
    private int selectedGuessTarget = -1;
    private String activeTurnPlayer = null;
    private boolean openedCellThisTurn = false;

    public AsocijacijeFragment() {
        super(R.layout.fragment_asocijacije);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : null;
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : null;

        tvTimer = view.findViewById(R.id.tvAsocTimer);
        tvStatus = view.findViewById(R.id.tvAsocStatus);
        tvScore = view.findViewById(R.id.tvAsocScore);
        grid = view.findViewById(R.id.gridAsoc);
        etGuess = view.findViewById(R.id.etAsocGuess);
        btnGuessA = view.findViewById(R.id.btnGuessA);
        btnGuessB = view.findViewById(R.id.btnGuessB);
        btnGuessC = view.findViewById(R.id.btnGuessC);
        btnGuessD = view.findViewById(R.id.btnGuessD);
        btnGuessFinal = view.findViewById(R.id.btnGuessFinal);
        btnSubmitGuess = view.findViewById(R.id.btnSubmitAsocGuess);

        btnGuessA.setOnClickListener(v -> selectGuessTarget(0));
        btnGuessB.setOnClickListener(v -> selectGuessTarget(1));
        btnGuessC.setOnClickListener(v -> selectGuessTarget(2));
        btnGuessD.setOnClickListener(v -> selectGuessTarget(3));
        btnGuessFinal.setOnClickListener(v -> selectGuessTarget(4));
        btnSubmitGuess.setOnClickListener(v -> submitSelectedGuess());

        vm.gameRoom.observe(getViewLifecycleOwner(), this::onRoomUpdated);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

    private void onRoomUpdated(GameRoom room) {
        if (room == null || !"asocijacije".equals(room.getCurrentMinigameType())) return;

        if (room.getAsocRoundStartedAt() == 0 && isRoundStarter(room) && !finishing) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("asocTurnPlayer", activeStarter(room));
            updates.put("asocRoundStartedAt", System.currentTimeMillis());
            vm.advancePhase(gameId, updates);
            return;
        }

        if (room.getAsocTurnPlayer() != null && !room.getAsocTurnPlayer().equals(activeTurnPlayer)) {
            activeTurnPlayer = room.getAsocTurnPlayer();
            openedCellThisTurn = false;
            selectedGuessTarget = -1;
        }

        renderBoard(room);
        renderStatus(room);
        renderScore(room);
        renderGuessButtons(room);
        startTimer(room);
    }

    private void renderBoard(GameRoom room) {
        AssociationSet set = currentSet(room);
        List<Integer> opened = safeIntegers(room.getAsocOpenedCells());
        List<String> solved = safeStrings(room.getAsocSolvedColumns());
        boolean finalSolved = Boolean.TRUE.equals(room.getAsocFinalSolved());

        grid.removeAllViews();
        grid.setColumnCount(4);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int cellIndex = col * 4 + row;
                boolean visible = opened.contains(cellIndex) || solved.contains(COLUMN_IDS[col]) || finalSolved;
                MaterialButton btn = new MaterialButton(requireContext());
                btn.setText(visible ? set.hints[col][row] : COLUMN_IDS[col] + (row + 1));
                btn.setTextSize(13f);
                btn.setAllCaps(false);
                btn.setEnabled(isMyTurn(room) && !openedCellThisTurn && !visible
                        && !solved.contains(COLUMN_IDS[col]) && !finalSolved);
                btn.setOnClickListener(v -> openCell(room, cellIndex));
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        visible ? 0xFFDBEAFE : 0xFF1D4ED8));
                btn.setTextColor(visible ? 0xFF0F172A : 0xFFFFFFFF);
                addGridView(btn);
            }
        }

        for (int col = 0; col < 4; col++) {
            TextView answer = new TextView(requireContext());
            boolean solvedColumn = solved.contains(COLUMN_IDS[col]) || finalSolved;
            answer.setText(solvedColumn ? set.columns[col] : "Resenje " + COLUMN_IDS[col]);
            answer.setTextColor(solvedColumn ? 0xFF15803D : 0xFF475569);
            answer.setTextSize(14f);
            answer.setGravity(android.view.Gravity.CENTER);
            answer.setTypeface(null, android.graphics.Typeface.BOLD);
            answer.setBackgroundColor(0xFFFFFFFF);
            answer.setPadding(dp(6), dp(10), dp(6), dp(10));
            addGridView(answer);
        }
    }

    private void openCell(GameRoom room, int cellIndex) {
        if (!isMyTurn(room) || openedCellThisTurn) return;
        List<Integer> opened = safeIntegers(room.getAsocOpenedCells());
        if (!opened.contains(cellIndex)) opened.add(cellIndex);

        openedCellThisTurn = true;
        Map<String, Object> updates = new HashMap<>();
        updates.put("asocOpenedCells", opened);
        vm.advancePhase(gameId, updates);
    }

    private void selectGuessTarget(int target) {
        selectedGuessTarget = target;
        renderGuessButtons(vm.gameRoom.getValue());
    }

    private void submitSelectedGuess() {
        if (selectedGuessTarget < 0) {
            tvStatus.setText("Prvo izaberi sta pogadjas.");
            return;
        }
        if (selectedGuessTarget == 4) {
            submitFinalGuess();
        } else {
            submitColumnGuess(selectedGuessTarget);
        }
    }

    private void submitColumnGuess(int col) {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || !isMyTurn(room) || Boolean.TRUE.equals(room.getAsocFinalSolved())) return;
        String guess = etGuess.getText().toString().trim();
        if (guess.isEmpty()) return;

        AssociationSet set = currentSet(room);
        List<String> solved = safeStrings(room.getAsocSolvedColumns());
        String columnId = COLUMN_IDS[col];
        Map<String, Object> updates = new HashMap<>();

        if (guess.equalsIgnoreCase(set.columns[col]) && !solved.contains(columnId)) {
            solved.add(columnId);
            int score = 2 + unopenedInColumn(room, col);
            updates.put("asocSolvedColumns", solved);
            addScoreUpdate(room, updates, score);
            tvStatus.setText("Tacno " + columnId + " +" + score);
        } else {
            updates.put("asocTurnPlayer", otherPlayer(room));
            tvStatus.setText("Nije tacno. Protivnik je na potezu.");
        }

        selectedGuessTarget = -1;
        etGuess.setText("");
        vm.advancePhase(gameId, updates);
    }

    private void submitFinalGuess() {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || !isMyTurn(room) || Boolean.TRUE.equals(room.getAsocFinalSolved())) return;
        String guess = etGuess.getText().toString().trim();
        if (guess.isEmpty()) return;

        AssociationSet set = currentSet(room);
        Map<String, Object> updates = new HashMap<>();
        if (guess.equalsIgnoreCase(set.finalAnswer)) {
            int score = finalScore(room);
            addScoreUpdate(room, updates, score);
            updates.put("asocFinalSolved", true);
            addNextRoundUpdate(room, updates);
            finishing = true;
            tvStatus.setText("Konacno resenje! +" + score);
        } else {
            updates.put("asocTurnPlayer", otherPlayer(room));
            tvStatus.setText("Nije tacno. Protivnik je na potezu.");
        }

        selectedGuessTarget = -1;
        etGuess.setText("");
        vm.advancePhase(gameId, updates);
    }

    private void startTimer(GameRoom room) {
        long remaining = Math.max(0, room.getAsocRoundStartedAt() + ROUND_DURATION_MS - System.currentTimeMillis());
        cancelTimer();
        tvTimer.setText((remaining / 1000) + "s");
        if (remaining <= 0) {
            if (!finishing && isMyTurn(room)) finishRound(room);
            return;
        }
        roundTimer = new CountDownTimer(remaining, 500L) {
            @Override
            public void onTick(long ms) {
                if (isAdded()) tvTimer.setText((ms / 1000) + "s");
            }

            @Override
            public void onFinish() {
                if (!isAdded() || finishing || !isMyTurn(room)) return;
                tvTimer.setText("0s");
                finishRound(room);
            }
        }.start();
    }

    private void finishRound(GameRoom room) {
        finishing = true;
        Map<String, Object> updates = new HashMap<>();
        addNextRoundUpdate(room, updates);
        vm.advancePhase(gameId, updates);
    }

    private void addNextRoundUpdate(GameRoom room, Map<String, Object> updates) {
        if ("P1_TURN".equals(room.getRoundPhase())) {
            updates.put("roundPhase", "P2_TURN");
            updates.put("asocRoundIndex", 1);
            updates.put("asocOpenedCells", new ArrayList<Integer>());
            updates.put("asocSolvedColumns", new ArrayList<String>());
            updates.put("asocFinalSolved", false);
            updates.put("asocTurnPlayer", room.getPlayerTwo());
            updates.put("asocRoundStartedAt", System.currentTimeMillis());
            finishing = false;
        } else {
            updates.put("roundPhase", "MINIGAME_DONE");
        }
    }

    private int finalScore(GameRoom room) {
        List<String> solved = safeStrings(room.getAsocSolvedColumns());
        int score = 7;
        for (int col = 0; col < 4; col++) {
            if (solved.contains(COLUMN_IDS[col])) continue;
            int opened = openedInColumn(room, col);
            score += opened == 0 ? 6 : 2 + (4 - opened);
        }
        return score;
    }

    private int unopenedInColumn(GameRoom room, int col) {
        return 4 - openedInColumn(room, col);
    }

    private int openedInColumn(GameRoom room, int col) {
        List<Integer> opened = safeIntegers(room.getAsocOpenedCells());
        int count = 0;
        for (int row = 0; row < 4; row++) {
            if (opened.contains(col * 4 + row)) count++;
        }
        return count;
    }

    private void addScoreUpdate(GameRoom room, Map<String, Object> updates, int score) {
        if (myUsername != null && myUsername.equals(room.getPlayerOne())) {
            updates.put("playerOneRoundScore", room.getPlayerOneRoundScore() + score);
            updates.put("playerOneScore", room.getPlayerOneScore() + score);
        } else {
            updates.put("playerTwoRoundScore", room.getPlayerTwoRoundScore() + score);
            updates.put("playerTwoScore", room.getPlayerTwoScore() + score);
        }
    }

    private void renderStatus(GameRoom room) {
        String round = room.getAsocRoundIndex() == 0 ? "1/2" : "2/2";
        if (Boolean.TRUE.equals(room.getAsocFinalSolved())) {
            tvStatus.setText("Asocijacije - runda " + round + " zavrsena");
        } else if (isMyTurn(room)) {
            tvStatus.setText("Asocijacije - runda " + round + ". Tvoj potez.");
        } else {
            tvStatus.setText("Asocijacije - runda " + round + ". Igra " + room.getAsocTurnPlayer());
        }
    }

    private void renderScore(GameRoom room) {
        boolean p1 = myUsername != null && myUsername.equals(room.getPlayerOne());
        int total = p1 ? room.getPlayerOneScore() : room.getPlayerTwoScore();
        int round = p1 ? room.getPlayerOneRoundScore() : room.getPlayerTwoRoundScore();
        tvScore.setText("Bodovi: " + total + "  |  Runda: +" + round);
    }

    private void renderGuessButtons(GameRoom room) {
        boolean enabled = room != null && isMyTurn(room) && !Boolean.TRUE.equals(room.getAsocFinalSolved());
        styleGuessButton(btnGuessA, 0, enabled);
        styleGuessButton(btnGuessB, 1, enabled);
        styleGuessButton(btnGuessC, 2, enabled);
        styleGuessButton(btnGuessD, 3, enabled);
        styleGuessButton(btnGuessFinal, 4, enabled);
        btnSubmitGuess.setEnabled(enabled);
    }

    private void styleGuessButton(Button button, int target, boolean enabled) {
        boolean selected = selectedGuessTarget == target;
        button.setEnabled(enabled);
        button.setTextColor(selected ? 0xFFFFFFFF : 0xFF0F172A);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? 0xFF1D4ED8 : 0xFFE0F2FE));
    }

    private AssociationSet currentSet(GameRoom room) {
        int index = Math.max(0, Math.min(room.getAsocRoundIndex(), data.size() - 1));
        return data.get(index);
    }

    private boolean isMyTurn(GameRoom room) {
        return myUsername != null && myUsername.equals(room.getAsocTurnPlayer());
    }

    private boolean isRoundStarter(GameRoom room) {
        return myUsername != null && myUsername.equals(activeStarter(room));
    }

    private String activeStarter(GameRoom room) {
        return "P2_TURN".equals(room.getRoundPhase()) ? room.getPlayerTwo() : room.getPlayerOne();
    }

    private String otherPlayer(GameRoom room) {
        return myUsername != null && myUsername.equals(room.getPlayerOne())
                ? room.getPlayerTwo()
                : room.getPlayerOne();
    }

    private List<Integer> safeIntegers(List<Integer> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private List<String> safeStrings(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private void addGridView(View view) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0;
        p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        view.setLayoutParams(p);
        grid.addView(view);
    }

    private void cancelTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
