package com.app.billsense.scan.pojo;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Manages real-time WebSocket connections for all scan types.
 * This class handles connecting, sending frames, and parsing responses.
 * FINAL VERSION based on auto-start API documentation.
 */
public class RealTimeScanManager {

    private static final String TAG = "RealTimeScanManager";
    private static final String BASE_URL_WSS = com.app.billsense.BuildConfig.WS_BASE_URL;

    // --- NEW: Public constants for WebSocket endpoints ---
    public static final String ENDPOINT_STANDARD_SCAN = "/ws/standard-scan";
    public static final String ENDPOINT_MULTI_ANGLE_SCAN = "/ws/real-multi-scan";
    public static final String ENDPOINT_VIDEO_SCAN = "/ws/real-video-scan";


    private WebSocket webSocket;
    private final OkHttpClient client;
    private final Gson gson;
    private final RealTimeScanListener listener;

    // --- Cold-start handling (shared by standard/multi/video scans) ---
    private final android.os.Handler watchdog = new android.os.Handler(android.os.Looper.getMainLooper());
    private final OkHttpClient warmClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private String currentEndpoint;
    private int openRetries = 0;
    private static final int MAX_OPEN_RETRIES = 5;
    private volatile boolean opened = false;

    /**
     * Interface to pass WebSocket events back to the UI thread.
     */
    public interface RealTimeScanListener {
        void onConnectionOpen();
        void onScanUpdate(RealTimeScanResponse response);
        void onScanError(String error);
        void onConnectionClosing(String reason);
    }

