package com.app.billsense.scan.pojo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Manages ML model configuration and TFLite model downloads.
 * Reads ml_config from Firebase RTDB (via REST to avoid SDK hang)
 * and downloads TFLite models from Firebase Storage when on-device mode is active.
 */
public class TFLiteModelManager {

    private static final String TAG = "TFLiteModelManager";
    private static final String PREFS_NAME = "ml_config_prefs";
    private static final String KEY_SCAN_MODE = "scan_mode";
    private static final String KEY_ACTIVE_MODELS_JSON = "active_models_json";

    private static final String FIREBASE_DB_URL =
            "https://bill-sense-aec6b-default-rtdb.firebaseio.com";
    private static final String STORAGE_PATH = "ml_models/";

    public static final String MODE_CLOUD = "cloud";
    public static final String MODE_ON_DEVICE = "on_device";

    private static volatile TFLiteModelManager instance;

    private final Context context;
    private final SharedPreferences prefs;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private String scanMode = MODE_CLOUD;
    private final ConcurrentHashMap<String, ModelInfo> activeModels = new ConcurrentHashMap<>();
    private boolean configLoaded = false;

    /**
     * Holds info about one TFLite model.
     */
    public static class ModelInfo {
        public boolean enabled;
        public String file;
        public double sizeMb;

        public ModelInfo() {}

        public ModelInfo(boolean enabled, String file, double sizeMb) {
            this.enabled = enabled;
            this.file = file;
            this.sizeMb = sizeMb;
        }
    }

    /**
     * Callback for config load operations.
     */
    public interface ConfigCallback {
        void onConfigLoaded(String mode);
        void onConfigError(String error);
    }

    /**
     * Callback for model download operations.
     */
    public interface DownloadCallback {
        void onProgress(String modelName, int percentComplete);
        void onModelReady(String modelName, File modelFile);
        void onAllModelsReady(Map<String, File> modelFiles);
        void onError(String modelName, String error);
    }

    private TFLiteModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();

