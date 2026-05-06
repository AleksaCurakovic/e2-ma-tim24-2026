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

    // =========================================================================
    // MOJ BROJ
    // =========================================================================

    public int generateTarget() {
        return new java.util.Random().nextInt(900) + 100;
    }

    public List<Integer> generateNumbers() {
        List<Integer> nums = new ArrayList<>();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 4; i++) nums.add(rnd.nextInt(9) + 1);
        int[] tens = {10, 15, 20};
        nums.add(tens[rnd.nextInt(tens.length)]);
        int[] hundreds = {25, 50, 75, 100};
        nums.add(hundreds[rnd.nextInt(hundreds.length)]);
        return nums;
    }

    public void publishMojBrojTarget(String gameId, int target,
                                     OnSuccessListener<Void> onSuccess,
                                     OnFailureListener onFailure) {
        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("mojBrojTarget", target);
        repository.updateGameRoom(gameId, updates, onSuccess, onFailure);
    }

    public void publishMojBrojNumbers(String gameId, List<Integer> numbers,
                                      OnSuccessListener<Void> onSuccess,
                                      OnFailureListener onFailure) {
        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("mojBrojNumbers", new ArrayList<>(numbers));
        repository.updateGameRoom(gameId, updates, onSuccess, onFailure);
    }

    public void submitMojBrojResult(String gameId, boolean isPlayerOne, int result,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        Map<String, Object> updates = new java.util.HashMap<>();
        if (isPlayerOne) {
            updates.put("mojBrojP1Result", result);
            updates.put("mojBrojP1Submitted", true);
        } else {
            updates.put("mojBrojP2Result", result);
            updates.put("mojBrojP2Submitted", true);
        }
        repository.updateGameRoom(gameId, updates, onSuccess, onFailure);
    }

    public Map<String, Object> scoreMojBrojRound(int target, int p1Result, int p2Result,
                                                  boolean p1IsRoundOwner,
                                                  int currentP1Score, int currentP2Score) {
        int p1Score = 0, p2Score = 0;
        boolean p1Exact = (p1Result == target);
        boolean p2Exact = (p2Result == target);

        if (p1Exact && p2Exact) {
            p1Score = 10; p2Score = 10;
        } else if (p1Exact) {
            p1Score = 10;
        } else if (p2Exact) {
            p2Score = 10;
        } else {
            int p1Diff = p1Result == 0 ? Integer.MAX_VALUE : Math.abs(target - p1Result);
            int p2Diff = p2Result == 0 ? Integer.MAX_VALUE : Math.abs(target - p2Result);
            if (p1Result != 0 && p2Result != 0 && p1Result == p2Result) {
                if (p1IsRoundOwner) p1Score = 5; else p2Score = 5;
            } else if (p1Diff < p2Diff) {
                p1Score = 5;
            } else if (p2Diff < p1Diff) {
                p2Score = 5;
            }
        }

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("playerOneScore",      currentP1Score + p1Score);
        updates.put("playerTwoScore",      currentP2Score + p2Score);
        updates.put("playerOneRoundScore", p1Score);
        updates.put("playerTwoRoundScore", p2Score);
        updates.put("mojBrojTarget",       null);
        updates.put("mojBrojNumbers",      null);
        updates.put("mojBrojP1Result",     0);
        updates.put("mojBrojP2Result",     0);
        updates.put("mojBrojP1Submitted",  false);
        updates.put("mojBrojP2Submitted",  false);
        // After P1's round → start P2's round; after P2's round → minigame is fully done
        updates.put("roundPhase", p1IsRoundOwner ? "P2_TURN" : "MINIGAME_DONE");
        return updates;
    }

    // =========================================================================
    // KORAK PO KORAK
    // =========================================================================

    public void fetchKorakSolution(String docId, String playerPrefix,
                                   OnSuccessListener<Map<String, Object>> onSuccess,
                                   OnFailureListener onFailure) {
        repository.fetchKorakSolution(docId, playerPrefix, onSuccess, onFailure);
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
    
    // =========================================================================
    // STARS / REWARDS
    // =========================================================================

    /**
     * Winner:  +10 + floor(score/40)
     * Loser:   -(10 - floor(score/40))  i.e. net = bonusStars - 10, min delta = -10
     */
    public int computeStarsDelta(int myScore, boolean iWon) {
        int bonus = myScore / 40;
        if (iWon) {
            return 10 + bonus;
        } else {
            return bonus - 10;
        }
    }

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