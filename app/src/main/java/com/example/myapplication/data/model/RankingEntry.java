package com.example.myapplication.data.model;

/**
 * Jedan unos na rang listi za određeni ciklus.
 * Čuva se u Firestore-u kao: rankings/{cycleId}/entries/{uid}
 */
public class RankingEntry {
    private String uid;
    private String username;
    private String leagueIcon;
    private int starsEarned;   // zvezde osvojene u tom ciklusu (počinje od 0)
    private int gamesPlayed;   // broj odigranih partija u ciklusu
    private long updatedAt;

    // Ne čuva se u bazi — popunjava se pri učitavanju rang liste.
    private int rank;

    public RankingEntry() {}

    public RankingEntry(String uid, String username, String leagueIcon) {
        this.uid = uid;
        this.username = username;
        this.leagueIcon = leagueIcon;
        this.starsEarned = 0;
        this.gamesPlayed = 0;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getLeagueIcon() { return leagueIcon; }
    public void setLeagueIcon(String leagueIcon) { this.leagueIcon = leagueIcon; }

    public int getStarsEarned() { return starsEarned; }
    public void setStarsEarned(int starsEarned) { this.starsEarned = starsEarned; }

    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
}
