package com.example.myapplication.service;

import com.example.myapplication.data.repository.GameRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

public class GameService {

    private final GameRepository repository;

    public GameService(GameRepository repository) {
        this.repository = repository;
    }

    // =========================================================================
    // MATCHMAKING
    // =========================================================================

    public void startMatchmaking(String username,
                                 OnSuccessListener<String> onNavigateToGame,
                                 OnFailureListener onFailure) {
        repository.findOrPostRequest(username, result -> {
            if (result.type == GameRepository.PathResult.Type.JOINED) {
                handleJoinerPath(result.gameId, onNavigateToGame, onFailure);
            } else {
                handleCreatorPath(result.requestDocId, username, onNavigateToGame, onFailure);
            }
        }, onFailure);
    }

    private void handleJoinerPath(String gameId,
                                  OnSuccessListener<String> onNavigateToGame,
                                  OnFailureListener onFailure) {
        repository.listenToGameRoom(gameId, room -> {
            if (room != null) {
                repository.detachMatchmakingListeners(); // keep game room listener alive
                onNavigateToGame.onSuccess(gameId);
            }
        }, onFailure);
    }

    private void handleCreatorPath(String requestDocId, String creatorName,
                                   OnSuccessListener<String> onNavigateToGame,
                                   OnFailureListener onFailure) {
        repository.listenToOwnRequest(requestDocId, acceptedRequest -> {
            repository.detachMatchmakingListeners(); // keep game room listener alive

            String gameId    = acceptedRequest.getGameId();
            String joinerName = acceptedRequest.getJoinerName();

            buildGameRoom(gameId, creatorName, joinerName, requestDocId, onNavigateToGame, onFailure);
        }, onFailure);
    }

    private void buildGameRoom(String gameId, String creatorName, String joinerName,
                               String requestDocId,
                               OnSuccessListener<String> onNavigateToGame,
                               OnFailureListener onFailure) {
        repository.fetchMinigameIds(allIds -> {
            repository.createGameRoom(gameId, creatorName, joinerName, allIds, unused -> {
                repository.deleteGameRequest(requestDocId, deleted -> {
                    onNavigateToGame.onSuccess(gameId);
                }, onFailure);
            }, onFailure);
        }, onFailure);
    }

    // =========================================================================
    // TURN MANAGEMENT
    // =========================================================================

    /**
     * Call when a player finishes their main turn (30s up or solved).
     * Passes attempts and solved status so the opponent can see them during bonus.
     */
    public void finishMainTurn(String gameId, String myId, int score,
                               List<List<String>> attempts, boolean solved,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        repository.finishMainTurn(gameId, myId, score, attempts, solved)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Call when a player finishes their bonus attempt (10s, one guess).
     */
    public void finishBonusTurn(String gameId, String myId,
                                int bonusScore, boolean bonusSolved,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        repository.finishBonusTurn(gameId, myId, bonusScore, bonusSolved)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void advanceRound(String gameId,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {
        repository.advanceRound(gameId)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void detachListeners() {
        repository.detachListeners();
    }
}