        // Load cached config
        scanMode = prefs.getString(KEY_SCAN_MODE, MODE_CLOUD);
        loadCachedActiveModels();
    }

    public static TFLiteModelManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TFLiteModelManager.class) {
                if (instance == null) {
                    instance = new TFLiteModelManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Fetches ml_config from Firebase RTDB via REST API (avoids SDK initialization hangs).
     * Should be called on app startup or before scan.
     */
    public void fetchConfig(@Nullable ConfigCallback callback) {
        String url = FIREBASE_DB_URL + "/ml_config.json";

        Request request = new Request.Builder().url(url).get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch ML config: " + e.getMessage());
                // Fall back to cached config
                if (callback != null) {
                    callback.onConfigError("Network error: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "ML config fetch failed: HTTP " + response.code());
                    if (callback != null) {
                        callback.onConfigError("HTTP " + response.code());
                    }
                    return;
                }

                String json = response.body().string();
                Log.d(TAG, "ML config response: " + json);

                try {
                    parseConfig(json);
                    configLoaded = true;
                    if (callback != null) {
                        callback.onConfigLoaded(scanMode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse ML config", e);
                    if (callback != null) {
                        callback.onConfigError("Parse error: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void parseConfig(String json) {
        if (json == null || json.equals("null") || json.isEmpty()) {
            Log.w(TAG, "No ml_config found in Firebase, using defaults");
            return;
        }

        JsonObject config = gson.fromJson(json, JsonObject.class);

        if (config.has("scan_mode")) {
            scanMode = config.get("scan_mode").getAsString();
            prefs.edit().putString(KEY_SCAN_MODE, scanMode).apply();
        }

        if (config.has("active_models")) {
            JsonObject models = config.getAsJsonObject("active_models");
            activeModels.clear();

            for (String key : models.keySet()) {
                JsonObject modelObj = models.getAsJsonObject(key);
                ModelInfo info = new ModelInfo();
                info.enabled = modelObj.has("enabled") && modelObj.get("enabled").getAsBoolean();
                info.file = modelObj.has("file") ? modelObj.get("file").getAsString() : "";
                info.sizeMb = modelObj.has("size_mb") ? modelObj.get("size_mb").getAsDouble() : 0;
                activeModels.put(key, info);
            }

            // Cache active models
            prefs.edit().putString(KEY_ACTIVE_MODELS_JSON, models.toString()).apply();
        }
    }

    private void loadCachedActiveModels() {
        String cached = prefs.getString(KEY_ACTIVE_MODELS_JSON, null);
        if (cached != null) {
            try {
                JsonObject models = gson.fromJson(cached, JsonObject.class);
                for (String key : models.keySet()) {
                    JsonObject modelObj = models.getAsJsonObject(key);
                    ModelInfo info = new ModelInfo();
                    info.enabled = modelObj.has("enabled") && modelObj.get("enabled").getAsBoolean();
                    info.file = modelObj.has("file") ? modelObj.get("file").getAsString() : "";
                    info.sizeMb = modelObj.has("size_mb") ? modelObj.get("size_mb").getAsDouble() : 0;
                    activeModels.put(key, info);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse cached active models", e);
            }
        }
    }

    /**
     * Returns current scan mode: "cloud" or "on_device".
     */
    public String getScanMode() {
        return scanMode;
    }

    /**
     * Returns true if on-device inference is active.
     */
    public boolean isOnDeviceMode() {
        return MODE_ON_DEVICE.equals(scanMode);
    }

    /**
     * Returns list of enabled model keys.
     */
    public List<String> getEnabledModelKeys() {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ModelInfo> entry : activeModels.entrySet()) {
            if (entry.getValue().enabled) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /**
     * Returns the ModelInfo for a given key, or null if not found.
     */
    @Nullable
    public ModelInfo getModelInfo(String key) {
        return activeModels.get(key);
    }

    /**
     * Returns the local cache file for a model. Does not guarantee the file exists.
     */
    public File getModelFile(String filename) {
        File modelsDir = new File(context.getFilesDir(), "tflite_models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }
        return new File(modelsDir, filename);
    }

    /**
     * Returns true if a model file is already cached on device.
     */
    public boolean isModelCached(String filename) {
        File file = getModelFile(filename);
        return file.exists() && file.length() > 0;
    }

    /**
     * Downloads all enabled models that are not yet cached.
     * Uses direct HTTP download (Firebase Storage SDK hangs on some devices).
     */
    public void downloadEnabledModels(@NonNull DownloadCallback callback) {
        List<String> enabledKeys = getEnabledModelKeys();
        if (enabledKeys.isEmpty()) {
            Log.w(TAG, "No enabled models to download");
            callback.onAllModelsReady(new ConcurrentHashMap<>());
            return;
        }

        ConcurrentHashMap<String, File> readyModels = new ConcurrentHashMap<>();
        AtomicInteger remaining = new AtomicInteger(enabledKeys.size());

        for (String key : enabledKeys) {
            ModelInfo info = activeModels.get(key);
            if (info == null || info.file == null || info.file.isEmpty()) {
                if (remaining.decrementAndGet() == 0) {
                    callback.onAllModelsReady(readyModels);
                }
                continue;
            }

            File localFile = getModelFile(info.file);

            if (localFile.exists() && localFile.length() > 1_000_000) {
                Log.d(TAG, "Model already cached: " + info.file + " (" + localFile.length() + " bytes)");
                readyModels.put(key, localFile);
                callback.onModelReady(key, localFile);
                if (remaining.decrementAndGet() == 0) {
                    callback.onAllModelsReady(readyModels);
                }
                continue;
            }

            // Download via direct HTTP (bypasses Firebase Storage SDK which hangs)
            String encodedPath = (STORAGE_PATH + info.file).replace("/", "%2F");
            String downloadUrl = "https://firebasestorage.googleapis.com/v0/b/bill-sense-aec6b.firebasestorage.app/o/" + encodedPath + "?alt=media";

            Log.d(TAG, "Downloading model via HTTP: " + info.file + " from " + downloadUrl);

            OkHttpClient downloadClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                    .build();

            Request request = new Request.Builder().url(downloadUrl).get().build();

            downloadClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to download model: " + info.file + " - " + e.getMessage());
                    callback.onError(key, e.getMessage());
                    if (remaining.decrementAndGet() == 0) {
                        callback.onAllModelsReady(readyModels);
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        String msg = "HTTP " + response.code();
                        Log.e(TAG, "Download failed for " + info.file + ": " + msg);
                        callback.onError(key, msg);
                        if (remaining.decrementAndGet() == 0) {
                            callback.onAllModelsReady(readyModels);
                        }
                        return;
                    }

                    long totalBytes = response.body().contentLength();
                    Log.d(TAG, "Downloading " + info.file + " size=" + totalBytes + " bytes");

                    try (java.io.InputStream is = response.body().byteStream();
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(localFile)) {
                        byte[] buffer = new byte[8192];
                        long downloaded = 0;
                        int read;
                        int lastPercent = 0;

                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            downloaded += read;
                            if (totalBytes > 0) {
                                int percent = (int) ((downloaded * 100) / totalBytes);
                                if (percent != lastPercent) {
                                    lastPercent = percent;
                                    callback.onProgress(key, percent);
                                }
                            }
                        }
                        fos.flush();
                    }

                    Log.d(TAG, "Model downloaded: " + info.file + " -> " + localFile.length() + " bytes");
                    readyModels.put(key, localFile);
                    callback.onModelReady(key, localFile);
                    if (remaining.decrementAndGet() == 0) {
                        callback.onAllModelsReady(readyModels);
                    }
                }
            });
        }
    }

    /**
     * Clears all cached model files.
     */
    public void clearModelCache() {
        File modelsDir = new File(context.getFilesDir(), "tflite_models");
        if (modelsDir.exists()) {
            File[] files = modelsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    /**
     * Gets total size of enabled models in MB.
     */
    public double getEnabledModelsTotalSizeMb() {
        double total = 0;
        for (ModelInfo info : activeModels.values()) {
            if (info.enabled) {
                total += info.sizeMb;
            }
        }
        return total;
    }
}
