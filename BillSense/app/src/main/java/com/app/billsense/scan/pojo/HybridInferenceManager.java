package com.app.billsense.scan.pojo;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Manages hybrid inference strategy:
 * 1. Try on-device inference first (Firebase ML TFLite model) for speed
 * 2. Fall back to cloud API if model not ready or confidence is low
 *
 * Usage:
 *   HybridInferenceManager manager = new HybridInferenceManager(listener);
 *   manager.initialize(); // Downloads models
 *   manager.scan(bitmap);  // Uses best available method
 */
public class HybridInferenceManager {

    private static final String TAG = "HybridInferenceManager";
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.5f;

    private final FirebaseModelManager firebaseModelManager;
    private final HybridScanListener listener;
    private boolean useOnDevice = true;

    public interface HybridScanListener {
        void onScanResult(List<Map<String, Object>> predictions, String source);
        void onScanError(String error);
        void onModelStatus(String modelName, boolean ready);
    }

    public HybridInferenceManager(@NonNull HybridScanListener listener) {
        this.firebaseModelManager = new FirebaseModelManager();
        this.listener = listener;
    }

    /**
     * Initialize by downloading Firebase ML models.
     */
    public void initialize() {
        firebaseModelManager.downloadModel(FirebaseModelManager.MODEL_SIMPLE,
                new FirebaseModelManager.ModelReadyCallback() {
                    @Override
                    public void onModelReady(String modelName) {
                        Log.i(TAG, "Simple model ready for on-device inference");
                        listener.onModelStatus(modelName, true);
                    }

                    @Override
                    public void onModelFailed(String modelName, String error) {
                        Log.w(TAG, "Simple model download failed: " + error + ". Will use cloud API.");
                        listener.onModelStatus(modelName, false);
                    }
                });

        firebaseModelManager.downloadModel(FirebaseModelManager.MODEL_UV,
                new FirebaseModelManager.ModelReadyCallback() {
                    @Override
                    public void onModelReady(String modelName) {
                        Log.i(TAG, "UV model ready for on-device inference");
                        listener.onModelStatus(modelName, true);
                    }

                    @Override
                    public void onModelFailed(String modelName, String error) {
                        Log.w(TAG, "UV model download failed: " + error + ". Will use cloud API.");
                        listener.onModelStatus(modelName, false);
                    }
                });
    }

    /**
     * Perform a scan using the best available method.
     * On-device if model is ready, otherwise signals to use cloud API.
     */
    public void scan(@NonNull Bitmap bitmap) {
        scan(bitmap, FirebaseModelManager.MODEL_SIMPLE);
    }

    /**
     * Perform a scan with a specific model.
     */
    public void scan(@NonNull Bitmap bitmap, @NonNull String modelName) {
        if (useOnDevice && firebaseModelManager.isModelReady(modelName)) {
            // Try on-device inference
            List<Map<String, Object>> predictions = firebaseModelManager.runInference(modelName, bitmap);
            if (predictions != null && !predictions.isEmpty()) {
                // Check if max confidence meets threshold
                double maxConf = 0;
                for (Map<String, Object> pred : predictions) {
                    Object conf = pred.get("confidence");
                    if (conf instanceof Double && (Double) conf > maxConf) {
                        maxConf = (Double) conf;
                    }
                }

                if (maxConf >= MIN_CONFIDENCE_THRESHOLD) {
                    listener.onScanResult(predictions, "on-device");
                    return;
                }
                Log.d(TAG, "On-device confidence too low (" + maxConf + "), falling back to cloud");
            }
        }

        // Fall back to cloud API
        listener.onScanResult(null, "cloud-fallback");
    }

    /**
     * Enable or disable on-device inference.
     */
    public void setUseOnDevice(boolean useOnDevice) {
        this.useOnDevice = useOnDevice;
    }

    /**
     * Check if on-device inference is available for a model.
     */
    public boolean isOnDeviceReady(String modelName) {
        return firebaseModelManager.isModelReady(modelName);
    }

    /**
     * Release resources.
     */
    public void close() {
        firebaseModelManager.close();
    }
}
