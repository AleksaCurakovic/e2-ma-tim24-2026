package com.example.myapplication.data.repository;

import androidx.annotation.NonNull;

import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.data.model.User;
import com.example.myapplication.util.CycleUtil;
import com.example.myapplication.util.LeagueUtil;
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

    /** Mesečna kazna: ako korisnik nije rangiran, gubi 30% ukupnih zvezda i liga se preračunava. */
    public void applyMonthlyNonPlacementPenaltyAndMarkClaimed(@NonNull String uid,
                                                              @NonNull String claimField,
                                                              @NonNull String cycleId,
                                                              OnSuccessListener<Void> onSuccess,
                                                              OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS).document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    long currentStars = snapshot.getLong("stars") != null ? snapshot.getLong("stars") : 0;
                    long newStars = Math.max(0, Math.round(Math.floor(currentStars * 0.70)));
                    int oldLeague = LeagueUtil.levelForStars(currentStars);
                    int newLeague = LeagueUtil.levelForStars(newStars);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("stars", newStars);
                    updates.put("leagueLevel", newLeague);
                    updates.put("leagueName", LeagueUtil.nameForLevel(newLeague));
                    updates.put("leagueIcon", LeagueUtil.iconForLevel(newLeague));
                    updates.put(claimField, cycleId);
                    if (oldLeague != newLeague) {
                        String direction = newLeague > oldLeague ? "Usao si u " : "Pao si u ";
                        updates.put("pendingLeagueChange", direction + LeagueUtil.nameForLevel(newLeague));
                    }

                    db.collection(COLLECTION_USERS).document(uid)
                            .update(updates)
                            .addOnSuccessListener(onSuccess)
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Na osnovu mesečne rang liste regiona postavlja okvir avatara korisnicima iz
     * prva tri regiona prethodnog ciklusa.
     */
    public void applyRegionAvatarFrames(@NonNull String monthlyCycleId,
                                        OnSuccessListener<Void> onSuccess,
                                        OnFailureListener onFailure) {
        getRanking(monthlyCycleId, ranking ->
                db.collection(COLLECTION_USERS).get()
                        .addOnSuccessListener(usersSnapshot -> {
                            Map<String, User> usersByUid = new HashMap<>();
                            Map<String, Integer> starsByRegion = new HashMap<>();
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : usersSnapshot) {
                                User user = doc.toObject(User.class);
                                if (user != null && user.getUid() != null) {
                                    usersByUid.put(user.getUid(), user);
                                }
                            }

                            for (RankingEntry entry : ranking) {
                                User user = usersByUid.get(entry.getUid());
                                if (user == null || user.getRegion() == null || user.getRegion().isEmpty()) continue;
                                int current = starsByRegion.containsKey(user.getRegion())
                                        ? starsByRegion.get(user.getRegion()) : 0;
                                starsByRegion.put(user.getRegion(), current + entry.getStarsEarned());
                            }

                            List<Map.Entry<String, Integer>> regions = new ArrayList<>(starsByRegion.entrySet());
                            regions.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                            Map<String, String> frameByRegion = new HashMap<>();
                            if (regions.size() > 0) frameByRegion.put(regions.get(0).getKey(), "gold");
                            if (regions.size() > 1) frameByRegion.put(regions.get(1).getKey(), "silver");
                            if (regions.size() > 2) frameByRegion.put(regions.get(2).getKey(), "bronze");

                            com.google.firebase.firestore.WriteBatch batch = db.batch();
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : usersSnapshot) {
                                User user = doc.toObject(User.class);
                                String frame = "";
                                if (user != null && user.getRegion() != null) {
                                    frame = frameByRegion.containsKey(user.getRegion())
                                            ? frameByRegion.get(user.getRegion()) : "";
                                }
                                batch.update(doc.getReference(), "avatarFrameColor", frame);
                            }
                            batch.commit().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
                        })
                        .addOnFailureListener(onFailure),
                onFailure);
    }
}
