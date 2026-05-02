package com.example.myapplication.data.repository;

import com.example.myapplication.data.model.GameRequest;
import com.example.myapplication.data.model.GameRoom;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class GameRepository {

    private static final String COL_REQUESTS = "gameRequests";
    private static final String COL_ROOMS    = "gameRooms";
    private final FirebaseFirestore db;

    // Matchmaking listeners
    private ListenerRegistration gameRequestListener;
    private ListenerRegistration gameRoomListener;

    // In-game listener
    private ListenerRegistration roundStateListener;

    public GameRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // =========================================================================
    // MATCHMAKING
    // =========================================================================

    public void findOrPostRequest(String username,
                                  OnSuccessListener<PathResult> onSuccess,
                                  OnFailureListener onFailure) {
        db.collection(COL_REQUESTS)
                .whereEqualTo("accepted", false)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    db.runTransaction(transaction -> {
                        List<DocumentSnapshot> available = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            DocumentSnapshot fresh = transaction.get(doc.getReference());
                            Boolean accepted = fresh.getBoolean("accepted");
                            String creator   = fresh.getString("creatorName");
                            if (Boolean.FALSE.equals(accepted) && !username.equals(creator)) {
                                available.add(fresh);
                            }
                        }

                        if (!available.isEmpty()) {
                            Collections.shuffle(available);
                            DocumentSnapshot chosen = available.get(0);
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("accepted", true);
                            updates.put("joinerName", username);
                            transaction.update(chosen.getReference(), updates);
                            return new PathResult(
                                    PathResult.Type.JOINED,
                                    chosen.getString("gameId"),
                                    chosen.getId(),
                                    chosen.getString("creatorName")
                            );
                        } else {
                            DocumentReference ref = db.collection(COL_REQUESTS).document();
                            GameRequest req = new GameRequest(
                                    username,
                                    System.currentTimeMillis(),
                                    UUID.randomUUID().toString()
                            );
                            transaction.set(ref, req);
                            return new PathResult(
                                    PathResult.Type.WAITING,
                                    req.getGameId(),
                                    ref.getId(),
                                    null
                            );
                        }
                    }).addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
                }).addOnFailureListener(onFailure);
    }

    public void listenToOwnRequest(String documentId,
                                   OnSuccessListener<GameRequest> onAccepted,
                                   OnFailureListener onFailure) {
        gameRequestListener = db.collection(COL_REQUESTS).document(documentId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { onFailure.onFailure(error); return; }
                    if (snapshot != null && snapshot.exists()) {
                        GameRequest req = snapshot.toObject(GameRequest.class);
                        if (req != null && req.isAccepted()) {
                            onAccepted.onSuccess(req);
                        }
                    }
                });
    }

    public void listenToGameRoom(String gameId,
                                 OnSuccessListener<GameRoom> onUpdate,
                                 OnFailureListener onFailure) {
        // Always clean up previous listener first to avoid duplicates
        if (gameRoomListener != null) {
            gameRoomListener.remove();
        }
        gameRoomListener = db.collection(COL_ROOMS).document(gameId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { onFailure.onFailure(error); return; }
                    if (snapshot != null && snapshot.exists()) {
                        GameRoom room = snapshot.toObject(GameRoom.class);
                        if (room != null) {
                            onUpdate.onSuccess(room);
                        }
                    }
                });
    }

    // =========================================================================
    // MINIGAME DATA
    // =========================================================================

    public void fetchMinigameIds(OnSuccessListener<List<String>> onSuccess,
                                 OnFailureListener onFailure) {
        String[] categories = { "skocko", "memory", "quiz", "reaction", "math", "speed" };

        List<String> result = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);

        for (String category : categories) {
            db.collection("minigames")
                    .document(category)
                    .collection("items")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<DocumentSnapshot> docs = snapshot.getDocuments();
                        if (!docs.isEmpty()) {
                            Collections.shuffle(docs);
                            // Store as "category:docId" so GameFragment knows the type
                            result.add(category + ":" + docs.get(0).getId());
                        }
                        if (counter.incrementAndGet() == categories.length) {
                            onSuccess.onSuccess(result);
                        }
                    })
                    .addOnFailureListener(onFailure);
        }
    }

    /**
     * Fetches Skocko puzzle data for a given docId.
     * Returns a map with "p1Solution" and "p2Solution" as List<String>.
     */
    public void fetchSkockoData(String docId,
                                OnSuccessListener<DocumentSnapshot> onSuccess,
                                OnFailureListener onFailure) {
        db.collection("minigames")
                .document("skocko")
                .collection("items")
                .document(docId)
                .get()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // =========================================================================
    // GAME ROOM CREATION
    // =========================================================================

    public void createGameRoom(String gameId, String playerOne, String playerTwo,
                               List<String> playlist,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        GameRoom room = new GameRoom(gameId, playerOne, playerTwo, playlist);

        // Set the first minigame type from playlist (format: "type:docId")
        if (playlist != null && !playlist.isEmpty()) {
            String first = playlist.get(0);
            room.setCurrentMinigameType(first.contains(":") ? first.split(":")[0] : first);
            // Store skockoDocId if first minigame is skocko
            if (first.startsWith("skocko:")) {
                room.setSkockoDocId(first.split(":")[1]);
            }
        }

        db.collection(COL_ROOMS).document(gameId)
                .set(room)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // =========================================================================
    // TURN / ROUND MANAGEMENT
    // =========================================================================

    /**
     * Called when P1 or P2 finishes their main turn.
     * Saves attempts + solved status, then transitions phase:
     *   P1_TURN -> P2_BONUS (if P1 failed) or P2_TURN (if P1 solved)
     *   P2_TURN -> P1_BONUS (if P2 failed) or SHOWING_RESULTS (if P2 solved)
     */
    public Task<Void> finishMainTurn(String gameId, String myUserId, int score,
                                     List<List<String>> attempts, boolean solved) {
        DocumentReference ref = db.collection(COL_ROOMS).document(gameId);

        return db.runTransaction(transaction -> {
            GameRoom room = transaction.get(ref).toObject(GameRoom.class);
            if (room == null) throw new RuntimeException("Room not found");

            String phase = room.getRoundPhase();

            if (phase.equals("P1_TURN")) {
                if (!myUserId.equals(room.getPlayerOne()))
                    throw new RuntimeException("Not your turn");

                room.setP1Attempts(attempts);
                room.setP1Solved(solved);
                room.setPlayerOneRoundScore(score);
                room.setPlayerOneScore(room.getPlayerOneScore() + score);

                // If P1 failed, P2 gets a bonus attempt; otherwise skip to P2's turn
                room.setRoundPhase(solved ? "P2_TURN" : "P2_BONUS");

            } else if (phase.equals("P2_TURN")) {
                if (!myUserId.equals(room.getPlayerTwo()))
                    throw new RuntimeException("Not your turn");

                room.setP2Attempts(attempts);
                room.setP2Solved(solved);
                room.setPlayerTwoRoundScore(score);
                room.setPlayerTwoScore(room.getPlayerTwoScore() + score);

                // If P2 failed, P1 gets a bonus attempt; otherwise go to results
                room.setRoundPhase(solved ? "SHOWING_RESULTS" : "P1_BONUS");

            } else {
                throw new RuntimeException("Not a main turn phase: " + phase);
            }

            transaction.set(ref, room);
            return null;
        });
    }

    /**
     * Called when P1 or P2 submits their bonus attempt (one guess, 10s).
     * P2_BONUS: P2 tries to guess P1's combination
     * P1_BONUS: P1 tries to guess P2's combination
     * After bonus, always goes to the next logical phase.
     */
    public Task<Void> finishBonusTurn(String gameId, String myUserId,
                                      int bonusScore, boolean bonusSolved) {
        DocumentReference ref = db.collection(COL_ROOMS).document(gameId);

        return db.runTransaction(transaction -> {
            GameRoom room = transaction.get(ref).toObject(GameRoom.class);
            if (room == null) throw new RuntimeException("Room not found");

            String phase = room.getRoundPhase();

            if (phase.equals("P2_BONUS")) {
                // P2 is attempting to guess P1's combination
                if (!myUserId.equals(room.getPlayerTwo()))
                    throw new RuntimeException("Not your bonus turn");

                if (bonusSolved) {
                    room.setPlayerTwoRoundScore(room.getPlayerTwoRoundScore() + bonusScore);
                    room.setPlayerTwoScore(room.getPlayerTwoScore() + bonusScore);
                }
                // After P2 bonus, it's P2's main turn
                room.setRoundPhase("P2_TURN");

            } else if (phase.equals("P1_BONUS")) {
                // P1 is attempting to guess P2's combination
                if (!myUserId.equals(room.getPlayerOne()))
                    throw new RuntimeException("Not your bonus turn");

                if (bonusSolved) {
                    room.setPlayerOneRoundScore(room.getPlayerOneRoundScore() + bonusScore);
                    room.setPlayerOneScore(room.getPlayerOneScore() + bonusScore);
                }
                // After P1 bonus, show results
                room.setRoundPhase("SHOWING_RESULTS");

            } else {
                throw new RuntimeException("Not a bonus phase: " + phase);
            }

            transaction.set(ref, room);
            return null;
        });
    }

    public Task<Void> advanceRound(String gameId) {
        DocumentReference ref = db.collection(COL_ROOMS).document(gameId);

        return db.runTransaction(transaction -> {
            GameRoom room = transaction.get(ref).toObject(GameRoom.class);
            if (room == null) throw new RuntimeException("Room not found");

            if (!"SHOWING_RESULTS".equals(room.getRoundPhase())) return null;

            int nextIndex = room.getCurrentMinigameIndex() + 1;

            if (nextIndex >= room.getMinigamePlaylist().size()) {
                room.setGameState("FINISHED");
            } else {
                int nextRound = room.getRoundNumber() + 1;
                room.setRoundNumber(nextRound);
                room.setCurrentMinigameIndex(nextIndex);
                room.setPlayerOneRoundScore(0);
                room.setPlayerTwoRoundScore(0);
                room.setP1Attempts(new ArrayList<>());
                room.setP2Attempts(new ArrayList<>());
                room.setP1Solved(false);
                room.setP2Solved(false);

                // Update minigame type for next round
                String next = room.getMinigamePlaylist().get(nextIndex);
                room.setCurrentMinigameType(next.contains(":") ? next.split(":")[0] : next);
                if (next.startsWith("skocko:")) {
                    room.setSkockoDocId(next.split(":")[1]);
                }

                room.setRoundPhase(nextRound % 2 == 0 ? "P1_TURN" : "P2_TURN");
            }

            transaction.set(ref, room);
            return null;
        });
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    public void deleteGameRequest(String documentId,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        db.collection(COL_REQUESTS).document(documentId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Only detach matchmaking listeners — keeps the game room listener alive */
    public void detachMatchmakingListeners() {
        if (gameRequestListener != null) {
            gameRequestListener.remove();
            gameRequestListener = null;
        }
    }

    /** Detach everything — call only from ViewModel.onCleared() */
    public void detachListeners() {
        if (gameRequestListener != null) { gameRequestListener.remove(); gameRequestListener = null; }
        if (gameRoomListener    != null) { gameRoomListener.remove();    gameRoomListener    = null; }
        if (roundStateListener  != null) { roundStateListener.remove();  roundStateListener  = null; }
    }

    // =========================================================================
    // PATH RESULT
    // =========================================================================

    public static class PathResult {
        public enum Type { JOINED, WAITING }

        public final Type   type;
        public final String gameId;
        public final String requestDocId;
        public final String otherPlayerName;

        public PathResult(Type type, String gameId, String requestDocId, String otherPlayerName) {
            this.type            = type;
            this.gameId          = gameId;
            this.requestDocId    = requestDocId;
            this.otherPlayerName = otherPlayerName;
        }
    }
}