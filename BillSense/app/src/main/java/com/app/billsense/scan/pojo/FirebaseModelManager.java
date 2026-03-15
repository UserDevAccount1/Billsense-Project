package com.app.billsense.scan.pojo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages downloading and running TFLite models from Firebase ML.
 * Handles the simple_model (Real/Fake detection) and uv_model (security feature detection).
 */
public class FirebaseModelManager {

    private static final String TAG = "FirebaseModelManager";
    private static final int INPUT_SIZE = 640;
    private static final float CONF_THRESHOLD = 0.15f;

    public static final String MODEL_SIMPLE = "simple_model";
    public static final String MODEL_UV = "uv_model";

    private static final String[] SIMPLE_CLASSES = {"Real", "Fake"};
    private static final String[] UV_CLASSES = {
            "concealed-value", "value", "serial-number",
            "optically-variable-thread", "security-thread", "UV-thread"
    };

    private Interpreter simpleInterpreter;
    private Interpreter uvInterpreter;
    private boolean simpleModelReady = false;
    private boolean uvModelReady = false;

    public interface ModelReadyCallback {
        void onModelReady(String modelName);
        void onModelFailed(String modelName, String error);
    }

    /**
     * Downloads a model from Firebase ML and initializes the TFLite interpreter.
     */
    public void downloadModel(@NonNull String modelName, @NonNull ModelReadyCallback callback) {
        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .requireWifi()
                .build();

        FirebaseModelDownloader.getInstance()
                .getModel(modelName, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
                .addOnSuccessListener(model -> {
                    File modelFile = model.getFile();
                    if (modelFile != null) {
                        try {
                            Interpreter interpreter = new Interpreter(modelFile);
                            if (MODEL_SIMPLE.equals(modelName)) {
                                simpleInterpreter = interpreter;
                                simpleModelReady = true;
                            } else if (MODEL_UV.equals(modelName)) {
                                uvInterpreter = interpreter;
                                uvModelReady = true;
                            }
                            Log.i(TAG, "Model " + modelName + " loaded successfully");
                            callback.onModelReady(modelName);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to create interpreter for " + modelName, e);
                            callback.onModelFailed(modelName, e.getMessage());
                        }
                    } else {
                        callback.onModelFailed(modelName, "Model file is null");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to download model " + modelName, e);
                    callback.onModelFailed(modelName, e.getMessage());
                });
    }

    /**
     * Check if a model is ready for inference.
     */
    public boolean isModelReady(String modelName) {
        if (MODEL_SIMPLE.equals(modelName)) return simpleModelReady;
        if (MODEL_UV.equals(modelName)) return uvModelReady;
        return false;
    }

    /**
     * Run inference on a bitmap using the specified model.
     * Returns predictions in the same format as the cloud API.
     */
    @Nullable
    public List<Map<String, Object>> runInference(@NonNull String modelName, @NonNull Bitmap bitmap) {
        Interpreter interpreter;
        String[] classes;

        if (MODEL_SIMPLE.equals(modelName)) {
            interpreter = simpleInterpreter;
            classes = SIMPLE_CLASSES;
        } else if (MODEL_UV.equals(modelName)) {
            interpreter = uvInterpreter;
            classes = UV_CLASSES;
        } else {
            Log.e(TAG, "Unknown model: " + modelName);
            return null;
        }

        if (interpreter == null) {
            Log.e(TAG, "Interpreter not initialized for " + modelName);
            return null;
        }

        try {
            // Preprocess: resize to 640x640 with letterboxing
            Bitmap resized = letterboxBitmap(bitmap, INPUT_SIZE);
            ByteBuffer inputBuffer = bitmapToByteBuffer(resized);

            // Run inference
            // Output shape depends on model — typically [1, num_detections, 7+num_classes]
            // for OBB: [x_center, y_center, width, height, angle, class_scores...]
            int outputSize = 7 + classes.length; // OBB output format
            float[][][] output = new float[1][100][outputSize]; // max 100 detections
            interpreter.run(inputBuffer, output);

            // Post-process: filter by confidence and convert to API format
            List<Map<String, Object>> predictions = new ArrayList<>();
            float scaleX = (float) bitmap.getWidth() / INPUT_SIZE;
            float scaleY = (float) bitmap.getHeight() / INPUT_SIZE;

            for (int i = 0; i < output[0].length; i++) {
                float[] det = output[0][i];
                // Find best class score
                float maxScore = 0;
                int maxClass = 0;
                for (int c = 0; c < classes.length; c++) {
                    float score = det[5 + c]; // scores start after [x, y, w, h, angle]
                    if (score > maxScore) {
                        maxScore = score;
                        maxClass = c;
                    }
                }

                if (maxScore < CONF_THRESHOLD) continue;

                // Extract OBB parameters
                float cx = det[0] * scaleX;
                float cy = det[1] * scaleY;
                float w = det[2] * scaleX;
                float h = det[3] * scaleY;
                float angle = det[4]; // radians

                // Convert OBB to 4 corner points
                List<int[]> points = obbToPoints(cx, cy, w, h, angle);

                Map<String, Object> prediction = new HashMap<>();
                prediction.put("class", classes[maxClass]);
                prediction.put("confidence", (double) maxScore);
                prediction.put("points", points);
                predictions.add(prediction);
            }

            return predictions;
        } catch (Exception e) {
            Log.e(TAG, "Inference failed for " + modelName, e);
            return null;
        }
    }

    /**
     * Resize bitmap with letterboxing to preserve aspect ratio.
     */
    private Bitmap letterboxBitmap(Bitmap src, int targetSize) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        float scale = Math.min((float) targetSize / srcW, (float) targetSize / srcH);
        int newW = Math.round(srcW * scale);
        int newH = Math.round(srcH * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(src, newW, newH, true);
        Bitmap letterboxed = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(letterboxed);
        canvas.drawColor(android.graphics.Color.BLACK); // pad with black
        int left = (targetSize - newW) / 2;
        int top = (targetSize - newH) / 2;
        canvas.drawBitmap(scaled, left, top, null);

        return letterboxed;
    }

    /**
     * Convert a 640x640 ARGB bitmap to a float ByteBuffer normalized to [0, 1].
     */
    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // G
            buffer.putFloat((pixel & 0xFF) / 255.0f);          // B
        }
        buffer.rewind();
        return buffer;
    }

    /**
     * Convert oriented bounding box (center, size, angle) to 4 corner points.
     */
    private List<int[]> obbToPoints(float cx, float cy, float w, float h, float angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        float hw = w / 2;
        float hh = h / 2;

        // Four corners relative to center
        float[][] corners = {
                {-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}
        };

        List<int[]> points = new ArrayList<>();
        for (float[] c : corners) {
            int x = (int) (cx + c[0] * cos - c[1] * sin);
            int y = (int) (cy + c[0] * sin + c[1] * cos);
            points.add(new int[]{x, y});
        }
        return points;
    }

    /**
     * Release interpreter resources.
     */
    public void close() {
        if (simpleInterpreter != null) {
            simpleInterpreter.close();
            simpleInterpreter = null;
            simpleModelReady = false;
        }
        if (uvInterpreter != null) {
            uvInterpreter.close();
            uvInterpreter = null;
            uvModelReady = false;
        }
    }
}
