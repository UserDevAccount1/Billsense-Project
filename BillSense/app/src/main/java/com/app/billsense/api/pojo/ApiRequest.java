package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ApiRequest {
    @SerializedName("messages")
    private List<ApiRequestMessage> messages;

    public ApiRequest(List<ApiRequestMessage> messages) {
        this.messages = messages;
    }

    // Getter
    public List<ApiRequestMessage> getMessages() {
        return messages;
    }
}
