package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;

public class ApiResponseMessage {
    @SerializedName("is_user")
    private boolean isUser;

    @SerializedName("text")
    private String text;

    // Getters (and setters if Gson needs them, usually not for deserialization)
    public boolean isUser() {
        return isUser;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "ApiResponseMessage{" +
                "isUser=" + isUser +
                ", text='" + text + '\'' +
                '}';
    }
}