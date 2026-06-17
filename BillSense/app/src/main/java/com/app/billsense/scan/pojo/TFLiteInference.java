package com.app.billsense.scan.pojo;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Performs on-device TFLite inference for YOLOv8 OBB models.
 * Loads a .tflite model from a local file and runs inference on Bitmap input.
 * Parses YOLOv8 OBB output format and returns results compatible with StandardScanResponse.
 */
public class TFLiteInference {

    private static final String TAG = "TFLiteInference";

    // YOLOv8 standard input size
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float NMS_IOU_THRESHOLD = 0.45f;

    // Counterfeit model class names (7 classes)
    private static final String[] COUNTERFEIT_CLASSES = {
            "concealed-value", "enhanced-value-panel", "real", "fake",
            "security-thread", "serial-number", "watermark"
    };

    // Security model class names (6 classes)
    private static final String[] SECURITY_CLASSES = {
            "concealed-value", "security-thread", "serial-number",
            "UV-thread", "value", "watermark"
    };

    private Interpreter interpreter;
    private final String modelType;
    private final String[] classNames;
    private boolean isLoaded = false;

    /**
     * Detection result from a single inference.
     */
    public static class Detection {
        public String className;
        public float confidence;
        public float centerX;
        public float centerY;
        public float width;
        public float height;
        public float angle; // OBB rotation angle

        public Detection(String className, float confidence, float cx, float cy,
                         float w, float h, float angle) {
            this.className = className;
            this.confidence = confidence;
            this.centerX = cx;
            this.centerY = cy;
            this.width = w;
            this.height = h;
            this.angle = angle;
        }
    }

    /**
     * Creates a TFLiteInference engine for a specific model type.
     *
     * @param modelType "counterfeit" or "security" - determines class label mapping
     */
    public TFLiteInference(@NonNull String modelType) {
        this.modelType = modelType;
        if ("security".equals(modelType) || "uv".equals(modelType)) {
            this.classNames = SECURITY_CLASSES;
        } else {
            this.classNames = COUNTERFEIT_CLASSES;
        }
    }

