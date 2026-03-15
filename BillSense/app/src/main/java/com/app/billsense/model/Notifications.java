package com.app.billsense.model;

public class Notifications {
    String id, title, body, senderId, receiverId, status, type, time, topic;

    public Notifications() {
    }

    public Notifications(String id, String title, String body, String senderId,
                         String receiverId, String status, String type,
                         String time, String topic) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status = status;
        this.type = type;
        this.time = time;
        this.topic = topic;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
