package com.app.billsense.fcm;

import android.content.Context;
import android.util.Log;

import com.app.billsense.R;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class FcmAuthHelper {

    private static final String TAG = "FcmAuthHelper";
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static String cachedAccessToken = null;
    private static long tokenExpiryTime = 0; // In milliseconds

    // Call this method in a background thread
    public static synchronized String getAccessToken(Context context) throws IOException {
        // Check cache first (simple caching, adjust buffer as needed)
        if (cachedAccessToken != null && System.currentTimeMillis() < (tokenExpiryTime - 5 * 60 * 1000)) { // 5 min buffer
            Log.d(TAG, "Returning cached FCM Access Token");
            return cachedAccessToken;
        }

        Log.d(TAG, "Requesting new FCM Access Token");
        InputStream serviceAccountStream = context.getResources().openRawResource(R.raw.service_account);

        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                .createScoped(Collections.singletonList(FCM_SCOPE));
        // .createScoped(Lists.newArrayList(FCM_SCOPE)); // If using Guava

        credentials.refreshIfExpired(); // Ensures the token is fresh
        cachedAccessToken = credentials.getAccessToken().getTokenValue();
        if (credentials.getAccessToken().getExpirationTime() != null) {
            tokenExpiryTime = credentials.getAccessToken().getExpirationTime().getTime();
            Log.d(TAG, "New Access Token Expires: " + credentials.getAccessToken().getExpirationTime());
        } else {
            // Default to 1 hour if expiration time is null (should not happen with service accounts)
            tokenExpiryTime = System.currentTimeMillis() + 3600 * 1000;
        }

        Log.d(TAG, "Obtained new FCM Access Token: " + cachedAccessToken);
        serviceAccountStream.close();
        return cachedAccessToken;
    }
}
