package com.app.billsense.model;

public class Tutorials {
    String id, title, description, mediaType, date,
            time, downloadVideoUrl, downloadImageUrl;

    public Tutorials() {
    }

    public Tutorials(String id, String title, String description, String mediaType,
                     String date, String time, String downloadVideoUrl, String downloadImageUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.mediaType = mediaType;
        this.date = date;
        this.time = time;
        this.downloadVideoUrl = downloadVideoUrl;
        this.downloadImageUrl = downloadImageUrl;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDownloadVideoUrl() {
        return downloadVideoUrl;
    }

    public void setDownloadVideoUrl(String downloadVideoUrl) {
        this.downloadVideoUrl = downloadVideoUrl;
    }

    public String getDownloadImageUrl() {
        return downloadImageUrl;
    }

    public void setDownloadImageUrl(String downloadImageUrl) {
        this.downloadImageUrl = downloadImageUrl;
    }
}
