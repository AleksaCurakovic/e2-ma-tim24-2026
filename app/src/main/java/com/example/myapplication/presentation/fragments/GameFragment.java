package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.presentation.viewModel.GameViewModel;

import java.util.HashMap;
import java.util.Map;

public class GameFragment extends Fragment {

    private static final Map<String, Class<? extends Fragment>> MINIGAME_REGISTRY = new HashMap<>();
    static {
        MINIGAME_REGISTRY.put("skocko", SkockoFragment.class);
    }

    private GameViewModel vm;
    private String gameId;
    private String myUsername;
    private String currentMinigameType = null;
    private boolean navigatedToResults = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable resultsRunnable;

    private FrameLayout layoutGame;
    private LinearLayout layoutResults;
    private TextView tvRoundScoreOne;
    private TextView tvRoundScoreTwo;
    private TextView tvCountdown;

    public GameFragment() {
        super(R.layout.fragment_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle b) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        gameId     = getArguments() != null ? getArguments().getString("gameId") : null;
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : vm.myUsername.getValue();

        initializeViews(view);
        vm.listen(gameId);

        observeGameRoom(view);
    }

    private void initializeViews(View view) {
        layoutGame = view.findViewById(R.id.layoutGame);
        layoutResults = view.findViewById(R.id.layoutResults);
        tvRoundScoreOne = view.findViewById(R.id.tvRoundScoreOne);
        tvRoundScoreTwo = view.findViewById(R.id.tvRoundScoreTwo);
        tvCountdown = view.findViewById(R.id.tvCountdown);
    }

    private void observeGameRoom(View view) {
        TextView tvPlayerOneName = view.findViewById(R.id.tvPlayerOneName);
        TextView tvPlayerTwoName = view.findViewById(R.id.tvPlayerTwoName);
        TextView tvPlayerOneScore = view.findViewById(R.id.tvPlayerOneScore);
        TextView tvPlayerTwoScore = view.findViewById(R.id.tvPlayerTwoScore);
        TextView tvMinigameLabel = view.findViewById(R.id.tvMinigameLabel);

        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room == null) return;

            tvPlayerOneName.setText(room.getPlayerOne());
            tvPlayerTwoName.setText(room.getPlayerTwo());
            tvPlayerOneScore.setText(String.valueOf(room.getPlayerOneScore()));
            tvPlayerTwoScore.setText(String.valueOf(room.getPlayerTwoScore()));

            int current = room.getCurrentMinigameIndex() + 1;
            int total = room.getMinigamePlaylist() != null ? room.getMinigamePlaylist().size() : 0;
            tvMinigameLabel.setText("Minigame " + current + " / " + total);

            if ("FINISHED".equals(room.getGameState())) {
                if (!navigatedToResults) {
                    navigatedToResults = true;
                    navigateToResults();
                }
                return;
            }

            String phase = room.getRoundPhase();
            if ("SHOWING_RESULTS".equals(phase)) {
                showResults(room);
            } else {
                layoutResults.setVisibility(View.GONE);
                layoutGame.setVisibility(View.VISIBLE);

                String minigameType = room.getCurrentMinigameType();
                if (minigameType != null && !minigameType.equals(currentMinigameType)) {
                    currentMinigameType = minigameType;
                    loadMinigameFragment(minigameType);
                }
            }
        });
    }

    private void loadMinigameFragment(String type) {
        Class<? extends Fragment> fragmentClass = MINIGAME_REGISTRY.get(type);
        if (fragmentClass == null) {
            vm.errorMessage.postValue("Unknown minigame: " + type);
            return;
        }

        try {
            Fragment minigameFragment = fragmentClass.newInstance();
            Bundle args = new Bundle();
            args.putString("gameId", gameId);
            args.putString("myUsername", myUsername);
            minigameFragment.setArguments(args);

            getChildFragmentManager().beginTransaction()
                    .replace(R.id.layoutGame, minigameFragment)
                    .commit();
        } catch (Exception e) {
            vm.errorMessage.postValue("Failed to load minigame: " + e.getMessage());
        }
    }

    private void showResults(com.example.myapplication.data.model.GameRoom room) {
        layoutGame.setVisibility(View.GONE);
        layoutResults.setVisibility(View.VISIBLE);

        tvRoundScoreOne.setText("+" + room.getPlayerOneRoundScore());
        tvRoundScoreTwo.setText("+" + room.getPlayerTwoRoundScore());
        tvCountdown.setText("Next round in 3s...");

        if (resultsRunnable != null) {
            handler.removeCallbacks(resultsRunnable);
        }

        resultsRunnable = () -> {
            com.example.myapplication.data.model.GameRoom current = vm.gameRoom.getValue();
            if (current != null && myUsername.equals(current.getPlayerOne())) {
                advanceToNextRound(current);
            }
        };
        handler.postDelayed(resultsRunnable, 3000);
    }

    private void advanceToNextRound(com.example.myapplication.data.model.GameRoom room) {
        int roundNum = room.getRoundNumber();
        int nextIndex = room.getCurrentMinigameIndex();

        Map<String, Object> updates = new HashMap<>();

        if (roundNum == 0) {
            updates.put("roundNumber", 1);
            updates.put("playerOneRoundScore", 0);
            updates.put("playerTwoRoundScore", 0);
            updates.put("roundPhase", "P2_TURN");
        } else if (roundNum == 1) {
            nextIndex++;
            if (nextIndex >= room.getMinigamePlaylist().size()) {
                updates.put("gameState", "FINISHED");
            } else {
                updates.put("currentMinigameIndex", nextIndex);
                updates.put("roundNumber", 0);
                updates.put("playerOneRoundScore", 0);
                updates.put("playerTwoRoundScore", 0);
                updates.put("roundPhase", "P1_TURN");
                
                String next = room.getMinigamePlaylist().get(nextIndex);
                String type = next.contains(":") ? next.split(":")[0] : next;
                updates.put("currentMinigameType", type);
            }
        }

        vm.advancePhase(gameId, updates);
    }

    private void navigateToResults() {
        Bundle args = new Bundle();
        args.putString("gameId", gameId);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_gameFragment_to_resultsFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (resultsRunnable != null) {
            handler.removeCallbacks(resultsRunnable);
        }
    }
}