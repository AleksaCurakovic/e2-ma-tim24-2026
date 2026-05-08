package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;

import java.util.HashMap;
import java.util.Map;

public class GameFragment extends Fragment {

    private static final Map<String, Class<? extends Fragment>> MINIGAME_REGISTRY = new HashMap<>();
    static {
        MINIGAME_REGISTRY.put("skocko", SkockoFragment.class);
        MINIGAME_REGISTRY.put("korakPoKorak", KorakFragment.class);
        MINIGAME_REGISTRY.put("mojbroj", MojBrojFragment.class);
        MINIGAME_REGISTRY.put("quiz", QuizFragment.class);
        MINIGAME_REGISTRY.put("spojnice", SpojniceFragment.class);
        MINIGAME_REGISTRY.put("asocijacije", AsocijacijeFragment.class);
    }

    private static final int ROUNDS_PER_GAME = 2;

    private GameViewModel vm;
    private String gameId;
    private String myUsername;
    private String currentMinigameType = null;
    private boolean navigatedToResults = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable resultsRunnable;
    private int roundsPlayed = 0;

    private FrameLayout layoutGame;

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
    }

    private void observeGameRoom(View view) {
        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room == null) return;


            if ("FINISHED".equals(room.getGameState())) {
                if (!navigatedToResults) {
                    navigatedToResults = true;
                    navigateToResults();
                }
                return;
            }

            String phase = room.getRoundPhase();
            if ("MINIGAME_DONE".equals(phase)) {
                roundsPlayed++;
                if (roundsPlayed >= ROUNDS_PER_GAME) {
                    roundsPlayed = 0;
                    currentMinigameType = null;
                    scheduleAdvance();
                } else {
                    scheduleNextRound();
                }
            } else {
                layoutGame.setVisibility(View.VISIBLE);

                String minigameType = room.getCurrentMinigameType();
                if (minigameType != null) {
                    boolean typeChanged = !minigameType.equals(currentMinigameType);
                    boolean round2Starting = "P1_TURN".equals(phase)
                            && minigameType.equals(currentMinigameType)
                            && roundsPlayed == 1;

                    if (typeChanged) {
                        currentMinigameType = minigameType;
                        roundsPlayed = 0;
                        loadMinigameFragment(minigameType);
                    } else if (round2Starting) {
                        loadMinigameFragment(minigameType);
                    }
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
            args.putInt("roundNumber", roundsPlayed + 1);
            minigameFragment.setArguments(args);

            getChildFragmentManager().beginTransaction()
                    .replace(R.id.layoutGame, minigameFragment)
                    .commit();
        } catch (Exception e) {
            vm.errorMessage.postValue("Failed to load minigame: " + e.getMessage());
        }
    }

    private void scheduleNextRound() {
        if (resultsRunnable != null) {
            handler.removeCallbacks(resultsRunnable);
        }
        resultsRunnable = () -> {
            GameRoom current = vm.gameRoom.getValue();
            if (current != null && myUsername.equals(current.getPlayerOne())) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("roundPhase", "P1_TURN");
                updates.put("playerOneRoundScore", 0);
                updates.put("playerTwoRoundScore", 0);
                vm.advancePhase(gameId, updates);
            }
        };
        handler.postDelayed(resultsRunnable, 1500);
    }

    private void scheduleAdvance() {
        if (resultsRunnable != null) {
            handler.removeCallbacks(resultsRunnable);
        }
        resultsRunnable = () -> {
            GameRoom current = vm.gameRoom.getValue();
            if (current != null && myUsername.equals(current.getPlayerOne())) {
                advanceToNextRound(current);
            }
        };
        handler.postDelayed(resultsRunnable, 2000);
    }

    private void advanceToNextRound(GameRoom room) {
        int nextIndex = room.getCurrentMinigameIndex() + 1;

        Map<String, Object> updates = new HashMap<>();

        if (nextIndex >= room.getMinigamePlaylist().size()) {
            updates.put("gameState", "FINISHED");
        } else {
            String next = room.getMinigamePlaylist().get(nextIndex);
            String type = next.contains(":") ? next.split(":")[0] : next;
            updates.put("currentMinigameIndex", nextIndex);
            updates.put("currentMinigameType", type);
            updates.put("playerOneRoundScore", 0);
            updates.put("playerTwoRoundScore", 0);
            updates.put("roundPhase", "P1_TURN");
        }

        vm.advancePhase(gameId, updates);
    }

    private void navigateToResults() {
        Bundle args = new Bundle();
        args.putString("gameId", gameId);
        args.putString("myUsername", myUsername);
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