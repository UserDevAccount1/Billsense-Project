package com.app.billsense.model;

public class Comments {
    String id;
    String userId;
    String postId;
    String userName;
    String comment;
    String likes;

    public Comments() {
    }

    public Comments(String id, String userId, String postId, String userName, String comment, String likes) {
        this.id = id;
        this.userId = userId;
        this.postId = postId;
        this.userName = userName;
        this.comment = comment;
        this.likes = likes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getLikes() {
        return likes;
    }

    public void setLikes(String likes) {
        this.likes = likes;
    }
}
