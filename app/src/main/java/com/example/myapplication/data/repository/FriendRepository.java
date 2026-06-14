package com.example.myapplication.data.repository;

import com.example.myapplication.data.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/** Firestore pristup za prijatelje: pretraga, dodavanje i ucitavanje liste. */
public class FriendRepository {

    private static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore db;

    public FriendRepository() {
        db = FirebaseFirestore.getInstance();
    }

    /** Pretraga korisnika po korisnickom imenu (prefiks pretraga, case-sensitive). */
    public void searchByUsername(String query,
                                 OnSuccessListener<List<User>> onSuccess,
                                 OnFailureListener onFailure) {
        if (query == null || query.trim().isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }
        String q = query.trim();
        // Gornja granica prefiksa: najveci moguci char garantuje da uhvatimo sve
        // korisnike cije ime pocinje sa q.
        String qEnd = q + Character.MAX_VALUE;
        db.collection(COLLECTION_USERS)
                .orderBy("username")
                .startAt(q)
                .endAt(qEnd)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> users = new ArrayList<>();
                    snapshot.getDocuments().forEach(doc -> {
                        User u = doc.toObject(User.class);
                        if (u != null) users.add(u);
                    });
                    onSuccess.onSuccess(users);
                })
                .addOnFailureListener(onFailure);
    }

    /** Dodaje prijatelja obostrano (oba korisnika dobijaju onog drugog u listu). */
    public void addFriendMutual(String myUid, String friendUid,
                                OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (myUid == null || friendUid == null || myUid.equals(friendUid)) {
            onFailure.onFailure(new Exception("Neispravan zahtev za dodavanje prijatelja"));
            return;
        }
        db.collection(COLLECTION_USERS).document(myUid)
                .update("friends", FieldValue.arrayUnion(friendUid))
                .addOnSuccessListener(unused ->
                        db.collection(COLLECTION_USERS).document(friendUid)
                                .update("friends", FieldValue.arrayUnion(myUid))
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(onFailure))
                .addOnFailureListener(onFailure);
    }

    /** Ucitava korisnike za date uid-eve (lista prijatelja). */
    public void getUsers(List<String> uids,
                         OnSuccessListener<List<User>> onSuccess,
                         OnFailureListener onFailure) {
        if (uids == null || uids.isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }
        List<User> result = new ArrayList<>();
        final int[] remaining = {uids.size()};
        for (String uid : uids) {
            db.collection(COLLECTION_USERS).document(uid)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            User u = snapshot.toObject(User.class);
                            if (u != null) {
                                synchronized (result) { result.add(u); }
                            }
                        }
                        if (--remaining[0] == 0) onSuccess.onSuccess(result);
                    })
                    .addOnFailureListener(e -> {
                        if (--remaining[0] == 0) onSuccess.onSuccess(result);
                    });
        }
    }

    /** Ucitava tekuceg korisnika (radi sveze liste prijatelja). */
    public void getUser(String uid,
                        OnSuccessListener<User> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS).document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) onSuccess.onSuccess(snapshot.toObject(User.class));
                    else onFailure.onFailure(new Exception("Korisnik nije pronadjen"));
                })
                .addOnFailureListener(onFailure);
    }
}
