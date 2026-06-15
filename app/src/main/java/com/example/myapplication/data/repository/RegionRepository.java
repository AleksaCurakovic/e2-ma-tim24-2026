package com.example.myapplication.data.repository;

import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.data.model.User;
import com.example.myapplication.util.CycleUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final RankingRepository rankingRepository = new RankingRepository();

    public void loadOverview(OnSuccessListener<RegionOverview> onSuccess, OnFailureListener onFailure) {
        db.collection("users").get()
                .addOnSuccessListener(usersSnapshot -> {
                    List<User> users = new ArrayList<>();
                    Map<String, User> usersByUid = new HashMap<>();
                    Map<String, RegionStats> stats = new HashMap<>();

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : usersSnapshot) {
                        User user = doc.toObject(User.class);
                        if (user == null) continue;
                        users.add(user);
                        if (user.getUid() != null) usersByUid.put(user.getUid(), user);
                        String region = normalize(user.getRegion());
                        if (region.isEmpty()) continue;
                        RegionStats s = statsFor(stats, region);
                        s.totalPlayers++;
                        if (user.isLoggedIn()) s.activePlayers++;
                    }

                    rankingRepository.getRanking(CycleUtil.getCurrentMonthlyCycleId(), ranking -> {
                        Map<String, Integer> starsByRegion = aggregateStarsByRegion(ranking, usersByUid);
                        applyPlacements(stats, starsByRegion);
                        onSuccess.onSuccess(new RegionOverview(users, stats, starsByRegion));
                    }, e -> onSuccess.onSuccess(new RegionOverview(users, stats, new HashMap<>())));
                })
                .addOnFailureListener(onFailure);
    }

    private Map<String, Integer> aggregateStarsByRegion(List<RankingEntry> ranking, Map<String, User> usersByUid) {
        Map<String, Integer> starsByRegion = new HashMap<>();
        for (RankingEntry entry : ranking) {
            User user = usersByUid.get(entry.getUid());
            if (user == null) continue;
            String region = normalize(user.getRegion());
            if (region.isEmpty()) continue;
            int current = starsByRegion.containsKey(region) ? starsByRegion.get(region) : 0;
            starsByRegion.put(region, current + entry.getStarsEarned());
        }
        return starsByRegion;
    }

    private void applyPlacements(Map<String, RegionStats> stats, Map<String, Integer> starsByRegion) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(starsByRegion.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < list.size() && i < 3; i++) {
            RegionStats s = statsFor(stats, list.get(i).getKey());
            if (i == 0) s.firstPlaces = 1;
            if (i == 1) s.secondPlaces = 1;
            if (i == 2) s.thirdPlaces = 1;
        }
    }

    private RegionStats statsFor(Map<String, RegionStats> stats, String region) {
        RegionStats s = stats.get(region);
        if (s == null) {
            s = new RegionStats(region);
            stats.put(region, s);
        }
        return s;
    }

    private String normalize(String region) {
        return region == null ? "" : region.trim();
    }

    public static class RegionOverview {
        public final List<User> users;
        public final Map<String, RegionStats> statsByRegion;
        public final Map<String, Integer> starsByRegion;

        public RegionOverview(List<User> users,
                              Map<String, RegionStats> statsByRegion,
                              Map<String, Integer> starsByRegion) {
            this.users = users;
            this.statsByRegion = statsByRegion;
            this.starsByRegion = starsByRegion;
        }
    }

    public static class RegionStats {
        public final String region;
        public int firstPlaces;
        public int secondPlaces;
        public int thirdPlaces;
        public int activePlayers;
        public int totalPlayers;

        public RegionStats(String region) {
            this.region = region;
        }
    }
}
