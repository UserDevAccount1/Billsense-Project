package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ApiResult {
    @SerializedName("messages")
    private List<ApiResponseMessage> messages;

    @SerializedName("search_results")
    private List<SearchResult> searchResults;

    // Getters
    public List<ApiResponseMessage> getMessages() { return messages; }
    public List<SearchResult> getSearchResults() { return searchResults; }

    @Override
    public String toString() {
        return "ApiResult{" +
                "messages=" + messages +
                ", searchResults=" + searchResults +
                '}';
    }
}
