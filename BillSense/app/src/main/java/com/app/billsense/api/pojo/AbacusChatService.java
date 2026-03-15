package com.app.billsense.api.pojo;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Collections;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AbacusChatService {

    // Define a callback interface for asynchronous results
    public interface AbacusChatCallback {
        void onSuccess(ApiResponse response);
        void onError(String errorMessage);
    }

    public static void getChatResponseAsync(
            String deploymentToken,
            String deploymentId,
            String userMessageText,
            @NonNull AbacusChatCallback callback
    ) {
        AbacusApiService apiService = RetrofitClient.getClient().create(AbacusApiService.class);

        ApiRequestMessage userMessage = new ApiRequestMessage(true, userMessageText);
        ApiRequest requestPayload = new ApiRequest(Collections.singletonList(userMessage));

        Call<ApiResponse> call = apiService.getChatResponse(deploymentToken, deploymentId, requestPayload);

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) { // Check the success flag within your API response
                        callback.onSuccess(response.body());
                    } else {
                        // Handle cases where the HTTP call was successful but the API logic reported an error
                        String errorMsg = "API reported failure.";
                        if (response.body().getResult() != null &&
                                response.body().getResult().getMessages() != null &&
                                !response.body().getResult().getMessages().isEmpty()) {
                            // You might want to extract a more specific error message from the response if available
                            errorMsg = "API Error: " + response.body().getResult().getMessages().get(0).getText();
                        }
                        callback.onError(errorMsg);
                    }
                } else {
                    String errorMsg = "API Call Failed (Code: " + response.code() + ")";
                    if (response.errorBody() != null) {
                        try {
                            errorMsg += " - Error Body: " + response.errorBody().string();
                        } catch (IOException e) {
                            errorMsg += " - Error reading error body: " + e.getMessage();
                        }
                    }
                    callback.onError(errorMsg);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                callback.onError("Network Error or other Exception: " + t.getMessage());
            }
        });
    }
}
