package com.example.myapplication.data.model;

import java.util.Date;

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

    private long lastLoginTime;

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
}
