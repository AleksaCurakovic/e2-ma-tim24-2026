package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;

public class GameFragment extends Fragment {

    private GameViewModel vm;
    private String gameId;
    private String myId;

    private String lastHandledPhase = "";

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable advanceRunnable;

    // Progress bar timer state
    private ProgressBar progressBar;
    private int timerDurationMs;
    private long timerStartMs;

    // Runnable that updates progress bar every 100ms
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (progressBar == null) return;
            long elapsed = System.currentTimeMillis() - timerStartMs;
            long remaining = timerDurationMs - elapsed;
            if (remaining <= 0) {
                progressBar.setProgress(0);
                return;
            }
            int progress = (int) ((remaining * 100) / timerDurationMs);
            progressBar.setProgress(progress);
            timerHandler.postDelayed(this, 100);
        }
    };

    public GameFragment() {
        super(R.layout.fragment_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle b) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        gameId = getArguments().getString("gameId");
        myId   = vm.myUsername.getValue();

        progressBar = view.findViewById(R.id.skockoProgressBar);

        vm.listen(gameId);

        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room == null) return;

            if ("FINISHED".equals(room.getGameState())) {
                cancelAllTimers();
                navigateToResults();
                return;
            }

            String phase = room.getRoundPhase();
            if (phase.equals(lastHandledPhase)) return;
            lastHandledPhase = phase;

            cancelAllTimers();

            switch (phase) {

                case "P1_TURN":
                    if (myId.equals(room.getPlayerOne())) {
                        navigateToSkocko(room, false); // false = main turn, not bonus
                    } else {
                        showWaiting(room.getPlayerOne() + " is playing...");
                    }
                    break;

                case "P2_BONUS":
                    // P1 failed — P2 gets 10s to guess P1's combination
                    if (myId.equals(room.getPlayerTwo())) {
                        navigateToSkockoBonus(room); // P2 sees P1's attempts + gets 1 guess
                    } else {
                        showWaiting("Waiting for " + room.getPlayerTwo() + "'s bonus attempt...");
                    }
                    break;

                case "P2_TURN":
                    if (myId.equals(room.getPlayerTwo())) {
                        navigateToSkocko(room, false);
                    } else {
                        showWaiting(room.getPlayerTwo() + " is playing...");
                    }
                    break;

                case "P1_BONUS":
                    // P2 failed — P1 gets 10s to guess P2's combination
                    if (myId.equals(room.getPlayerOne())) {
                        navigateToSkockoBonus(room); // P1 sees P2's attempts + gets 1 guess
                    } else {
                        showWaiting("Waiting for " + room.getPlayerOne() + "'s bonus attempt...");
                    }
                    break;

                case "SHOWING_RESULTS":
                    showResults(room);
                    startProgressTimer(5000, () -> {
                        // Only playerOne advances to avoid double-firing
                        if (myId.equals(room.getPlayerOne())) {
                            vm.advanceRound(gameId);
                        }
                    });
                    break;
            }
        });
    }

    // =========================================================================
    // NAVIGATION TO MINIGAMES
    // =========================================================================

    private void navigateToSkocko(GameRoom room, boolean isBonusMode) {
        // Load puzzle data first, then navigate
        boolean isPlayerOne = myId.equals(room.getPlayerOne());
        String docId = room.getSkockoDocId();

        vm.loadSkockoData(docId, isPlayerOne);

        Bundle args = new Bundle();
        args.putString("gameId", gameId);
        args.putBoolean("isBonusMode", isBonusMode);
        args.putInt("turnDurationMs", 30000);

        Navigation.findNavController(requireView())
                .navigate(R.id.action_gameFragment_to_skockoFragment, args);
    }

    private void navigateToSkockoBonus(GameRoom room) {
        boolean isPlayerOne = myId.equals(room.getPlayerOne());
        String docId = room.getSkockoDocId();

        vm.loadSkockoData(docId, isPlayerOne);

        Bundle args = new Bundle();
        args.putString("gameId", gameId);
        args.putBoolean("isBonusMode", true);
        args.putInt("turnDurationMs", 10000);

        Navigation.findNavController(requireView())
                .navigate(R.id.action_gameFragment_to_skockoFragment, args);
    }

    // =========================================================================
    // UI STATES (waiting + results shown directly in GameFragment)
    // =========================================================================

    private void showWaiting(String message) {
        requireView().findViewById(R.id.layoutResults).setVisibility(View.GONE);
        requireView().findViewById(R.id.layoutWaiting).setVisibility(View.VISIBLE);
        ((TextView) requireView().findViewById(R.id.tvWaiting)).setText(message);
        startProgressTimer(30000, null); // show passive timer while waiting
    }

    private void showResults(GameRoom room) {
        requireView().findViewById(R.id.layoutWaiting).setVisibility(View.GONE);
        requireView().findViewById(R.id.layoutResults).setVisibility(View.VISIBLE);

        ((TextView) requireView().findViewById(R.id.tvRoundScoreOne))
                .setText("+" + room.getPlayerOneRoundScore());
        ((TextView) requireView().findViewById(R.id.tvRoundScoreTwo))
                .setText("+" + room.getPlayerTwoRoundScore());
        ((TextView) requireView().findViewById(R.id.tvRoundLabelOne))
                .setText(room.getPlayerOne());
        ((TextView) requireView().findViewById(R.id.tvRoundLabelTwo))
                .setText(room.getPlayerTwo());
    }

    private void navigateToResults() {
        Bundle args = new Bundle();
        args.putString("gameId", gameId);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_gameFragment_to_resultsFragment, args);
    }

    // =========================================================================
    // TIMER + PROGRESS BAR
    // =========================================================================

    /**
     * Starts the progress bar countdown and fires onFinish when done.
     * onFinish can be null (e.g. for passive waiting display).
     */
    private void startProgressTimer(int durationMs, Runnable onFinish) {
        timerDurationMs = durationMs;
        timerStartMs    = System.currentTimeMillis();

        if (progressBar != null) {
            progressBar.setMax(100);
            progressBar.setProgress(100);
            timerHandler.post(progressUpdater);
        }

        if (onFinish != null) {
            advanceRunnable = onFinish;
            timerHandler.postDelayed(advanceRunnable, durationMs);
        }
    }

    private void cancelAllTimers() {
        timerHandler.removeCallbacks(progressUpdater);
        if (advanceRunnable != null) {
            timerHandler.removeCallbacks(advanceRunnable);
            advanceRunnable = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelAllTimers();
    }
}