package com.example.myapplication.data.model;

/**
 * Pozivnica za partiju upućena prijatelju.
 * Čuva se u Firestore kolekciji "gameInvites".
 */
public class GameInvite {

    public static final String STATUS_PENDING   = "pending";
    public static final String STATUS_ACCEPTED  = "accepted";
    public static final String STATUS_REJECTED  = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED   = "expired";

    private String id;          // id dokumenta (popunjava se pri učitavanju)
    private String fromUid;
    private String fromUsername;
    private String toUid;
    private String toUsername;
    private String gameId;      // unapred generisan id sobe
    private String status;
    private long createdAt;

    public GameInvite() {}

    public GameInvite(String fromUid, String fromUsername, String toUid, String toUsername,
                      String gameId) {
        this.fromUid = fromUid;
        this.fromUsername = fromUsername;
        this.toUid = toUid;
        this.toUsername = toUsername;
        this.gameId = gameId;
        this.status = STATUS_PENDING;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromUid() { return fromUid; }
    public void setFromUid(String fromUid) { this.fromUid = fromUid; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getToUid() { return toUid; }
    public void setToUid(String toUid) { this.toUid = toUid; }

    public String getToUsername() { return toUsername; }
    public void setToUsername(String toUsername) { this.toUsername = toUsername; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
