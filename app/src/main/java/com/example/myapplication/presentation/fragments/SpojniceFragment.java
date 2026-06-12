package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    private static final long PHASE_DURATION_MS = 30_000L;

    private final List<String> left = new ArrayList<>();
    private final List<String> right = new ArrayList<>();
    private final Map<String, String> pairs = new HashMap<>();

    private GameViewModel vm;
    private String gameId;
    private String myUsername;
    private TextView tvTimer;
    private TextView tvResult;
    private GridLayout gridLeft;
    private GridLayout gridRight;
    private CountDownTimer phaseTimer;
    private String pendingLeft = null;
    private String activePhase = null;
    private boolean phaseFinishing = false;

    public SpojniceFragment() {
        super(R.layout.fragment_spojnice);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : null;
        gameId = getArguments() != null ? getArguments().getString("gameId") : null;

        tvTimer = view.findViewById(R.id.tvSpojniceTimer);
        tvResult = view.findViewById(R.id.tvSpojniceResult);
        gridLeft = view.findViewById(R.id.gridLeft);
        gridRight = view.findViewById(R.id.gridRight);

        vm.gameRoom.observe(getViewLifecycleOwner(), this::onRoomUpdated);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

    private void onRoomUpdated(GameRoom room) {
        if (room == null || !"spojnice".equals(room.getCurrentMinigameType())) return;

        loadRoundData(room.getSpojniceRoundIndex());
        render(room);

        boolean active = isActivePlayer(room);
        if (!room.getRoundPhase().equals(activePhase)) {
            activePhase = room.getRoundPhase();
            phaseFinishing = false;
            pendingLeft = null;
            if (active && !"MINIGAME_DONE".equals(activePhase)) {
                startTimer(room);
            } else {
                cancelTimer();
                tvTimer.setText("30s");
            }
        }

        if (!active) {
            String turn = activePlayerName(room);
            tvResult.setText("Spojnice - ceka se " + turn);
        }
    }

    private void loadRoundData(int roundIndex) {
        left.clear();
        right.clear();
        pairs.clear();

        if (roundIndex == 0) {
            left.addAll(Arrays.asList("Queen", "Metallica", "ABBA", "The Beatles", "Nirvana"));
            pairs.put("Queen", "Bohemian Rhapsody");
            pairs.put("Metallica", "Nothing Else Matters");
            pairs.put("ABBA", "Dancing Queen");
            pairs.put("The Beatles", "Hey Jude");
            pairs.put("Nirvana", "Smells Like Teen Spirit");
        } else {
            left.addAll(Arrays.asList("Srbija", "Francuska", "Japan", "Brazil", "Egipat"));
            pairs.put("Srbija", "Beograd");
            pairs.put("Francuska", "Pariz");
            pairs.put("Japan", "Tokio");
            pairs.put("Brazil", "Brazilija");
            pairs.put("Egipat", "Kairo");
        }

        for (String item : left) right.add(pairs.get(item));
        Collections.shuffle(right);
    }

    private void render(GameRoom room) {
        setupColumn(gridLeft, left, true, room);
        setupColumn(gridRight, right, false, room);
    }

    private void setupColumn(GridLayout grid, List<String> items, boolean isLeft, GameRoom room) {
        grid.removeAllViews();
        grid.setColumnCount(1);
        List<String> solved = safeStrings(room.getSpojniceSolvedLeft());
        List<String> attempted = safeStrings(room.getSpojniceAttemptedLeft());

        for (String text : items) {
            MaterialCardView card = (MaterialCardView) View.inflate(requireContext(), R.layout.item_spojnice_card, null);
            TextView tv = card.findViewById(R.id.tvText);
            tv.setText(text);

            boolean disabled = false;
            if (isLeft) {
                disabled = solved.contains(text) || attempted.contains(text);
            } else {
                disabled = isRightSolved(text, solved);
            }

            if (disabled) {
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_absent));
                card.setEnabled(false);
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_cell_filled));
                card.setEnabled(isActivePlayer(room));
                card.setOnClickListener(v -> onCardClicked(text, isLeft, room));
            }

            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = ViewGroup.LayoutParams.MATCH_PARENT;
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(p);
            grid.addView(card);
        }
    }

    private void onCardClicked(String text, boolean isLeft, GameRoom room) {
        if (!isActivePlayer(room) || phaseFinishing) return;

        if (isLeft) {
            pendingLeft = text;
            tvResult.setText("Izabrano: " + text);
            return;
        }

        if (pendingLeft == null) return;

        List<String> solved = safeStrings(room.getSpojniceSolvedLeft());
        List<String> attempted = safeStrings(room.getSpojniceAttemptedLeft());
        if (!attempted.contains(pendingLeft)) attempted.add(pendingLeft);

        boolean correct = text.equals(pairs.get(pendingLeft));
        int delta = 0;
        if (correct && !solved.contains(pendingLeft)) {
            solved.add(pendingLeft);
            delta = 2;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("spojniceSolvedLeft", solved);
        updates.put("spojniceAttemptedLeft", attempted);
        addScoreUpdate(room, updates, delta);

        tvResult.setText(correct ? "Tacno +2" : "Pogresno");
        pendingLeft = null;

        if (solved.size() == left.size() || attempted.size() == left.size()) {
            addNextPhaseUpdate(room, updates);
        }
        vm.advancePhase(gameId, updates);
    }

    private void startTimer(GameRoom room) {
        cancelTimer();
        tvTimer.setText("30s");
        phaseTimer = new CountDownTimer(PHASE_DURATION_MS, 500L) {
            @Override
            public void onTick(long ms) {
                if (isAdded()) tvTimer.setText((ms / 1000) + "s");
            }

            @Override
            public void onFinish() {
                if (!isAdded() || phaseFinishing) return;
                tvTimer.setText("0s");
                finishPhase(room);
            }
        }.start();
    }

    private void finishPhase(GameRoom room) {
        if (phaseFinishing || !isActivePlayer(room)) return;
        phaseFinishing = true;
        Map<String, Object> updates = new HashMap<>();
        addNextPhaseUpdate(room, updates);
        vm.advancePhase(gameId, updates);
    }

    private void addNextPhaseUpdate(GameRoom room, Map<String, Object> updates) {
        String phase = room.getRoundPhase();
        List<String> solved = safeStrings(room.getSpojniceSolvedLeft());
        boolean allSolved = solved.size() == left.size();

        if ("P1_TURN".equals(phase)) {
            if (allSolved) {
                startSecondRound(updates);
            } else {
                updates.put("roundPhase", "P2_BONUS");
            }
        } else if ("P2_BONUS".equals(phase)) {
            startSecondRound(updates);
        } else if ("P2_TURN".equals(phase)) {
            updates.put("roundPhase", allSolved ? "MINIGAME_DONE" : "P1_BONUS");
        } else {
            updates.put("roundPhase", "MINIGAME_DONE");
        }
    }

    private void startSecondRound(Map<String, Object> updates) {
        updates.put("roundPhase", "P2_TURN");
        updates.put("spojniceRoundIndex", 1);
        updates.put("spojniceSolvedLeft", new ArrayList<String>());
        updates.put("spojniceAttemptedLeft", new ArrayList<String>());
    }

    private void addScoreUpdate(GameRoom room, Map<String, Object> updates, int delta) {
        if (delta == 0) return;
        String player = activePlayerName(room);
        if (player.equals(room.getPlayerOne())) {
            updates.put("playerOneRoundScore", room.getPlayerOneRoundScore() + delta);
            updates.put("playerOneScore", room.getPlayerOneScore() + delta);
        } else {
            updates.put("playerTwoRoundScore", room.getPlayerTwoRoundScore() + delta);
            updates.put("playerTwoScore", room.getPlayerTwoScore() + delta);
        }
    }

    private boolean isActivePlayer(GameRoom room) {
        return myUsername != null && myUsername.equals(activePlayerName(room));
    }

    private String activePlayerName(GameRoom room) {
        switch (room.getRoundPhase()) {
            case "P1_TURN":
            case "P1_BONUS":
                return room.getPlayerOne();
            case "P2_TURN":
            case "P2_BONUS":
                return room.getPlayerTwo();
            default:
                return "";
        }
    }

    private boolean isRightSolved(String rightText, List<String> solvedLeft) {
        for (String leftText : solvedLeft) {
            if (rightText.equals(pairs.get(leftText))) return true;
        }
        return false;
    }

    private List<String> safeStrings(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private void cancelTimer() {
        if (phaseTimer != null) {
            phaseTimer.cancel();
            phaseTimer = null;
        }
    }

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
