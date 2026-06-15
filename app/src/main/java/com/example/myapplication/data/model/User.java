package com.example.myapplication.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class User {
    private String uid;
    private String username;
    private String email;
    private String region;
    private int tokens;
    private int stars;
    private String leagueName;
    private String leagueIcon;
    private int avatarId;
    private String qrCode;
    private int totalGames;
    private int wonGames;
    private int lostGames;

    // Lista prijatelja (uid-evi) — veza je obostrana.
    private List<String> friends = new ArrayList<>();
    // Da li je korisnik trenutno ulogovan (ne mora biti u prvom planu aplikacije).
    private boolean loggedIn;
    // Da li trenutno učestvuje u nekoj partiji.
    private boolean inGame;

    // Poslednji ciklus za koji je korisniku već dodeljena nagrada sa rang liste.
    // Sprečava ponovno dodeljivanje pri svakom otvaranju aplikacije.
    private String lastClaimedWeeklyCycle;
    private String lastClaimedMonthlyCycle;

    private long lastLoginTime;

    // Poslednji "heartbeat" dok je app u prvom planu. Koristi se da bi se korisnik
    // prikazao offline i kad je aplikacija naglo ugašena (kill/crash) — tada
    // heartbeat prestane i lastSeen zastari.
    private long lastSeen;

    public User() {}

    public User(String uid, String username, String email, String region) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.region = region;
        this.tokens = 5;
        this.lastLoginTime = 0;
        this.leagueName = "Commoner";
        this.leagueIcon = "league0";
        this.avatarId = 0;
        this.totalGames = 0;
        this.wonGames = 0;
        this.lostGames = 0;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public void setLeagueName(String leagueName) {
        this.leagueName = leagueName;
    }

    public String getLeagueIcon() { return leagueIcon;}

    public long getLastLoginTime() { return lastLoginTime; }

    public void setLastLoginTime(long lastLogin) { this.lastLoginTime = lastLogin; }

    public long getLastSeen() { return lastSeen; }

    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public void setLeagueIcon(String leagueIcon) {
        this.leagueIcon = leagueIcon;
    }

    public int getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(int avatarId) {
        this.avatarId = avatarId;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }

    public int getWonGames() {
        return wonGames;
    }

    public void setWonGames(int wonGames) {
        this.wonGames = wonGames;
    }

    public int getLostGames() {
        return lostGames;
    }

    public void setLostGames(int lostGames) {
        this.lostGames = lostGames;
    }

    public String getLastClaimedWeeklyCycle() { return lastClaimedWeeklyCycle; }

    public void setLastClaimedWeeklyCycle(String lastClaimedWeeklyCycle) {
        this.lastClaimedWeeklyCycle = lastClaimedWeeklyCycle;
    }

    public String getLastClaimedMonthlyCycle() { return lastClaimedMonthlyCycle; }

    public void setLastClaimedMonthlyCycle(String lastClaimedMonthlyCycle) {
        this.lastClaimedMonthlyCycle = lastClaimedMonthlyCycle;
    }

    public List<String> getFriends() {
        return friends != null ? friends : new ArrayList<>();
    }

    public void setFriends(List<String> friends) { this.friends = friends; }

    public boolean isLoggedIn() { return loggedIn; }

    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }

    public boolean isInGame() { return inGame; }

    public void setInGame(boolean inGame) { this.inGame = inGame; }
}
