package com.app.billsense.fcm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.app.billsense.R;
import com.app.billsense.activities.CasesActivity;
import com.app.billsense.activities.MainActivity;
import com.app.billsense.activities.ProfileActivity;
import com.app.billsense.activities.SupportActivity;
import com.app.billsense.activities.VotingActivity;
import com.app.billsense.utils.PrefManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Objects;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom()); // FCM sender ID or topic

        String titleFromNotificationPayload = null;
        String bodyFromNotificationPayload = null;

        // Check for notification payload (used by system tray when app is backgrounded)
        if (remoteMessage.getNotification() != null) {
            titleFromNotificationPayload = remoteMessage.getNotification().getTitle();
            bodyFromNotificationPayload = remoteMessage.getNotification().getBody();
            Log.d(TAG, "Notification Payload: Title: " + titleFromNotificationPayload + ", Body: " + bodyFromNotificationPayload);
        }

        // Always check for data payload
        Map<String, String> dataPayload = remoteMessage.getData();
        if (!dataPayload.isEmpty()) {
            Log.d(TAG, "Data Payload: " + dataPayload.toString());

            // Extract your custom data
            String titleFromData = dataPayload.get("title");
            String bodyFromData = dataPayload.get("body");
            String senderId = dataPayload.get("senderId");
            String receiverId = dataPayload.get("receiverId"); // This should match the current user's ID
            String sentTimestamp = dataPayload.get("sent_timestamp");
            String messageType = dataPayload.get("type");


            Log.d(TAG, "Data - Title: " + titleFromData);
            Log.d(TAG, "Data - Body: " + bodyFromData);
            Log.d(TAG, "Data - Sender ID: " + senderId);
            Log.d(TAG, "Data - Receiver ID: " + receiverId);
            Log.d(TAG, "Data - Timestamp: " + sentTimestamp);
            Log.d(TAG, "Data - Message Type: " + messageType);

            String finalTitle = (titleFromNotificationPayload != null) ? titleFromNotificationPayload : titleFromData;
            String finalBody = (bodyFromNotificationPayload != null) ? bodyFromNotificationPayload : bodyFromData;

            if (finalTitle == null) finalTitle = "New Message"; // Fallback


            // Example: Check if the notification is actually for the current user
            // String currentLoggedInUserId = getCurrentUserId(); // Implement this method
            // if (receiverId != null && receiverId.equals(currentLoggedInUserId)) {
            //     sendNotification(finalTitle, finalBody, senderId, receiverId, dataPayload);
            // } else {
            //     Log.d(TAG, "ReceiverId mismatch or not intended for this user. Ignoring.");
            // }
            sendLocalNotification(finalTitle, finalBody, dataPayload); // Pass the whole data map for flexibility

        } else if (titleFromNotificationPayload != null || bodyFromNotificationPayload != null) {
            // Only notification payload, no data payload.
            // This happens if you send a "notification-only" message from FCM console or API.
            sendLocalNotification(titleFromNotificationPayload, bodyFromNotificationPayload, null);
        } else {
            Log.d(TAG, "Received message with no notification or data payload.");
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        // Send this token to your app server if needed
    }


    private void sendLocalNotification(String title, String body, Map<String, String> data) {
        if (title == null && body == null) {
            Log.d(TAG, "Not creating notification as title and body are null.");
            return;
        }

        Intent intent;
        // Example: Use senderId from data to open a specific chat
        if (data != null && data.containsKey("type")) {
            String messageType = data.get("type");
            if (Objects.equals(messageType, "account")) {
                if (PrefManager.getInstance().isLoggedIn()) {
                    intent = new Intent(this, ProfileActivity.class);
                } else {
                    intent = new Intent(this, MainActivity.class);
                }
            } else if (Objects.equals(messageType, "posts")) {
                if (PrefManager.getInstance().isLoggedIn()) {
                    intent = new Intent(this, VotingActivity.class);
                } else {
                    intent = new Intent(this, MainActivity.class);
                }
            } else if (Objects.equals(messageType, "support")) {
                if (PrefManager.getInstance().isLoggedIn()) {
                    intent = new Intent(this, SupportActivity.class);
                } else {
                    intent = new Intent(this, MainActivity.class);
                }
            } else if (Objects.equals(messageType, "case")) {
                if (PrefManager.getInstance().isLoggedIn()) {
                    intent = new Intent(this, CasesActivity.class);
                } else {
                    intent = new Intent(this, MainActivity.class);
                }
            } else {
                intent = new Intent(this, MainActivity.class);
            }
        } else {
            intent = new Intent(this, MainActivity.class); // Default
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);


        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                (int) System.currentTimeMillis() /* Unique request code */, // Make request code unique
                intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = "fcm_default_channel"; // getString(R.string.default_notification_channel_id)
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.logo_main) // <<-- IMPORTANT: REPLACE
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Default App Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // Use a unique ID for each notification to ensure they all show up
        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        Log.d(TAG, "Notification displayed: Title: " + title);
    }

}