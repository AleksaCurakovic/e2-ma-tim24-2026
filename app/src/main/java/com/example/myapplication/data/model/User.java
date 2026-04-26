package com.example.myapplication.data.model;

public class User {
    private String uid;
    private String username;
    private String email;
    private String region;
    private boolean emailVerified;

    public User() {}

    public User(String uid, String username, String email, String region) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.region = region;
        this.emailVerified = false;
    }
    public String getUid() { return uid; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRegion() { return region; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
}