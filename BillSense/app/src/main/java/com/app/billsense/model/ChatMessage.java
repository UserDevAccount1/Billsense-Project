package com.app.billsense.model;

import com.google.firebase.database.Exclude;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT = 1;

    private String id;
    private String senderId;
    private String message;
    private int messageType;
    private long timestamp;
    private boolean isTyping;

    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String message, int messageType) {
        this(message, messageType, false);
    }

    public ChatMessage(String message, int messageType, boolean isTyping) {
        this.message = message;
        this.messageType = messageType;
        this.isTyping = isTyping;
        this.timestamp = System.currentTimeMillis();
        if (messageType == TYPE_USER) {
            this.senderId = "currentUser";
        } else {
            this.senderId = "bot";
        }

        if (messageType == TYPE_USER && isTyping) {
            throw new IllegalArgumentException("User messages cannot be in typing state.");
        }
    }

    public String getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getMessage() {
        return message;
    }

    public int getMessageType() {
        return messageType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isTyping() {
        return isTyping;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
    }

    @Exclude
    public boolean isTypingState() {
        return isTyping;
    }
}
