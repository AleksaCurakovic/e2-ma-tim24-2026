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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GameFragment extends Fragment {

    private static final Map<String, Class<? extends Fragment>> MINIGAME_REGISTRY = new HashMap<>();
    static {
        MINIGAME_REGISTRY.put("skocko", SkockoFragment.class);
        MINIGAME_REGISTRY.put("korakPoKorak", KorakFragment.class);
        MINIGAME_REGISTRY.put("mojBroj", MojBrojFragment.class);
        MINIGAME_REGISTRY.put("koZnaZna", QuizFragment.class);
        MINIGAME_REGISTRY.put("spojnice", SpojniceFragment.class);
        MINIGAME_REGISTRY.put("asocijacije", AsocijacijeFragment.class);
    }

    private static final int ROUNDS_PER_GAME = 2;

    private GameViewModel vm;
    private com.example.myapplication.presentation.viewModel.HomeViewModel homeVm;
    private String gameId;
    private String myUsername;
    private String currentMinigameType = null;
    private boolean navigatedToResults = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable resultsRunnable;
    private int roundsPlayed = 0;

    // --- Prisustvo / napuštanje ---
    private boolean presenceInit = false;
    private boolean amIPlayerOne = false;
    private Runnable watchdogRunnable;
    private String pendingSkipPhase;   // faza za koju merimo trajanje odsustva
    private long pendingSkipSince;     // kada je odsustvo prvi put detektovano
    private String lastSkippedPhase;   // poslednja faza koju smo već preskočili
    private static final long SKIP_CONFIRM_MS = 1500L;
    private static final long WATCHDOG_TICK_MS = 1500L;

    private FrameLayout layoutGame;

    public GameFragment() {
        super(R.layout.fragment_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle b) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        homeVm = new ViewModelProvider(requireActivity())
                .get(com.example.myapplication.presentation.viewModel.HomeViewModel.class);

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

            // Pokreni praćenje prisustva (heartbeat) i nadzor odsutnog igrača jednom,
            // kad prvi put znamo ko je playerOne.
            if (!presenceInit && myUsername != null) {
                presenceInit = true;
                amIPlayerOne = myUsername.equals(room.getPlayerOne());
                vm.startPresence(gameId, amIPlayerOne);
                startAbsenceWatchdog();
            }

            if ("FINISHED".equals(room.getGameState())) {
                if (!navigatedToResults) {
                    navigatedToResults = true;
                    navigateToResults();
                }
                return;
            }

            String phase = room.getRoundPhase();
            if ("MINIGAME_DONE".equals(phase)) {
                roundsPlayed = 0; // Resetuj brojač za sledeću igru
                currentMinigameType = null;

                // Samo scheduleAdvance, bez provere rundi
                scheduleAdvance();
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
            if (current != null && shouldIDriveSequencing(current)) {
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
            if (current != null && shouldIDriveSequencing(current)) {
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
        if (resultsRunnable != null) handler.removeCallbacks(resultsRunnable);
        if (watchdogRunnable != null) handler.removeCallbacks(watchdogRunnable);

        boolean rotating = getActivity() != null && getActivity().isChangingConfigurations();
        if (rotating) return; // rotacija nije napuštanje

        if (navigatedToResults) {
            // Normalan kraj partije — očisti presence dokument.
            vm.stopPresence(gameId);
        } else {
            handleLeaveIfAbandoned();
        }
    }

    /**
     * Napuštanje partije pre kraja (taster nazad, izlazak iz ekrana, gašenje aplikacije):
     * igrač gubi i ne dobija zvezde, ali PROTIVNIK NASTAVLJA partiju. Označavamo igrača
     * odsutnim (heartbeat zastareva) i postavljamo leftPlayer kao oznaku predaje; protivnik
     * preko nadzora prisustva preskače poteze odsutnog i dovršava preostale miniigre.
     */
    private void handleLeaveIfAbandoned() {
        if (gameId == null || myUsername == null) return;

        GameRoom room = vm.gameRoom.getValue();
        if (room != null && !"FINISHED".equals(room.getGameState())
                && room.getLeftPlayer() == null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("leftPlayer", myUsername); // bez gameState=FINISHED → protivnik nastavlja
            vm.advancePhase(gameId, updates);
        }

        // Prestani da šalješ heartbeat i označi sebe odsutnim (ne brišemo presence dokument).
        vm.markSelfAbsent(gameId, amIPlayerOne);

        if (homeVm != null) homeVm.setInGame(false);
    }

    // ------------------------------------------------------------- PRISUSTVO

    /**
     * Sekvenciranje (prelaz između miniigara) inače vodi playerOne. Ako je playerOne
     * napustio partiju, vođenje preuzima prisutni playerTwo da partija ne bi stala.
     */
    private boolean shouldIDriveSequencing(GameRoom room) {
        boolean p1Present = vm.isPlayerPresent(true);
        String orchestrator = p1Present ? room.getPlayerOne() : room.getPlayerTwo();
        return myUsername != null && myUsername.equals(orchestrator);
    }

    private void startAbsenceWatchdog() {
        if (watchdogRunnable != null) return;
        watchdogRunnable = new Runnable() {
            @Override public void run() {
                checkAbsentAndSkip();
                handler.postDelayed(this, WATCHDOG_TICK_MS);
            }
        };
        handler.postDelayed(watchdogRunnable, WATCHDOG_TICK_MS);
    }

    /**
     * Ako je na potezu igrač koji je odsutan, prisutni igrač posle kratke potvrde
     * odsustva preskače njegov potez (0 bodova) i prelazi dalje — bez čekanja tajmera.
     */
    private void checkAbsentAndSkip() {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || "FINISHED".equals(room.getGameState())) { pendingSkipPhase = null; return; }

        String phase = room.getRoundPhase();
        String type  = room.getCurrentMinigameType();
        if (phase == null || "MINIGAME_DONE".equals(phase)) { pendingSkipPhase = null; return; }
        // Quiz je simultan (oba igrača igraju istovremeno) — rešava se preuzimanjem
        // vođenja napredovanja pitanja, ne preskakanjem faze.
        if ("koZnaZna".equals(type)) { pendingSkipPhase = null; return; }

        boolean activeIsP1 = phase.startsWith("P1");
        boolean activeIsP2 = phase.startsWith("P2");
        if (!activeIsP1 && !activeIsP2) { pendingSkipPhase = null; return; }

        // Faza se promenila u odnosu na onu koju smo već preskočili → reset oznake.
        if (lastSkippedPhase != null && !lastSkippedPhase.equals(phase)) lastSkippedPhase = null;

        boolean iAmActive = (activeIsP1 && amIPlayerOne) || (activeIsP2 && !amIPlayerOne);
        if (iAmActive) { pendingSkipPhase = null; return; } // svoj potez igram normalno

        if (vm.isPlayerPresent(activeIsP1)) { pendingSkipPhase = null; return; } // protivnik je prisutan
        if (phase.equals(lastSkippedPhase)) return; // već poslat preskok za ovu fazu

        long now = System.currentTimeMillis();
        if (!phase.equals(pendingSkipPhase)) { pendingSkipPhase = phase; pendingSkipSince = now; return; }
        if (now - pendingSkipSince < SKIP_CONFIRM_MS) return;

        performAbsentSkip(type, phase, activeIsP1);
        lastSkippedPhase = phase;
        pendingSkipPhase = null;
    }

    private void performAbsentSkip(String type, String phase, boolean absentIsP1) {
        Map<String, Object> updates = new HashMap<>();
        String next;
        switch (type != null ? type : "") {
            case "skocko":
            case "spojnice":
            case "korakPoKorak":
                next = phase.startsWith("P1")
                        ? (phase.equals("P1_TURN") ? "P2_TURN" : "MINIGAME_DONE")
                        : (phase.equals("P2_TURN") ? "MINIGAME_DONE" : "P2_TURN");
                updates.put(absentIsP1 ? "playerOneRoundScore" : "playerTwoRoundScore", 0);
                break;
            case "asocijacije":
                next = "P1_TURN".equals(phase) ? "P2_TURN" : "MINIGAME_DONE";
                updates.put("asocOpenedCells", new ArrayList<>());
                updates.put("asocSolvedColumns", new ArrayList<>());
                updates.put("asocTurnPlayer", "");
                updates.put("asocFinalSolved", false);
                updates.put(absentIsP1 ? "playerOneRoundScore" : "playerTwoRoundScore", 0);
                break;
            case "mojBroj":
                // Vlasnik runde generiše brojeve; ako je odsutan, preskačemo celu rundu.
                next = "MINIGAME_DONE";
                updates.put("mojBrojTarget", null);
                updates.put("mojBrojNumbers", null);
                updates.put("mojBrojP1Submitted", false);
                updates.put("mojBrojP2Submitted", false);
                updates.put("mojBrojP1Result", 0);
                updates.put("mojBrojP2Result", 0);
                break;
            default:
                next = "MINIGAME_DONE";
        }
        updates.put("roundPhase", next);
        vm.advancePhase(gameId, updates);
    }
}