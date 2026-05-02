package com.example.myapplication.data.model;

public class GameRequest {

    private String creatorName;
    private long createdAt;
    private boolean accepted;
    private String joinerName;

    private String gameId;

    public GameRequest() {
    }

    public GameRequest(String creatorName, long createdAt, String gameId) {
        this.creatorName = creatorName;
        this.createdAt = createdAt;
        this.accepted = false;
        this.gameId = gameId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getJoinerName() {
        return joinerName;
    }

    public void setJoinerName(String joinerName) {
        this.joinerName = joinerName;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
}
