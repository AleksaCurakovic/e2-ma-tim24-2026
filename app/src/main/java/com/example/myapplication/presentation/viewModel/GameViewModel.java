package com.example.myapplication.presentation.viewModel;

import android.os.CountDownTimer;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.data.repository.GameRepository;
import com.example.myapplication.service.GameService;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

/**
 * Single shared ViewModel for the entire game session.
 * Holds all LiveData the UI observes; no game logic lives here.
 */
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

    // Timer
    public final MutableLiveData<Long> timerMillisLeft = new MutableLiveData<>(0L);
    private CountDownTimer countDownTimer;

    // Skocko puzzle data (loaded once per round, lives locally on this device)
    public final MutableLiveData<List<String>> mySkockoSolution       = new MutableLiveData<>();
    public final MutableLiveData<List<String>> opponentSkockoSolution = new MutableLiveData<>();

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

    public void listen(String gameId) {
        repository.listenToGameRoom(gameId,
                room -> {
                    gameRoom.postValue(room);
                    playerOne.postValue(room.getPlayerOne());
                    playerTwo.postValue(room.getPlayerTwo());
                    playlist.postValue(room.getMinigamePlaylist());
                },
                e -> errorMessage.postValue("Sync error: " + e.getMessage())
        );
    }

    // =========================================================================
    // SKOCKO DATA
    // =========================================================================

    /**
     * Loads puzzle solutions from Firestore. Each device loads both solutions
     * but shows only their own during gameplay (opponent's shown during bonus).
     */
    public void loadSkockoData(String docId, boolean isPlayerOne) {
        repository.fetchSkockoData(docId,
                snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        errorMessage.postValue("Skocko puzzle not found");
                        return;
                    }
                    List<String> p1Sol = (List<String>) snapshot.get("p1Solution");
                    List<String> p2Sol = (List<String>) snapshot.get("p2Solution");

                    if (isPlayerOne) {
                        mySkockoSolution.postValue(p1Sol);
                        opponentSkockoSolution.postValue(p2Sol);
                    } else {
                        mySkockoSolution.postValue(p2Sol);
                        opponentSkockoSolution.postValue(p1Sol);
                    }
                },
                e -> errorMessage.postValue("Failed to load puzzle: " + e.getMessage())
        );
    }

    // =========================================================================
    // TURN MANAGEMENT
    // =========================================================================

    public void finishMainTurn(String gameId, String myId, int score,
                               List<List<String>> attempts, boolean solved) {
        gameService.finishMainTurn(gameId, myId, score, attempts, solved,
                unused -> { /* Firestore listener handles UI update */ },
                e -> errorMessage.postValue("Failed to save turn: " + e.getMessage())
        );
    }

    public void finishBonusTurn(String gameId, String myId,
                                int bonusScore, boolean bonusSolved) {
        gameService.finishBonusTurn(gameId, myId, bonusScore, bonusSolved,
                unused -> { /* Firestore listener handles UI update */ },
                e -> errorMessage.postValue("Failed to save bonus: " + e.getMessage())
        );
    }

    public void advanceRound(String gameId) {
        gameService.advanceRound(gameId,
                unused -> { /* Firestore listener handles UI update */ },
                e -> errorMessage.postValue("Failed to advance: " + e.getMessage())
        );
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Override
    public void onCleared() {
        super.onCleared();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        gameService.detachListeners();
    }
}