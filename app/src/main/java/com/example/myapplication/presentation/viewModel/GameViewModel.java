package com.example.myapplication.presentation.viewModel;

import android.os.CountDownTimer;

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

    private boolean listening = false;

    public void listen(String gameId) {
        if (listening) return;
        listening = true;
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