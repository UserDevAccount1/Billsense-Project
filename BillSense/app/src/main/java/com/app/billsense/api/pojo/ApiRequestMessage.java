package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;

public class ApiRequestMessage {
    @SerializedName("is_user")
    private boolean isUser;

    @SerializedName("text")
    private String text;

    public ApiRequestMessage(boolean isUser, String text) {
        this.isUser = isUser;
        this.text = text;
    }

    public boolean isUser() {
        return isUser;
    }

    public String getText() {
        return text;
    }
}
