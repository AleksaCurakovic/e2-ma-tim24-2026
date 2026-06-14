package com.example.myapplication.presentation.viewModel;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.data.repository.GameRepository;
import com.example.myapplication.service.GameService;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameViewModel extends ViewModel {

    private final GameRepository repository = new GameRepository();
    private final GameService    gameService = new GameService(repository);

    // --- Matchmaking ---
    public final MutableLiveData<String>  navigateToGame = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading      = new MutableLiveData<>(false);
    public final MutableLiveData<String>  errorMessage   = new MutableLiveData<>();

    // --- Session info ---
    public final MutableLiveData<String>   myUsername = new MutableLiveData<>();
    public final MutableLiveData<String>   gameId     = new MutableLiveData<>();
    public final MutableLiveData<GameRoom> gameRoom   = new MutableLiveData<>();

    // Derived from gameRoom
    public final MutableLiveData<String>       playerOne = new MutableLiveData<>();
    public final MutableLiveData<String>       playerTwo = new MutableLiveData<>();
    public final MutableLiveData<List<String>> playlist  = new MutableLiveData<>();
    public final MutableLiveData<String>       currentPhase = new MutableLiveData<>();

    // --- Presence (heartbeat) ---
    // [p1LastSeen, p2LastSeen] u milisekundama; 0 = još neviđen.
    public final MutableLiveData<long[]> presence = new MutableLiveData<>(new long[]{0, 0});
    private static final long ABSENT_THRESHOLD_MS = 5_000L;
    private static final long HEARTBEAT_MS = 2_000L;
    private Handler heartbeatHandler;
    private boolean presenceStarted = false;

    // =========================================================================
    // MATCHMAKING
    // =========================================================================

    public void startMatchmaking(String username) {
        myUsername.setValue(username);
        isLoading.setValue(true);
        gameService.startMatchmaking(username,
                gid -> {
                    isLoading.postValue(false);
                    gameId.postValue(gid);
                    navigateToGame.postValue(gid);
                },
                e -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(e.getMessage());
                });
    }

    private String listenedGameId;

    public void listen(String gameId) {
        if (gameId == null) return;
        if (gameId.equals(listenedGameId)) return; // već slušamo ovu partiju
        listenedGameId = gameId;

        // Očisti stanje prethodne (završene) partije da nova krene čisto i da
        // GameFragment ne reaguje na zaostalu FINISHED sobu.
        gameRoom.setValue(null);
        currentPhase.setValue(null);

        repository.listenToGameRoom(gameId,
                room -> {
                    gameRoom.postValue(room);
                    playerOne.postValue(room.getPlayerOne());
                    playerTwo.postValue(room.getPlayerTwo());
                    playlist.postValue(room.getMinigamePlaylist());
                    currentPhase.postValue(room.getRoundPhase());
                },
                e -> errorMessage.postValue("Sync error: " + e.getMessage())
        );
    }

    /** Pokreće periodični heartbeat lokalnog igrača i slušanje prisustva oba igrača. */
    public void startPresence(String gameId, boolean amIPlayerOne) {
        if (presenceStarted || gameId == null) return;
        presenceStarted = true;

        repository.listenPresence(gameId, data -> {
            long p1 = toLong(data.get("p1LastSeen"));
            long p2 = toLong(data.get("p2LastSeen"));
            presence.postValue(new long[]{p1, p2});
        }, e -> { /* tiho */ });

        heartbeatHandler = new Handler(Looper.getMainLooper());
        Runnable beat = new Runnable() {
            @Override public void run() {
                repository.sendHeartbeat(gameId, amIPlayerOne);
                heartbeatHandler.postDelayed(this, HEARTBEAT_MS);
            }
        };
        heartbeatHandler.post(beat);
    }

    public void stopPresence(String gameId) {
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacksAndMessages(null);
            heartbeatHandler = null;
        }
        repository.detachPresence();
        repository.deletePresence(gameId);
        presenceStarted = false;
    }

    /**
     * Pri napuštanju partije: prekida slanje heartbeat-a i odmah označava sebe odsutnim
     * (bez brisanja presence dokumenta, da protivnik može da detektuje odsustvo).
     */
    public void markSelfAbsent(String gameId, boolean amIPlayerOne) {
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacksAndMessages(null);
            heartbeatHandler = null;
        }
        repository.markAbsent(gameId, amIPlayerOne);
        repository.detachPresence();
        presenceStarted = false;
    }

    /**
     * Da li je igrač trenutno prisutan. Dok ga nismo ni videli (lastSeen==0) tretiramo
     * ga kao prisutnog (igra tek počinje); odsutan je tek kad mu heartbeat zastari.
     */
    public boolean isPlayerPresent(boolean isPlayerOne) {
        long[] p = presence.getValue();
        if (p == null) return true;
        long seen = isPlayerOne ? p[0] : p[1];
        if (seen == 0) return true;
        return System.currentTimeMillis() - seen < ABSENT_THRESHOLD_MS;
    }

    private static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    public void advancePhase(String gameId, Map<String, Object> updates) {
        gameService.updateGameRoom(gameId, updates,
                unused -> {},
                e -> errorMessage.postValue("Failed to update: " + e.getMessage())
        );
    }

    public void deleteRoom(String gameId) {
        gameService.deleteGameRoom(gameId,
                unused -> {},
                e -> errorMessage.postValue("Failed to delete room: " + e.getMessage())
        );
    }

    public void fetchSkockoSolution(String docId, String solutionField,
                                    com.google.android.gms.tasks.OnSuccessListener<List<String>> onSuccess,
                                    com.google.android.gms.tasks.OnFailureListener onFailure) {
        gameService.fetchSkockoSolution(docId, solutionField, onSuccess, onFailure);
    }

    public List<String> calculateFeedback(List<String> guess, List<String> solution) {
        return gameService.calculateFeedback(guess, solution);
    }

    // =========================================================================
    // MOJ BROJ
    // =========================================================================

    public int generateMojBrojTarget() {
        return gameService.generateTarget();
    }

    public List<Integer> generateMojBrojNumbers() {
        return gameService.generateNumbers();
    }

    public void publishMojBrojTarget(String gameId, int target) {
        gameService.publishMojBrojTarget(gameId, target,
                unused -> {},
                e -> errorMessage.postValue("Failed to publish target: " + e.getMessage()));
    }

    public void publishMojBrojNumbers(String gameId, List<Integer> numbers) {
        gameService.publishMojBrojNumbers(gameId, numbers,
                unused -> {},
                e -> errorMessage.postValue("Failed to publish numbers: " + e.getMessage()));
    }

    public void submitMojBrojResult(String gameId, boolean isPlayerOne, int result) {
        gameService.submitMojBrojResult(gameId, isPlayerOne, result,
                unused -> {},
                e -> errorMessage.postValue("Failed to submit result: " + e.getMessage()));
    }

    public void scoreMojBrojRound(String gameId, int target, int p1Result, int p2Result,
                                  boolean p1IsRoundOwner, int currentP1Score, int currentP2Score) {
        Map<String, Object> updates = gameService.scoreMojBrojRound(
                target, p1Result, p2Result, p1IsRoundOwner, currentP1Score, currentP2Score);
        advancePhase(gameId, updates);
    }

    public void fetchSpojniceData(String docId,
                                  com.google.android.gms.tasks.OnSuccessListener<Map<String, Object>> onSuccess,
                                  com.google.android.gms.tasks.OnFailureListener onFailure) {
        gameService.fetchSpojniceData(docId, onSuccess, onFailure);
    }

    public void fetchKoznaZnaData(String docId,
                              com.google.android.gms.tasks.OnSuccessListener<Map<String, Object>> onSuccess,
                              com.google.android.gms.tasks.OnFailureListener onFailure) {
        gameService.fetchKoZnaZnaData(docId, onSuccess, onFailure);
    }
    public void fetchKorakSolution(String docId, String playerPrefix,
                                   com.google.android.gms.tasks.OnSuccessListener<Map<String, Object>> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        gameService.fetchKorakSolution(docId, playerPrefix, onSuccess, onFailure);
    }

    public int computeStarsDelta(int myScore, boolean iWon) {
        return gameService.computeStarsDelta(myScore, iWon);
    }


    public void fetchAssociationQuestion(
            String questionId,
            OnSuccessListener<DocumentSnapshot> onSuccess,
            OnFailureListener onFailure) {
        gameService.fetchAssociationQuestion(questionId, onSuccess, onFailure);
    }


    public void submitAsocScore(
            String gameId,
            boolean isPlayerOne,
            Set<String> openedCells,
            Set<Integer> solvedCols,
            String nextPhase
    ) {
        GameRoom room = gameRoom.getValue();
        if (room == null) return;
        Map<String, Object> updates = gameService.scoreAsocRound(
                isPlayerOne,
                room.getPlayerOneScore(),
                room.getPlayerTwoScore(),
                openedCells,
                solvedCols,
                nextPhase
        );
        updates.put("asocTurnPlayer", "");
        updates.put("asocOpenedCells", new java.util.ArrayList<>());
        updates.put("asocSolvedColumns", new java.util.ArrayList<>());
        updates.put("asocFinalSolved", false);
        advancePhase(gameId, updates);
    }

    @Override
    public void onCleared() {
        super.onCleared();
        repository.detachListeners();
    }
}