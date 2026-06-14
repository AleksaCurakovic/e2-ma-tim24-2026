package com.example.myapplication.data.model;

/**
 * Poruka u regionalnom četu.
 * Firestore: regionChats/{region}/messages/{id}
 */
public class ChatMessage {
    private String id;
    private String senderUid;
    private String senderUsername;
    private String text;
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String senderUid, String senderUsername, String text) {
        this.senderUid = senderUid;
        this.senderUsername = senderUsername;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