    public RealTimeScanManager(RealTimeScanListener listener) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for a persistent connection
                .pingInterval(20, TimeUnit.SECONDS)    // Keep-alive ping every 20 seconds
                .build();
        this.gson = new Gson();
        this.listener = listener;
    }

    /**
     * Connects to a specific real-time WebSocket endpoint. Scanning auto-starts on connection.
     * @param endpoint e.g., RealTimeScanManager.ENDPOINT_STANDARD_SCAN
     */
    public void connect(String endpoint) {
        openRetries = 0;            // fresh user-initiated connect
        internalConnect(endpoint);
    }

    private void internalConnect(String endpoint) {
        currentEndpoint = endpoint;
        opened = false;
        if (webSocket != null) {
            try { webSocket.close(1000, "reconnect"); } catch (Exception ignored) {}
            webSocket = null;
        }
        // KEY: warm up FIRST. A cold Cloud Run (scale-to-zero) holds /api/health
        // until the container + 6 models are ready (~20s) then returns 200. We open
        // the WebSocket only AFTER that, so the upgrade succeeds immediately instead
        // of stalling and getting killed by the watchdog mid-cold-start.
        Log.d(TAG, "Warming up server before WS (attempt " + (openRetries + 1) + ")…");
        try {
            Request warm = new Request.Builder()
                    .url(com.app.billsense.BuildConfig.API_BASE_URL + "/api/health").build();
            warmClient.newCall(warm).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                    Log.w(TAG, "Warm-up failed (" + e.getMessage() + ") — opening WS anyway");
                    openSocket(endpoint);
                }
                @Override public void onResponse(@NonNull okhttp3.Call call, @NonNull Response resp) {
                    Log.d(TAG, "Server warm (" + resp.code() + ") — opening WS");
                    resp.close();
                    openSocket(endpoint);
                }
            });
        } catch (Exception e) {
            openSocket(endpoint);
        }

        // Overall safety: cold start + model load can take a while. If still not
        // open after this window, retry the whole warm-then-connect; then give up.
        watchdog.removeCallbacksAndMessages(null);
        watchdog.postDelayed(() -> {
            if (opened) return;
            if (openRetries < MAX_OPEN_RETRIES) {
                openRetries++;
                Log.w(TAG, "Connect watchdog — retry " + openRetries);
                internalConnect(currentEndpoint);
            } else if (listener != null) {
                listener.onScanError("Server is taking too long to respond. Please try again.");
            }
        }, 45000);
    }

    private void openSocket(String endpoint) {
        if (opened) return;
        String url = BASE_URL_WSS + endpoint;
        Request request = new Request.Builder().url(url).build();
        Log.d(TAG, "Opening WebSocket: " + url);
        webSocket = client.newWebSocket(request, new SocketListener());
    }

    /**
     * Sends a completion command to the WebSocket server.
     * @param command e.g., "COMPLETE_SCAN", "STOP_RECORDING"
     */
    public void sendCommand(String command) {
        if (webSocket != null) {
            Log.d(TAG, "Sending command: " + command);
            webSocket.send(command);
        } else {
            Log.w(TAG, "Cannot send command, WebSocket is not connected.");
        }
    }

    /**
     * Converts a Bitmap to a base64 string and sends it over the WebSocket for analysis.
     * @param frame The Bitmap image from the camera.
     */
    public void sendFrame(Bitmap frame) {
        if (webSocket != null) {
            String base64Image = CurrencyApiService.convertBitmapToBase64(frame);
            webSocket.send(base64Image);
        }
    }

    /**
     * Sends a pre-formatted command with an image for multi-angle scans.
     * @param command e.g., "ANGLE_1"
     * @param frame The Bitmap image for that angle.
     */
    public void sendAngleFrame(String command, Bitmap frame) {
        if (webSocket != null) {
            String base64Image = CurrencyApiService.convertBitmapToBase64(frame);
            String fullCommand = command + ":" + base64Image;
            Log.d(TAG, "Sending angle command: " + command);
            webSocket.send(fullCommand);
        }
    }

    /**
     * Closes the WebSocket connection gracefully.
     */
    public void disconnect() {
        watchdog.removeCallbacksAndMessages(null);
        opened = false;
        if (webSocket != null) {
            Log.d(TAG, "Disconnecting WebSocket.");
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
    }

    private final class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            super.onOpen(webSocket, response);
            opened = true;
            openRetries = 0;
            watchdog.removeCallbacksAndMessages(null); // connected — stop the cold-start watchdog
            // *** CRUCIAL CHANGE ***
            // According to the new documentation, scanning is AUTO-START.
            // We no longer send a "START_SCAN" command here. We just notify the UI.
            Log.i(TAG, "WebSocket connection opened! Scanning auto-started.");
            if (listener != null) {
                listener.onConnectionOpen();
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            super.onMessage(webSocket, text);
            Log.d(TAG, "Received message: " + text);
            if (listener != null) {
                try {
                    RealTimeScanResponse response = gson.fromJson(text, RealTimeScanResponse.class);
                    listener.onScanUpdate(response);
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON Parsing error", e);
                    listener.onScanError("Failed to parse server response.");
                }
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            super.onClosing(webSocket, code, reason);
            Log.i(TAG, "WebSocket closing: code=" + code + " reason=" + reason);
            // Echo the close to complete the WebSocket close handshake
            webSocket.close(code, reason);
            RealTimeScanManager.this.webSocket = null;
            if (listener != null) {
                listener.onConnectionClosing(reason);
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
            super.onFailure(webSocket, t, response);
            Log.e(TAG, "WebSocket connection failed!", t);
            // Clear the stale reference so connect() can create a fresh one
            RealTimeScanManager.this.webSocket = null;
            if (!opened && openRetries < MAX_OPEN_RETRIES) {
                openRetries++;
                watchdog.removeCallbacksAndMessages(null);
                Log.w(TAG, "WS failed — warming up + retry " + openRetries);
                watchdog.postDelayed(() -> internalConnect(currentEndpoint), 3000);
            } else {
                watchdog.removeCallbacksAndMessages(null);
                if (listener != null) {
                    listener.onScanError("Connection failed: " + t.getMessage());
                }
            }
        }
    }
}
