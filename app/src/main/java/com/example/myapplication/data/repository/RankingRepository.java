package com.example.myapplication.data.repository;

import androidx.annotation.NonNull;

import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.util.CycleUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore pristup rang listama.
 *
 * Šema:
 *   rankings/{cycleId}/entries/{uid}
 *     - uid, username, leagueIcon, starsEarned, gamesPlayed, updatedAt
 */
public class RankingRepository {

    private static final String COLLECTION_RANKINGS = "rankings";
    private static final String SUBCOLLECTION_ENTRIES = "entries";
    private static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore db;

    public RankingRepository() {
        db = FirebaseFirestore.getInstance();
    }

    private DocumentReference entryRef(String cycleId, String uid) {
        return db.collection(COLLECTION_RANKINGS)
                .document(cycleId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(uid);
    }

    /**
     * Beleži rezultat odigrane partije u tekući nedeljni i mesečni ciklus.
     * Inkrementira broj odigranih partija (uvek) i osvojene zvezde (>= 0).
     */
    public void recordGameResult(String uid, String username, String leagueIcon, int starsWon,
                                 OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (uid == null) {
            if (onFailure != null) onFailure.onFailure(new Exception("Nedostaje uid"));
            return;
        }
        int stars = Math.max(0, starsWon);

        String weekly  = CycleUtil.getCurrentWeeklyCycleId();
        String monthly = CycleUtil.getCurrentMonthlyCycleId();

        writeEntry(weekly, uid, username, leagueIcon, stars,
                unused -> writeEntry(monthly, uid, username, leagueIcon, stars, onSuccess, onFailure),
                onFailure);
    }

    private void writeEntry(String cycleId, String uid, String username, String leagueIcon,
                            int stars, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("username", username);
        data.put("leagueIcon", leagueIcon);
        data.put("starsEarned", FieldValue.increment(stars));
        data.put("gamesPlayed", FieldValue.increment(1));
        data.put("updatedAt", System.currentTimeMillis());

        entryRef(cycleId, uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure != null ? onFailure : e -> {});
    }

    /**
     * Učitava rang listu za dati ciklus, sortiranu po osvojenim zvezdama opadajuće.
     * Rangirani su samo igrači koji su odigrali bar jednu partiju (gamesPlayed >= 1).
     */
    public void getRanking(String cycleId,
                           OnSuccessListener<List<RankingEntry>> onSuccess,
                           OnFailureListener onFailure) {
        db.collection(COLLECTION_RANKINGS)
                .document(cycleId)
                .collection(SUBCOLLECTION_ENTRIES)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RankingEntry> entries = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshot) {
                        RankingEntry e = doc.toObject(RankingEntry.class);
                        // Rangiran je samo igrač koji je odigrao bar jednu partiju.
                        if (e.getGamesPlayed() >= 1) entries.add(e);
                    }
                    // Sortiranje po zvezdama (pa po partijama) radimo lokalno
                    // da izbegnemo potrebu za kompozitnim indeksom.
                    entries.sort((a, b) -> {
                        if (b.getStarsEarned() != a.getStarsEarned())
                            return Integer.compare(b.getStarsEarned(), a.getStarsEarned());
                        return Integer.compare(b.getGamesPlayed(), a.getGamesPlayed());
                    });
                    for (int i = 0; i < entries.size(); i++) {
                        entries.get(i).setRank(i + 1);
                    }
                    onSuccess.onSuccess(entries);
                })
                .addOnFailureListener(onFailure);
    }

    /** Dodaje tokene korisniku i obeležava da je nagrada za ciklus dodeljena. */
    public void awardTokensAndMarkClaimed(@NonNull String uid, int tokens, @NonNull String claimField,
                                          @NonNull String cycleId,
                                          OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        if (tokens > 0) {
            updates.put("tokens", FieldValue.increment(tokens));
        }
        updates.put(claimField, cycleId);

        db.collection(COLLECTION_USERS).document(uid)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
