package com.example.myapplication.service;

import com.example.myapplication.data.repository.GameRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameService {

    private final GameRepository repository;

    public GameService(GameRepository repository) {
        this.repository = repository;
    }
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
    // SKOCKO
    // =========================================================================

    public void fetchSkockoSolution(String docId, String solutionField,
                                    OnSuccessListener<List<String>> onSuccess,
                                    OnFailureListener onFailure) {
        repository.fetchSkockoSolution(docId, solutionField, onSuccess, onFailure);
    }

    public void updateGameRoom(String gameId, Map<String, Object> updates,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        repository.updateGameRoom(gameId, updates, onSuccess, onFailure);
    }

    public void deleteGameRoom(String gameId,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        repository.deleteGameRoom(gameId, onSuccess, onFailure);
    }

    /**
     * Pure business logic: compares a 4-symbol guess against the solution.
     * Returns a list of 4 results in position order: "CORRECT", "PRESENT", "ABSENT".
     * CORRECT  = right symbol, right position
     * PRESENT  = right symbol, wrong position
     * ABSENT   = symbol not in solution
     */
    public List<String> calculateFeedback(List<String> guess, List<String> solution) {
        String[] result = new String[4];
        List<String> solutionPool = new ArrayList<>(solution);

        // First pass — mark correct positions
        for (int i = 0; i < 4; i++) {
            if (guess.get(i).equals(solution.get(i))) {
                result[i] = "CORRECT";
                solutionPool.set(i, null);
            }
        }

        // Second pass — mark present/absent
        for (int i = 0; i < 4; i++) {
            if (result[i] != null) continue;
            int poolIdx = solutionPool.indexOf(guess.get(i));
            if (poolIdx != -1) {
                result[i] = "PRESENT";
                solutionPool.set(poolIdx, null);
            } else {
                result[i] = "ABSENT";
            }
        }

        List<String> feedback = new ArrayList<>();
        for (String r : result) feedback.add(r);
        return feedback;
    }
}