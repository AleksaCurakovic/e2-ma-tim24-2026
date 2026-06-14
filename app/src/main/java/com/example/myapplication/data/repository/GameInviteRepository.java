package com.example.myapplication.data.repository;

import com.example.myapplication.data.model.GameInvite;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

/**
 * Firestore pristup pozivnicama za partiju (kolekcija "gameInvites").
 * Pokriva i stranu pošiljaoca i stranu primaoca.
 */
public class GameInviteRepository {

    private static final String COLLECTION = "gameInvites";

    private final FirebaseFirestore db;

    private ListenerRegistration incomingListener;
    private ListenerRegistration sentInviteListener;

    public GameInviteRepository() {
        db = FirebaseFirestore.getInstance();
    }

    /** Kreira pozivnicu i vraća njen id (id sobe je već u objektu). */
    public void createInvite(GameInvite invite,
                             OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        DocumentReference ref = db.collection(COLLECTION).document();
        invite.setId(ref.getId());
        ref.set(invite)
                .addOnSuccessListener(unused -> onSuccess.onSuccess(ref.getId()))
                .addOnFailureListener(onFailure);
    }

    /** Sluša dolazne pozivnice upućene meni (primalac). Vraća najnoviju "pending" pozivnicu. */
    public void listenIncoming(String myUid,
                               OnSuccessListener<GameInvite> onInvite,
                               OnFailureListener onFailure) {
        detachIncoming();
        incomingListener = db.collection(COLLECTION)
                .whereEqualTo("toUid", myUid)
                .whereEqualTo("status", GameInvite.STATUS_PENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { onFailure.onFailure(error); return; }
                    if (snapshot == null || snapshot.isEmpty()) return;

                    GameInvite newest = null;
                    long now = System.currentTimeMillis();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        GameInvite inv = doc.toObject(GameInvite.class);
                        if (inv == null) continue;
                        // Ignoriši zastarele pozivnice (npr. iz prethodne sesije).
                        if (now - inv.getCreatedAt() > 15_000L) continue;
                        if (newest == null || inv.getCreatedAt() > newest.getCreatedAt()) {
                            newest = inv;
                        }
                    }
                    if (newest != null) onInvite.onSuccess(newest);
                });
    }

    /** Sluša promene statusa poslate pozivnice (pošiljalac). */
    public void listenToInvite(String inviteId,
                               OnSuccessListener<GameInvite> onUpdate,
                               OnFailureListener onFailure) {
        detachSent();
        sentInviteListener = db.collection(COLLECTION).document(inviteId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { onFailure.onFailure(error); return; }
                    if (snapshot != null && snapshot.exists()) {
                        GameInvite inv = snapshot.toObject(GameInvite.class);
                        if (inv != null) {
                            inv.setId(snapshot.getId());
                            onUpdate.onSuccess(inv);
                        }
                    }
                });
    }

    /** Jednokratno učitavanje pozivnice (za naknadno reagovanje iz istorije obaveštenja). */
    public void getInvite(String inviteId,
                          OnSuccessListener<GameInvite> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION).document(inviteId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        GameInvite inv = snapshot.toObject(GameInvite.class);
                        if (inv != null) inv.setId(snapshot.getId());
                        onSuccess.onSuccess(inv);
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void updateStatus(String inviteId, String status,
                             OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION).document(inviteId)
                .update("status", status)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void deleteInvite(String inviteId) {
        if (inviteId == null) return;
        db.collection(COLLECTION).document(inviteId).delete();
    }

    /** Postavlja status na "expired" samo ako je pozivnica još uvek "pending" (auto-odbijanje). */
    public void expireIfPending(String inviteId) {
        if (inviteId == null) return;
        db.collection(COLLECTION).document(inviteId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()
                            && GameInvite.STATUS_PENDING.equals(snapshot.getString("status"))) {
                        db.collection(COLLECTION).document(inviteId)
                                .update("status", GameInvite.STATUS_EXPIRED);
                    }
                });
    }

    public void detachIncoming() {
        if (incomingListener != null) { incomingListener.remove(); incomingListener = null; }
    }

    public void detachSent() {
        if (sentInviteListener != null) { sentInviteListener.remove(); sentInviteListener = null; }
    }
}
