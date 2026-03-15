package com.app.billsense.api.pojo;

import com.google.gson.annotations.SerializedName;

public class ApiResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("result")
    private ApiResult result;

    // Getters
    public boolean isSuccess() { return success; }
    public ApiResult getResult() { return result; }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "success=" + success +
                ", result=" + result +
                '}';
    }
}