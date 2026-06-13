package com.example.myapplication.presentation.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpojniceFragment extends Fragment {

    private static final long TURN_DURATION_MS = 30_000L;
    private static final int TOTAL_ITEMS = 5;

    // Podaci runde ucitani sa Firebase-a
    private final List<Map<String, String>> ROUND_PAIRS = new ArrayList<>();
    private final List<List<String>> LEFT_ITEMS = new ArrayList<>();
    private final List<List<String>> RIGHT_ITEMS = new ArrayList<>();

    private boolean isLoadingData = false;

    private GameViewModel vm;
    private String gameId, myUsername;

    // Statusne promenljive
    private String activePhase = null;
    private boolean isTransitioning = false;
    private boolean isMyTurn = false;
    private int currentRoundIndex = 0;

    private TextView tvTimer, tvScore;
    private LinearLayout gridLeft, gridRight;
    private CountDownTimer turnTimer;
    private final Set<String> completedPhases = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Lokalno stanje poteza
    private Map<String, String> localMatches    = new HashMap<>();
    private Set<String>         localWrongLeft  = new HashSet<>();
    private Set<String>         localWrongRight = new HashSet<>();
    private String selectedLeft = null;
    private int localPointsEarned = 0; // Bodovi zarađeni u trenutnom potezu

    public SpojniceFragment() {
        super(R.layout.fragment_spojnice);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : "";
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : "";

        tvTimer = view.findViewById(R.id.tvTimer);
        tvScore = view.findViewById(R.id.tvScore);
        gridLeft = view.findViewById(R.id.gridLeft);
        gridRight = view.findViewById(R.id.gridRight);

        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room != null && "spojnice".equals(room.getCurrentMinigameType())) {

                if ("MINIGAME_DONE".equals(room.getRoundPhase())) {
                    showFinished();
                    return;
                }

                if (ROUND_PAIRS.isEmpty() && !isLoadingData) {
                    isLoadingData = true;
                    String currentMinigame = room.getMinigamePlaylist().get(room.getCurrentMinigameIndex());
                    String docId = currentMinigame.contains(":") ? currentMinigame.split(":")[1] : currentMinigame;

                    vm.fetchSpojniceData(docId, data -> {
                        parseFirebaseData(data);
                        onPhaseChanged(room);
                    }, e -> {
                        if (tvTimer != null) tvTimer.setText("Greška pri učitavanju");
                    });

                } else if (!ROUND_PAIRS.isEmpty()) {
                    onPhaseChanged(room);
                }
            }
        });
    }

    private void parseFirebaseData(Map<String, Object> data) {
        ROUND_PAIRS.clear();
        LEFT_ITEMS.clear();
        RIGHT_ITEMS.clear();

        List<Map<String, Object>> roundsList = (List<Map<String, Object>>) data.get("rounds");
        if (roundsList != null) {
            for (Map<String, Object> round : roundsList) {
                Map<String, String> pairs = (Map<String, String>) round.get("pairs");
                List<String> left = (List<String>) round.get("leftItems");
                List<String> right = (List<String>) round.get("rightItems");

                ROUND_PAIRS.add(pairs != null ? pairs : new HashMap<>());
                LEFT_ITEMS.add(left != null ? left : new ArrayList<>());
                RIGHT_ITEMS.add(right != null ? right : new ArrayList<>());
            }
        }
    }

    private void onPhaseChanged(GameRoom room) {
        String phase = room.getRoundPhase();

        if (completedPhases.contains(phase)) return;
        if (phase.equals(activePhase)) return;

        isTransitioning = false;
        activePhase = phase;
        isMyTurn = isActivePlayer(phase, room);

        // P1_TURN i P2_BONUS pripadaju Prvoj Rundi (Indeks 0)
        // P2_TURN i P1_BONUS pripadaju Drugoj Rundi (Indeks 1)
        currentRoundIndex = (phase.equals("P1_TURN") || phase.equals("P2_BONUS")) ? 0 : 1;

        localMatches = new HashMap<>();
        if (room.getSpojniceMatches() != null) {
            localMatches.putAll(room.getSpojniceMatches());
        }

        // Resetujemo lokalne promašaje i bodove za novi potez/bonus
        localWrongLeft.clear();
        localWrongRight.clear();
        selectedLeft = null;
        localPointsEarned = 0;

        renderGrids();

        if (isMyTurn) {
            startTimer(TURN_DURATION_MS);
        } else {
            cancelTimer();
            if (tvTimer != null) tvTimer.setText("Čekaj...");
        }
    }

    private void renderGrids() {
        if (!isAdded() || ROUND_PAIRS.isEmpty()) return;

        gridLeft.removeAllViews();
        gridRight.removeAllViews();

        // Ekran za čekanje za neaktivnog igrača
        if (!isMyTurn) {
            TextView tvWait = new TextView(requireContext());
            tvWait.setText("Protivnik trenutno spaja pojmove...");
            tvWait.setTextSize(18f);
            tvWait.setGravity(Gravity.CENTER);
            tvWait.setTextColor(0xFF555555);
            tvWait.setPadding(0, 50, 0, 0);
            gridLeft.addView(tvWait);
            return;
        }

        List<String> leftList = LEFT_ITEMS.get(currentRoundIndex);
        List<String> rightList = RIGHT_ITEMS.get(currentRoundIndex);

        for (String item : leftList) {
            boolean solved = localMatches.containsKey(item);
            boolean wrong  = localWrongLeft.contains(item);
            MaterialButton btn = createLeftButton(item, solved, wrong);
            if (item.equals(selectedLeft)) btn.setBackgroundTintList(ColorStateList.valueOf(0xFFF59E0B)); // Narandžasta selekcija
            btn.setOnClickListener(v -> {
                if (isMyTurn && !solved && !wrong) {
                    selectedLeft = item;
                    renderGrids();
                }
            });
            gridLeft.addView(btn);
        }

        for (String item : rightList) {
            boolean solved = localMatches.containsValue(item);
            boolean wrong  = localWrongRight.contains(item);
            MaterialButton btn = createRightButton(item, solved, wrong);
            btn.setOnClickListener(v -> {
                if (isMyTurn && selectedLeft != null && !solved && !wrong)
                    handleMatch(selectedLeft, item);
            });
            gridRight.addView(btn);
        }
    }

    private MaterialButton createLeftButton(String text, boolean solved, boolean wrong) {
        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setCornerRadius(12);
        int color = solved ? 0xFF22C55E : wrong ? 0xFFEF4444 : 0xFFFFFFFF; // Zelena za tačno, Crvena za netačno
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
        btn.setTextColor(solved || wrong ? 0xFFFFFFFF : 0xFF333333);
        btn.setEnabled(isMyTurn && !solved && !wrong);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private MaterialButton createRightButton(String text, boolean solved, boolean wrong) {
        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setCornerRadius(12);
        int color = solved ? 0xFF22C55E : wrong ? 0xFFEF4444 : 0xFFFFFFFF;
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
        btn.setTextColor(solved || wrong ? 0xFFFFFFFF : 0xFF333333);
        btn.setEnabled(isMyTurn && !solved && !wrong);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void handleMatch(String left, String right) {
        boolean correct = right.equals(ROUND_PAIRS.get(currentRoundIndex).get(left));
        if (correct) {
            localMatches.put(left, right);
            localPointsEarned += 2; // Dodela bodova po pravilima (2 boda po pogotku)
        } else {
            localWrongLeft.add(left);
            localWrongRight.add(right);
        }
        selectedLeft = null;
        renderGrids();

        // Ako je igrač pokušao da spoji svih 5 (bilo tačno ili netačno), automatski završavamo potez
        int totalAttempted = localMatches.size() + localWrongLeft.size();
        if (totalAttempted == TOTAL_ITEMS) {
            // Kratka pauza da bi se boja registrovala vizuelno pre prelaska
            handler.postDelayed(this::finishTurn, 800);
        }
    }

    private void finishTurn() {
        if (isTransitioning) return;
        isTransitioning = true;
        cancelTimer();
        completedPhases.add(activePhase);
        commitToFirestore();
    }

    private void commitToFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference gameRef = db.collection("gameRooms").document(gameId);

        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(gameRef);
            if (!snap.exists()) return null;

            boolean allSolved = (localMatches.size() == TOTAL_ITEMS);
            String nextPhase = getNextPhase(activePhase, allSolved);

            // Identifikujemo kome idu bodovi (igraču koji je trenutno završio potez)
            boolean p1WasActing = activePhase.equals("P1_TURN") || activePhase.equals("P1_BONUS");
            String roundScoreField = p1WasActing ? "playerOneRoundScore" : "playerTwoRoundScore";
            String totalScoreField = p1WasActing ? "playerOneScore" : "playerTwoScore";

            long currentRoundScore = snap.contains(roundScoreField) ? snap.getLong(roundScoreField) : 0;
            long currentTotalScore = snap.contains(totalScoreField) ? snap.getLong(totalScoreField) : 0;

            Map<String, Object> updates = new HashMap<>();
            updates.put("roundPhase", nextPhase);

            // Dodavanje osvojenih bodova
            updates.put(roundScoreField, currentRoundScore + localPointsEarned);
            updates.put(totalScoreField, currentTotalScore + localPointsEarned);

            // Ako idemo u novi glavni potez (P2_TURN ili kraj), čistimo tablu od prethodne runde.
            // Ako idemo u BONUS potez, prosleđujemo trenutne tačne spojeve kako bi ih protivnik video kao zaključane.
            boolean clearMatches = nextPhase.equals("P2_TURN") || nextPhase.equals("MINIGAME_DONE");
            updates.put("spojniceMatches", clearMatches ? new HashMap<>() : localMatches);

            tx.update(gameRef, updates);
            return null;
        });
    }

    private String getNextPhase(String current, boolean solved) {
        if (current.equals("P1_TURN")) return solved ? "P2_TURN" : "P2_BONUS";
        if (current.equals("P2_BONUS")) return "P2_TURN";
        if (current.equals("P2_TURN")) return solved ? "MINIGAME_DONE" : "P1_BONUS";
        if (current.equals("P1_BONUS")) return "MINIGAME_DONE";
        return "MINIGAME_DONE";
    }

    private boolean isActivePlayer(String phase, GameRoom room) {
        if (phase.equals("P1_TURN") || phase.equals("P1_BONUS")) return myUsername.equals(room.getPlayerOne());
        return myUsername.equals(room.getPlayerTwo());
    }

    private void startTimer(long ms) {
        cancelTimer();
        turnTimer = new CountDownTimer(ms, 1000) {
            public void onTick(long m) {
                if(tvTimer != null) tvTimer.setText((m/1000) + "s");
            }
            public void onFinish() {
                if(tvTimer != null) tvTimer.setText("0s");
                finishTurn();
            }
        }.start();
    }

    private void cancelTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    private void showFinished() {
        cancelTimer();
        if (gridLeft != null) gridLeft.removeAllViews();
        if (gridRight != null) gridRight.removeAllViews();
        if (tvTimer != null) tvTimer.setText("Gotovo!");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
        handler.removeCallbacksAndMessages(null);
    }
}