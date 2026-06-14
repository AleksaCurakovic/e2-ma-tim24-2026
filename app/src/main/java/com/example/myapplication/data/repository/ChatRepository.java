package com.example.myapplication.data.repository;

import com.example.myapplication.data.model.ChatMessage;
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
 * Regionalni čet u realnom vremenu.
 * Firestore: regionChats/{region}/messages/{id} (podkolekcija → bez kompozitnog indeksa).
 */
public class ChatRepository {

    private static final String COLLECTION = "regionChats";
    private static final String SUBCOLLECTION = "messages";
    private static final long MESSAGE_LIMIT = 200;

    private final FirebaseFirestore db;
    private ListenerRegistration listener;

    public ChatRepository() {
        db = FirebaseFirestore.getInstance();
    }

    private CollectionReference messages(String region) {
        return db.collection(COLLECTION).document(region).collection(SUBCOLLECTION);
    }

    public void send(String region, ChatMessage message,
                     OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (region == null) { if (onFailure != null) onFailure.onFailure(new Exception("Nepoznat region")); return; }
        messages(region).add(message)
                .addOnSuccessListener(ref -> { if (onSuccess != null) onSuccess.onSuccess(null); })
                .addOnFailureListener(onFailure != null ? onFailure : e -> {});
    }

    /** Sluša poruke regiona u realnom vremenu (najstarije → najnovije). */
    public void listen(String region,
                       OnSuccessListener<List<ChatMessage>> onUpdate,
                       OnFailureListener onFailure) {
        if (region == null) return;
        if (listener != null) listener.remove();
        listener = messages(region)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(MESSAGE_LIMIT)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) { if (onFailure != null) onFailure.onFailure(error); return; }
                    if (snapshot == null) return;
                    List<ChatMessage> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        ChatMessage m = doc.toObject(ChatMessage.class);
                        m.setId(doc.getId());
                        list.add(m);
                    }
                    onUpdate.onSuccess(list);
                });
    }

    public void detach() {
        if (listener != null) { listener.remove(); listener = null; }
    }
}
