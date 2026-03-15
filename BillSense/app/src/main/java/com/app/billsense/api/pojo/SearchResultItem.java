package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;

public class SearchResultItem {
    @SerializedName("answer")
    private String answer;

    @SerializedName("context")
    private SearchResultContext context;

    @SerializedName("score")
    private double score;

    // Getters
    public String getAnswer() { return answer; }
    public SearchResultContext getContext() { return context; }
    public double getScore() { return score; }

    @Override
    public String toString() {
        return "SearchResultItem{" +
                "answer='" + answer + '\'' +
                ", context=" + context +
                ", score=" + score +
                '}';
    }
}
