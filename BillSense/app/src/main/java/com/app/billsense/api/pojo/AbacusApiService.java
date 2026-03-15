package com.app.billsense.api.pojo;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AbacusApiService {

    @POST("api/v0/getChatResponse") // The path from your curl command
    Call<ApiResponse> getChatResponse(
            @Query("deploymentToken") String deploymentToken,
            @Query("deploymentId") String deploymentId,
            @Body ApiRequest requestBody
    );
}



