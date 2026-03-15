package com.app.billsense.fcm;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.app.billsense.BuildConfig;
import com.app.billsense.model.Tokens;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FcmNotificationSender {

    private static final String TAG = "FcmNotificationSender";
    private static final String FCM_API_URL_FORMAT = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient httpClient;
    private Gson gson;
    private Context context; // For Toast messages and getting access token

    private String firebaseProjectId;
    private static volatile FcmNotificationSender INSTANCE;
    private FBUtils fbHelper;

    public FcmNotificationSender(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID;
        this.fbHelper = new FBUtils();
    }

    public static void init(Context context) {
        if (INSTANCE == null) {
            synchronized (FcmNotificationSender.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FcmNotificationSender(context);
                }
            }
        }
    }

    public static FcmNotificationSender get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("FcmNotificationSender.init() must be called before get(). Typically in your Application class.");
        }
        return INSTANCE;
    }

    public void sendNotificationToAllCustomers(final String title, final String body,
            final String senderId, final String type){
        if (!Utils.customerTokens.isEmpty()){
            for (Tokens tokens : Utils.customerTokens){
                sendNotification(tokens.getToken(), title, body, senderId, tokens.getUserId(), type);
            }
        }
    }

    public void sendNotification(final String targetDeviceToken, final String title, final String body,
            final String senderId, final String receiverId, final String type) {
        // Network operations and token fetching must be on a background thread
        new Thread(() -> {
            try {
                String accessToken = FcmAuthHelper.getAccessToken(context);
                if (accessToken == null) {
                    Log.e(TAG, "Failed to get access token.");
                    showToast("Error: Could not get auth token");
                    return;
                }

                String fcmApiUrl = String.format(FCM_API_URL_FORMAT, firebaseProjectId);

                // Construct the JSON payload
                // Using JsonObject for simplicity, you can create POJO classes for better structure
                JsonObject messagePayload = new JsonObject();

                JsonObject message = new JsonObject();
                message.addProperty("token", targetDeviceToken);

                JsonObject notification = new JsonObject();
                notification.addProperty("title", title);
                notification.addProperty("body", body);
                message.add("notification", notification);

                // Optional: Add data payload
                JsonObject data = new JsonObject();
                data.addProperty("title", title);
                data.addProperty("body", body);
                data.addProperty("senderId", senderId);
                data.addProperty("receiverId", receiverId);
                // Add any other custom data you might need
                data.addProperty("sent_timestamp", String.valueOf(System.currentTimeMillis()));
                data.addProperty("type", type);
                message.add("data", data);

                messagePayload.add("message", message);

                String jsonBody = gson.toJson(messagePayload);
                Log.d(TAG, "Request JSON: " + jsonBody);

                RequestBody requestBody = RequestBody.create(jsonBody, JSON);

                Request request = new Request.Builder()
                        .url(fcmApiUrl)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "FCM API call failed: ", e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String responseBodyString = response.body() != null ? response.body().string() : "Empty response body";
                        if (response.isSuccessful()) {
                            Log.d(TAG, "FCM Notification sent successfully: " + responseBodyString);
                            if (!Objects.equals(senderId, receiverId)) {
                            Log.d(TAG, "Notification Saved " + responseBodyString);
                                fbHelper.saveNotificationData(fbHelper.NOTIFICATIONS_PATH,
                                        new Pair<>("title", title),
                                        new Pair<>("body", body),
                                        new Pair<>("senderId", senderId),
                                        new Pair<>("receiverId", receiverId),
                                        new Pair<>("time", String.valueOf(System.currentTimeMillis())),
                                        new Pair<>("type", type),
                                        new Pair<>("topic", "all"),
                                        new Pair<>("status", "unread")
                                );
                            }else {
                            Log.d(TAG, "Notification Not Saved same ids");
                            }
                        } else {
                            Log.e(TAG, "FCM API call error: " + response.code() + " - " + responseBodyString);
                            showToast("Error sending notification: " + response.code());
                        }
                        response.close(); // Important to close the response
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error preparing FCM request: ", e);
                showToast("Error: " + e.getMessage());
            }
        }).start();
    }

    public void sendNotificationToTopic(final String topicName, final String title, final String body,
            final String senderId, final String receiverId, final String type) {
        // Network operations and token fetching must be on a background thread
        new Thread(() -> {
            try {
                String accessToken = FcmAuthHelper.getAccessToken(context);
                if (accessToken == null) {
                    Log.e(TAG, "Failed to get access token.");
                    showToast("Error: Could not get auth token");
                    return;
                }

                String fcmApiUrl = String.format(FCM_API_URL_FORMAT, firebaseProjectId);

                // Construct the JSON payload
                // Using JsonObject for simplicity, you can create POJO classes for better structure
                JsonObject messagePayload = new JsonObject();

                JsonObject message = new JsonObject();
                message.addProperty("topic", (topicName == null) ? Utils.fcmTopic : topicName);

                JsonObject notification = new JsonObject();
                notification.addProperty("title", title);
                notification.addProperty("body", body);
                message.add("notification", notification);

                // Optional: Add data payload
                JsonObject data = new JsonObject();
                data.addProperty("title", title);
                data.addProperty("body", body);
                data.addProperty("senderId", senderId);
                data.addProperty("receiverId", receiverId);
                // Add any other custom data you might need
                data.addProperty("sent_timestamp", String.valueOf(System.currentTimeMillis()));
                data.addProperty("type", type);
                message.add("data", data);

                messagePayload.add("message", message);

                String jsonBody = gson.toJson(messagePayload);
                Log.d(TAG, "Request JSON: " + jsonBody);

                RequestBody requestBody = RequestBody.create(jsonBody, JSON);

                Request request = new Request.Builder()
                        .url(fcmApiUrl)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .build();

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "FCM API call failed: ", e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String responseBodyString = response.body() != null ? response.body().string() : "Empty response body";
                        if (response.isSuccessful()) {
                            Log.d(TAG, "FCM Notification sent successfully: " + responseBodyString);
                            if (!Objects.equals(senderId, receiverId)) {
                            Log.d(TAG, "Notification Saved " + responseBodyString);
                                fbHelper.saveNotificationData(fbHelper.NOTIFICATIONS_PATH,
                                        new Pair<>("title", title),
                                        new Pair<>("body", body),
                                        new Pair<>("senderId", senderId),
                                        new Pair<>("receiverId", receiverId),
                                        new Pair<>("time", String.valueOf(System.currentTimeMillis())),
                                        new Pair<>("type", type),
                                        new Pair<>("topic", topicName),
                                        new Pair<>("status", "unread")
                                );
                            }else {
                            Log.d(TAG, "Notification Not Saved same ids");
                            }
                        } else {
                            Log.e(TAG, "FCM API call error: " + response.code() + " - " + responseBodyString);
                            showToast("Error sending notification: " + response.code());
                        }
                        response.close(); // Important to close the response
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error preparing FCM request: ", e);
                showToast("Error: " + e.getMessage());
            }
        }).start();
    }

    private void showToast(final String message) {
        // Ensure Toast is shown on the main thread
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        );
    }
}
