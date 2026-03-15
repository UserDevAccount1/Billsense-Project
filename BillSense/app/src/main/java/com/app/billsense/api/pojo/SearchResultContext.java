package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;

public class SearchResultContext {
    @SerializedName("id")
    private String id;

    @SerializedName("imdb_id")
    private String imdbId;

    @SerializedName("title")
    private String title;

    // Getters
    public String getId() { return id; }
    public String getImdbId() { return imdbId; }
    public String getTitle() { return title; }

    @Override
    public String toString() {
        return "SearchResultContext{" +
                "id='" + id + '\'' +
                ", imdbId='" + imdbId + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
