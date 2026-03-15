package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SearchResult {
    @SerializedName("msg_id")
    private int msgId;

    @SerializedName("results")
    private List<SearchResultItem> results;

    // Getters
    public int getMsgId() { return msgId; }
    public List<SearchResultItem> getResults() { return results; }

    @Override
    public String toString() {
        return "SearchResult{" +
                "msgId=" + msgId +
                ", results=" + results +
                '}';
    }
}