    /**
     * Loads a TFLite model from a file.
     *
     * @param modelFile The .tflite model file
     * @return true if loaded successfully
     */
    public boolean loadModel(@NonNull File modelFile) {
        try {
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file does not exist: " + modelFile.getAbsolutePath());
                return false;
            }

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            // Use XNNPACK delegate for CPU acceleration
            options.setUseXNNPACK(true);

            interpreter = new Interpreter(modelFile, options);
            isLoaded = true;

            // Log input/output tensor info
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            Log.d(TAG, "Model loaded: " + modelFile.getName());
            Log.d(TAG, "Input shape: " + Arrays.toString(inputShape));
            Log.d(TAG, "Output shape: " + Arrays.toString(outputShape));

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load TFLite model: " + modelFile.getName(), e);
            isLoaded = false;
            return false;
        }
    }

    /**
     * Returns true if the model is loaded and ready for inference.
     */
    public boolean isLoaded() {
        return isLoaded && interpreter != null;
    }

    /**
     * Runs inference on a Bitmap and returns detected objects.
     *
     * @param bitmap The input image (will be resized to 640x640)
     * @return List of Detection results
     */
    @NonNull
    public List<Detection> runInference(@NonNull Bitmap bitmap) {
        if (!isLoaded()) {
            Log.e(TAG, "Model not loaded, cannot run inference");
            return new ArrayList<>();
        }

        long startTime = System.currentTimeMillis();

        // Preprocess: resize and normalize to [0, 1] float32
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = preprocessBitmap(resized);
        if (resized != bitmap) {
            resized.recycle();
        }

        // Run inference
        // YOLOv8 OBB output shape: [1, num_classes + 5 + 1, num_boxes]
        // = [1, C+6, 8400] where C is num classes, 5 = cx,cy,w,h + angle
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        int numAttributes = outputShape[1]; // e.g., 12 for 6 classes + 6 (cx,cy,w,h,angle,...)
        int numBoxes = outputShape[2];      // e.g., 8400

        float[][][] output = new float[1][numAttributes][numBoxes];

        try {
            interpreter.run(inputBuffer, output);
        } catch (Exception e) {
            Log.e(TAG, "Inference failed", e);
            return new ArrayList<>();
        }

        long inferenceTime = System.currentTimeMillis() - startTime;
        Log.d(TAG, "Inference completed in " + inferenceTime + "ms");

        // Parse detections
        return parseDetections(output[0], numAttributes, numBoxes);
    }

    /**
     * Preprocesses a bitmap into a float32 ByteBuffer normalized to [0, 1].
     */
    private ByteBuffer preprocessBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * width * height * 3);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int pixel : pixels) {
            // RGB channels normalized to [0, 1]
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // G
            buffer.putFloat((pixel & 0xFF) / 255.0f);          // B
        }

        buffer.rewind();
        return buffer;
    }

    /**
     * Parses the raw YOLOv8 OBB output into Detection objects.
     * Output format per box (column): [cx, cy, w, h, class_scores..., angle]
     */
    private List<Detection> parseDetections(float[][] output, int numAttributes, int numBoxes) {
        List<Detection> detections = new ArrayList<>();
        int numClasses = classNames.length;

        for (int i = 0; i < numBoxes; i++) {
            float cx = output[0][i];
            float cy = output[1][i];
            float w = output[2][i];
            float h = output[3][i];

            // Find best class
            float maxScore = 0;
            int bestClass = -1;
            for (int c = 0; c < numClasses && (c + 4) < numAttributes; c++) {
                float score = output[c + 4][i];
                if (score > maxScore) {
                    maxScore = score;
                    bestClass = c;
                }
            }

            if (maxScore < CONFIDENCE_THRESHOLD || bestClass < 0) {
                continue;
            }

            // Angle is the last attribute (if present in OBB models)
            float angle = 0;
            if (numAttributes > numClasses + 4) {
                angle = output[numAttributes - 1][i];
            }

            detections.add(new Detection(
                    classNames[bestClass], maxScore,
                    cx / INPUT_SIZE, cy / INPUT_SIZE,
                    w / INPUT_SIZE, h / INPUT_SIZE,
                    angle
            ));
        }

        // Apply NMS
        detections = applyNMS(detections);
        Log.d(TAG, "Detected " + detections.size() + " objects after NMS");
        return detections;
    }

    /**
     * Simple Non-Maximum Suppression by class.
     */
    private List<Detection> applyNMS(List<Detection> detections) {
        // Sort by confidence descending
        detections.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            result.add(detections.get(i));

            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                if (!detections.get(i).className.equals(detections.get(j).className)) continue;

                float iou = computeIoU(detections.get(i), detections.get(j));
                if (iou > NMS_IOU_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }
        return result;
    }

    /**
     * Computes IoU between two axis-aligned bounding boxes (ignoring OBB angle for simplicity).
     */
    private float computeIoU(Detection a, Detection b) {
        float ax1 = a.centerX - a.width / 2;
        float ay1 = a.centerY - a.height / 2;
        float ax2 = a.centerX + a.width / 2;
        float ay2 = a.centerY + a.height / 2;

        float bx1 = b.centerX - b.width / 2;
        float by1 = b.centerY - b.height / 2;
        float bx2 = b.centerX + b.width / 2;
        float by2 = b.centerY + b.height / 2;

        float interX1 = Math.max(ax1, bx1);
        float interY1 = Math.max(ay1, by1);
        float interX2 = Math.min(ax2, bx2);
        float interY2 = Math.min(ay2, by2);

        float interArea = Math.max(0, interX2 - interX1) * Math.max(0, interY2 - interY1);
        float aArea = a.width * a.height;
        float bArea = b.width * b.height;
        float unionArea = aArea + bArea - interArea;

        return unionArea > 0 ? interArea / unionArea : 0;
    }

    /**
     * Converts inference detections into a StandardScanResponse compatible with the existing UI.
     * This bridges on-device inference with the existing result display pipeline.
     */
    @NonNull
    public StandardScanResponse toStandardScanResponse(@NonNull List<Detection> detections) {
        StandardScanResponse response = new StandardScanResponse();
        response.scanId = UUID.randomUUID().toString();
        response.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
        response.analysisType = "on_device_" + modelType;
        response.status = "completed";
        response.processingMode = "on_device";
        response.modelInfo = "TFLite " + modelType;

        // Determine authenticity from detections
        boolean hasFake = false;
        boolean hasReal = false;
        float maxConfidence = 0;
        int detectedFeatures = 0;

        // Map detected security features
        StandardScanResponse.SecurityFeatures features = new StandardScanResponse.SecurityFeatures();

        for (Detection det : detections) {
            maxConfidence = Math.max(maxConfidence, det.confidence);

            switch (det.className) {
                case "real":
                    hasReal = true;
                    break;
                case "fake":
                    hasFake = true;
                    break;
                case "concealed-value":
                    features.concealedValue = true;
                    detectedFeatures++;
                    break;
                case "security-thread":
                    features.securityThread = true;
                    detectedFeatures++;
                    break;
                case "serial-number":
                    features.serialNumber = true;
                    detectedFeatures++;
                    break;
                case "watermark":
                    features.watermark = true;
                    detectedFeatures++;
                    break;
                case "enhanced-value-panel":
                    features.enhancedValuePanel = true;
                    detectedFeatures++;
                    break;
                case "value":
                    features.value = true;
                    detectedFeatures++;
                    break;
                case "UV-thread":
                    // UV thread maps to security thread in the features model
                    features.securityThread = true;
                    detectedFeatures++;
                    break;
            }
        }

        response.securityFeatures = features;
        response.detectedFeaturesCount = detectedFeatures;
        response.totalExpectedFeatures = classNames.length;
        response.coveragePercentage = classNames.length > 0
                ? (detectedFeatures * 100.0) / classNames.length : 0;

        // Build authenticity
        StandardScanResponse.Authenticity auth = new StandardScanResponse.Authenticity();
        if (hasFake) {
            auth.isGenuine = false;
            auth.status = "COUNTERFEIT DETECTED";
            auth.confidence = String.format(java.util.Locale.US, "%.1f%%", maxConfidence * 100);
            auth.reasons = new ArrayList<>();
            auth.reasons.add("On-device model detected counterfeit indicators");
        } else if (hasReal || detectedFeatures >= 3) {
            auth.isGenuine = true;
            auth.status = "LIKELY GENUINE";
            auth.confidence = String.format(java.util.Locale.US, "%.1f%%", maxConfidence * 100);
            auth.reasons = new ArrayList<>();
            auth.reasons.add("Detected " + detectedFeatures + " security features via on-device analysis");
        } else {
            auth.isGenuine = false;
            auth.status = "INSUFFICIENT FEATURES";
            auth.confidence = String.format(java.util.Locale.US, "%.1f%%", maxConfidence * 100);
            auth.reasons = new ArrayList<>();
            auth.reasons.add("Only " + detectedFeatures + " features detected; more features needed for verification");
        }
        auth.detectedFeaturesCount = detectedFeatures;
        auth.totalExpectedFeatures = classNames.length;
        auth.coveragePercentage = response.coveragePercentage;
        response.authenticity = auth;

        // Feature summary
        List<String> featureNames = new ArrayList<>();
        if (features.concealedValue) featureNames.add("Concealed Value");
        if (features.securityThread) featureNames.add("Security Thread");
        if (features.serialNumber) featureNames.add("Serial Number");
        if (features.watermark) featureNames.add("Watermark");
        if (features.enhancedValuePanel) featureNames.add("Enhanced Value Panel");
        if (features.value) featureNames.add("Value");
        response.featureSummary = featureNames.isEmpty()
                ? "No features detected"
                : String.join(", ", featureNames);

        return response;
    }

    /**
     * Releases the TFLite interpreter resources.
     */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        isLoaded = false;
    }
}
