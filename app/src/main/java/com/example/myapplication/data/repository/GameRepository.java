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
import com.google.firebase.firestore.Source;

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
    private static final long REQUEST_TTL_MS = 2 * 60 * 1000L;
    private final FirebaseFirestore db;

    private ListenerRegistration gameRequestListener;
    private ListenerRegistration opponentRequestListener;
    private ListenerRegistration gameRoomListener;

    private ListenerRegistration roundStateListener;

    public GameRepository() {
        db = FirebaseFirestore.getInstance();
    }


    public void findOrPostRequest(String username,
                                  OnSuccessListener<PathResult> onSuccess,
                                  OnFailureListener onFailure) {

        // We still force Source.SERVER to guarantee we do not read a stale local cache.
        db.collection(COL_REQUESTS)
                .whereEqualTo("accepted", false)
                .get(Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    db.runTransaction(transaction -> {
                        List<DocumentSnapshot> available = new ArrayList<>();

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            DocumentSnapshot fresh = transaction.get(doc.getReference());
                            Boolean accepted = fresh.getBoolean("accepted");
                            String creator   = fresh.getString("creatorName");

                            // Removed all time-checking logic.
                            // Now we only check if it is unaccepted and not created by ourselves.
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
                                    chosen.getString("creatorName"),
                                    chosen.getLong("createdAt") != null ? chosen.getLong("createdAt") : 0
                            );
                        } else {
                            DocumentReference ref = db.collection(COL_REQUESTS).document();
                            long createdAtNew = System.currentTimeMillis(); // Kept just for logging/history
                            GameRequest req = new GameRequest(
                                    username,
                                    createdAtNew,
                                    UUID.randomUUID().toString()
                            );
                            transaction.set(ref, req);

                            return new PathResult(
                                    PathResult.Type.WAITING,
                                    req.getGameId(),
                                    ref.getId(),
                                    null,
                                    createdAtNew
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

    public void listenForOlderRequest(String ownDocumentId,
                                      long ownCreatedAt,
                                      String username,
                                      OnSuccessListener<PathResult> onJoined,
                                      OnFailureListener onFailure) {
        opponentRequestListener = db.collection(COL_REQUESTS)
                .whereEqualTo("accepted", false)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { onFailure.onFailure(error); return; }
                    if (snapshot == null || snapshot.isEmpty()) return;

                    DocumentSnapshot chosen = null;
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (doc.getId().equals(ownDocumentId)) continue;
                        String creator = doc.getString("creatorName");
                        Long createdAt = doc.getLong("createdAt");
                        if (username.equals(creator) || createdAt == null) continue;
                        if ((now - createdAt) > REQUEST_TTL_MS) continue;
                        boolean older = createdAt < ownCreatedAt ||
                                (createdAt == ownCreatedAt && doc.getId().compareTo(ownDocumentId) < 0);
                        if (!older) continue;
                        if (chosen == null || createdAt < chosen.getLong("createdAt")) {
                            chosen = doc;
                        }
                    }

                    if (chosen == null) return;
                    DocumentReference chosenRef = chosen.getReference();
                    DocumentReference ownRef = db.collection(COL_REQUESTS).document(ownDocumentId);
                    db.runTransaction(transaction -> {
                        DocumentSnapshot fresh = transaction.get(chosenRef);
                        Boolean accepted = fresh.getBoolean("accepted");
                        String creator = fresh.getString("creatorName");
                        if (!Boolean.FALSE.equals(accepted) || username.equals(creator)) {
                            return null;
                        }

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("accepted", true);
                        updates.put("joinerName", username);
                        transaction.update(chosenRef, updates);
                        transaction.delete(ownRef);
                        return new PathResult(
                                PathResult.Type.JOINED,
                                fresh.getString("gameId"),
                                fresh.getId(),
                                creator,
                                fresh.getLong("createdAt") != null ? fresh.getLong("createdAt") : 0
                        );
                    }).addOnSuccessListener(result -> {
                        if (result != null) onJoined.onSuccess(result);
                    }).addOnFailureListener(onFailure);
                });
    }

    public void listenToGameRoom(String gameId,
                                 OnSuccessListener<GameRoom> onUpdate,
                                 OnFailureListener onFailure) {
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


    public void fetchMinigameIds(OnSuccessListener<List<String>> onSuccess,
                                 OnFailureListener onFailure) {
        List<String> playlist = new ArrayList<>();  //biranje docid
        playlist.add("koZnaZna:MYpnDssGiHQRGFm44Hnl");
        playlist.add("spojnice:ggkOP2ztg66gPMsvnr2a");
        playlist.add("asocijacije:RGl9pFhUbmeoF2TYm1UW");
        playlist.add("skocko:4Nu4vTBXWEI4BTKSDpH2");
        playlist.add("korakPoKorak:JikvyEnu1KOMN6ySvCWV");
        playlist.add("mojBroj:local");
        onSuccess.onSuccess(playlist);
    }

    public void fetchSpojniceData(String docId,
                                  com.google.android.gms.tasks.OnSuccessListener<Map<String, Object>> onSuccess,
                                  com.google.android.gms.tasks.OnFailureListener onFailure) {
        db.collection("minigames").document("spojnice")
                .collection("items").document(docId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.exists() && snap.getData() != null) {
                        onSuccess.onSuccess(snap.getData());
                    } else {
                        onFailure.onFailure(new Exception("Spojnice podaci nisu pronađeni za ID: " + docId));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void createGameRoom(String gameId, String playerOne, String playerTwo,
                               List<String> playlist,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        GameRoom room = new GameRoom(gameId, playerOne, playerTwo, playlist);

        if (playlist != null && !playlist.isEmpty()) {
            String first = playlist.get(0);
            room.setCurrentMinigameType(first.contains(":") ? first.split(":")[0] : first);

        }

        db.collection(COL_ROOMS).document(gameId)
                .set(room)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void fetchSkockoSolution(String docId, String solutionField,
                                    OnSuccessListener<List<String>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("minigames")
                .document("skocko")
                .collection("items")
                .document(docId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        List<String> solution = (List<String>) snapshot.get(solutionField);
                        if (solution != null && solution.size() == 4) {
                            onSuccess.onSuccess(solution);
                        } else {
                            onFailure.onFailure(new Exception("Nepravilan format resenja" + solutionField));
                        }
                    } else {
                        onFailure.onFailure(new Exception("Rešenje nije pronađeno"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void fetchKoZnaZnaData(String docId,
                              OnSuccessListener<Map<String, Object>> onSuccess,
                              OnFailureListener onFailure) {
        db.collection("minigames").document("koZnaZna")
                .collection("items").document(docId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.exists() && snap.getData() != null) {
                        onSuccess.onSuccess(snap.getData());
                    } else {
                        onFailure.onFailure(new Exception("Ko zna zna podaci nisu pronađeni za ID: " + docId));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void fetchKorakSolution(String docId, String playerPrefix,
                                   OnSuccessListener<Map<String, Object>> onSuccess,
                                   OnFailureListener onFailure) {
        db.collection("minigames")
                .document("korakPoKorak")
                .collection("items")
                .document(docId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String answer = snapshot.getString(playerPrefix + "Answer");
                        List<String> steps = (List<String>) snapshot.get(playerPrefix + "Steps");
                        if (answer != null && steps != null && steps.size() == 7) {
                            Map<String, Object> data = new HashMap<>();
                            data.put("answer", answer);
                            data.put("steps", steps);
                            onSuccess.onSuccess(data);
                        } else {
                            onFailure.onFailure(new Exception("Neis" + playerPrefix));
                        }
                    } else {
                        onFailure.onFailure(new Exception("Korak document nije nadjen"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void updateGameRoom(String gameId, Map<String, Object> updates,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        db.collection(COL_ROOMS).document(gameId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void deleteGameRoom(String gameId,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        db.collection(COL_ROOMS).document(gameId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }



    public void deleteGameRequest(String documentId,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        db.collection(COL_REQUESTS).document(documentId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void detachMatchmakingListeners() {
        if (gameRequestListener != null) {
            gameRequestListener.remove();
            gameRequestListener = null;
        }
        if (opponentRequestListener != null) {
            opponentRequestListener.remove();
            opponentRequestListener = null;
        }
    }
    public void detachListeners() {
        if (gameRequestListener != null) { gameRequestListener.remove(); gameRequestListener = null; }
        if (opponentRequestListener != null) { opponentRequestListener.remove(); opponentRequestListener = null; }
        if (gameRoomListener    != null) { gameRoomListener.remove();    gameRoomListener    = null; }
        if (roundStateListener  != null) { roundStateListener.remove();  roundStateListener  = null; }
    }

    public void fetchAssociationQuestion(
            String questionId,
            OnSuccessListener<DocumentSnapshot> onSuccess,
            OnFailureListener onFailure) {

        db.collection("minigames")
                .document("asocijacije")
                .collection("items")
                .document(questionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        onFailure.onFailure(
                                new Exception("Pitanje nije pronađeno: " + questionId));
                        return;
                    }
                    onSuccess.onSuccess(snapshot);
                })
                .addOnFailureListener(onFailure);
    }


    public static class PathResult {
        public enum Type { JOINED, WAITING }

        public final Type   type;
        public final String gameId;
        public final String requestDocId;
        public final String otherPlayerName;
        public final long createdAt;

        public PathResult(Type type, String gameId, String requestDocId, String otherPlayerName, long createdAt) {
            this.type            = type;
            this.gameId          = gameId;
            this.requestDocId    = requestDocId;
            this.otherPlayerName = otherPlayerName;
            this.createdAt       = createdAt;
        }
    }
}
