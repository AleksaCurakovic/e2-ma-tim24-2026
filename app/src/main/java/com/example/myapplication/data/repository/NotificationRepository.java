package com.example.myapplication.data.repository;

import androidx.annotation.NonNull;

import com.example.myapplication.data.model.AppNotification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Istorija sistemskih notifikacija: users/{uid}/notifications/{id}.
 * Podкolekcija (umesto top-level kolekcije) izbegava potrebu za kompozitnim indeksom
 * pri sortiranju po vremenu.
 */
public class NotificationRepository {

    private final FirebaseFirestore db;
    private ListenerRegistration listener;

    public NotificationRepository() {
        db = FirebaseFirestore.getInstance();
    }

    private CollectionReference notifications(String uid) {
        return db.collection("users").document(uid).collection("notifications");
    }

    /** Upisuje (ili prepisuje) notifikaciju. Deterministički id čini upis idempotentnim. */
    public void add(@NonNull String uid, @NonNull AppNotification n) {
        if (n.getId() == null) return;
        notifications(uid).document(n.getId()).set(n);
    }

    /** Sluša listu notifikacija sortiranu po vremenu (najnovije prvo). */
    public void listen(String uid,
                       OnSuccessListener<List<AppNotification>> onUpdate,
                       OnFailureListener onFailure) {
        if (listener != null) listener.remove();
        listener = notifications(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { if (onFailure != null) onFailure.onFailure(error); return; }
                    if (snapshot == null) return;
                    List<AppNotification> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        AppNotification n = doc.toObject(AppNotification.class);
                        n.setId(doc.getId());
                        list.add(n);
                    }
                    onUpdate.onSuccess(list);
                });
    }

    public void markRead(String uid, String id) {
        if (uid == null || id == null) return;
        notifications(uid).document(id).update("read", true);
    }

    public void markRead(String uid, String id,
                         OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (uid == null || id == null) { if (onFailure != null) onFailure.onFailure(new Exception("null")); return; }
        notifications(uid).document(id).update("read", true)
                .addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
    }

    public void detach() {
        if (listener != null) { listener.remove(); listener = null; }
    }
}
