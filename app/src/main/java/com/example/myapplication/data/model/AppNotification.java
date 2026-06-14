package com.example.myapplication.data.model;

/**
 * Zapis o sistemskoj notifikaciji, čuva se radi istorije obaveštenja.
 * Firestore: users/{uid}/notifications/{id}
 */
public class AppNotification {

    public static final String TYPE_FRIEND_INVITE    = "FRIEND_INVITE";
    public static final String TYPE_RANK_REWARD      = "RANK_REWARD";
    public static final String TYPE_RANK_PLACEMENT   = "RANK_PLACEMENT";
    public static final String TYPE_CHAT_MESSAGE     = "CHAT_MESSAGE";
    public static final String TYPE_LEAGUE_PROMOTION = "LEAGUE_PROMOTION";

    private String id;
    private String type;
    private String title;
    private String message;
    private boolean read;
    private long createdAt;

    // --- Payload za "naknadno reagovanje" (sva polja opciona) ---
    // Pozivnica za partiju:
    private String inviteId;
    private String gameId;
    private String fromUsername;
    private String toUsername;
    // Rang nagrada / plasman:
    private int tokens;
    private int rank;
    private boolean monthly;
    private String dateRange;

    public AppNotification() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getInviteId() { return inviteId; }
    public void setInviteId(String inviteId) { this.inviteId = inviteId; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getToUsername() { return toUsername; }
    public void setToUsername(String toUsername) { this.toUsername = toUsername; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public boolean isMonthly() { return monthly; }
    public void setMonthly(boolean monthly) { this.monthly = monthly; }

    public String getDateRange() { return dateRange; }
    public void setDateRange(String dateRange) { this.dateRange = dateRange; }

    // --- Pomoćni konstruktori (statičke fabrike) ---

    public static AppNotification friendInvite(GameInvite invite) {
        AppNotification n = new AppNotification();
        n.id = "invite_" + invite.getId();
        n.type = TYPE_FRIEND_INVITE;
        n.title = "Poziv za partiju";
        n.message = invite.getFromUsername() + " te poziva na partiju.";
        n.createdAt = System.currentTimeMillis();
        n.inviteId = invite.getId();
        n.gameId = invite.getGameId();
        n.fromUsername = invite.getFromUsername();
        n.toUsername = invite.getToUsername();
        return n;
    }

    public static AppNotification chatMessage(String messageId, String senderUsername, String text) {
        AppNotification n = new AppNotification();
        n.id = "chat_" + messageId;
        n.type = TYPE_CHAT_MESSAGE;
        n.title = "Poruka od " + (senderUsername != null ? senderUsername : "prijatelja");
        n.message = text;
        n.createdAt = System.currentTimeMillis();
        n.fromUsername = senderUsername;
        return n;
    }

    public static AppNotification fromReward(RankReward reward) {
        AppNotification n = new AppNotification();
        n.id = "rank_" + reward.cycleId;
        n.createdAt = System.currentTimeMillis();
        n.rank = reward.rank;
        n.tokens = reward.tokens;
        n.monthly = reward.monthly;
        n.dateRange = reward.dateRange;
        String type = reward.monthly ? "mesečnoj" : "nedeljnoj";
        if (reward.tokens > 0) {
            n.type = TYPE_RANK_REWARD;
            n.title = "Nagrada sa rang liste 🏆";
            n.message = reward.rank + ". mesto na " + type + " rang listi — +"
                    + reward.tokens + " tokena.";
        } else {
            n.type = TYPE_RANK_PLACEMENT;
            n.title = "Plasman na rang listi";
            n.message = "Završio si na " + reward.rank + ". mestu na " + type + " rang listi.";
        }
        return n;
    }
}
