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

    // Denomination model (detect, 6 classes — index order from denomination2.pt).
    public static final String[] DENOMINATION_CLASSES = {
            "20", "50", "100", "200", "500", "1000"
    };

    // securitycf model (detect, 16 classes — exact index order from securitycf.pt):
    // 8 genuine security features + 8 false_* forgery markers.
    // 'bill' / 'false_bill' are noisy whole-note guesses and are NOT used as features/condemners.
    public static final String[] SECURITYCF_CLASSES = {
            "bill", "watermark", "see_through_mark", "shadow_thread", "security_thread",
            "concealed_value", "enhanced_value_panel", "ovi",
            "false_bill", "false_watermark", "false_see_through_mark", "false_shadow_thread",
            "false_security_thread", "false_concealed_value", "false_enhanced_value_panel", "false_ovi"
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
        switch (modelType) {
            case "denomination": this.classNames = DENOMINATION_CLASSES; break;
            case "securitycf":   this.classNames = SECURITYCF_CLASSES; break;
            case "security":
            case "uv":           this.classNames = SECURITY_CLASSES; break;
            default:             this.classNames = COUNTERFEIT_CLASSES; break;
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
     * Builds the offline scan result from denomination + securitycf detections,
     * mirroring the SERVER's evaluate_counterfeit (v17.17 corroboration rule + v17.19):
     *   - denomination = highest-confidence denom detection;
     *   - genuine features populate the checklist;
     *   - a false_* marker only condemns at conf >= 0.75 AND when its genuine
     *     counterpart is NOT also detected; COUNTERFEIT requires >= 2 such markers
     *     AND fewer than 2 hard security features (so one noisy marker never condemns);
     *   - otherwise GENUINE (>=50% coverage) / LIKELY GENUINE.
     */
    @NonNull
    public static StandardScanResponse buildOfflineResponse(
            @NonNull List<Detection> denomDets, @NonNull List<Detection> scfDets) {
        StandardScanResponse r = new StandardScanResponse();
        r.scanId = UUID.randomUUID().toString();
        r.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
        r.analysisType = "on_device_scan";
        r.status = "completed";
        r.processingMode = "on_device";
        r.modelInfo = "TFLite denomination2 + securitycf";
        r.logicVersion = "offline-17.19";

        // Denomination: highest-confidence detection
        String denom = "UNKNOWN";
        float denomConf = 0f;
        for (Detection d : denomDets) {
            if (d.confidence > denomConf) { denomConf = d.confidence; denom = d.className; }
        }
        r.denomination = denom;
        boolean highDenom = "500".equals(denom) || "1000".equals(denom);
        r.isHighDenomination = highDenom;

        // securitycf: collect genuine features + best false-marker confidences
        StandardScanResponse.SecurityFeatures f = new StandardScanResponse.SecurityFeatures();
        java.util.Set<String> genuine = new java.util.HashSet<>();
        java.util.Map<String, Float> falseConf = new java.util.HashMap<>();
        for (Detection d : scfDets) {
            String c = d.className;
            if (c.startsWith("false_")) {
                if (!c.equals("false_bill")) {
                    Float prev = falseConf.get(c);
                    if (prev == null || d.confidence > prev) falseConf.put(c, d.confidence);
                }
            } else if (!c.equals("bill")) {
                genuine.add(c);
                switch (c) {
                    case "watermark": f.watermark = true; break;
                    case "see_through_mark": f.seeThroughMark = true; break;
                    case "security_thread":
                    case "shadow_thread": f.securityThread = true; break; // no dedicated shadow field
                    case "concealed_value": f.concealedValue = true; break;
                    case "enhanced_value_panel": f.enhancedValuePanel = true; break;
                    case "ovi": f.opticallyVariableInk = true; break;
                }
            }
        }
        r.securityFeatures = f;

        // false_X only condemns if genuine X is absent
        java.util.Map<String, String> genuineOf = new java.util.HashMap<>();
        genuineOf.put("false_watermark", "watermark");
        genuineOf.put("false_see_through_mark", "see_through_mark");
        genuineOf.put("false_shadow_thread", "shadow_thread");
        genuineOf.put("false_security_thread", "security_thread");
        genuineOf.put("false_concealed_value", "concealed_value");
        genuineOf.put("false_enhanced_value_panel", "enhanced_value_panel");
        genuineOf.put("false_ovi", "ovi");

        java.util.List<String> condemning = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Float> e : falseConf.entrySet()) {
            if (e.getValue() >= 0.75f && !genuine.contains(genuineOf.get(e.getKey()))) {
                condemning.add(e.getKey());
            }
        }

        String[] hard = {"watermark", "see_through_mark", "security_thread",
                "concealed_value", "shadow_thread", "enhanced_value_panel", "ovi"};
        int hardGenuine = 0;
        for (String h : hard) if (genuine.contains(h)) hardGenuine++;

        int detected = genuine.size();
        int total = highDenom ? 9 : 6;
        double coverage = total > 0 ? Math.min(100.0, (detected * 100.0) / total) : 0;

        StandardScanResponse.Authenticity a = new StandardScanResponse.Authenticity();
        a.reasons = new ArrayList<>();
        boolean corroborated = condemning.size() >= 2 && hardGenuine < 2;
        if ("UNKNOWN".equals(denom)) {
            a.isGenuine = false; a.status = "UNKNOWN";
            a.reasons.add("Could not identify the denomination. Re-scan the full note in good lighting.");
        } else if (corroborated) {
            a.isGenuine = false; a.status = "COUNTERFEIT";
            StringBuilder sb = new StringBuilder();
            for (String m : condemning) sb.append(m.replace("false_", "").replace("_", " ")).append(", ");
            a.reasons.add("Multiple counterfeit markers (" + sb.toString().replaceAll(", $", "")
                    + ") with no genuine security features verified.");
        } else if (coverage >= 50.0) {
            a.isGenuine = true; a.status = "GENUINE";
            a.reasons.add(detected + "/" + total + " security features verified ("
                    + Math.round(coverage) + "% coverage) — consistent with a genuine note.");
        } else {
            a.isGenuine = true; a.status = "LIKELY GENUINE";
            a.reasons.add("Only " + detected + "/" + total + " features visible offline — "
                    + "watermark / see-through / OVI need backlight or tilt.");
        }
        a.confidence = String.format(java.util.Locale.US, "%.1f%%", Math.max(denomConf, 0f) * 100);
        a.coveragePercentage = coverage;
        a.detectedFeaturesCount = detected;
        a.totalExpectedFeatures = total;
        a.denominationType = highDenom ? "HIGH_DENOMINATION" : "LOW_DENOMINATION";
        a.hasFalseEvp = falseConf.containsKey("false_enhanced_value_panel");
        a.authenticityScore = (int) Math.round(coverage);
        r.authenticity = a;

        r.detectedFeaturesCount = detected;
        r.totalExpectedFeatures = total;
        r.coveragePercentage = coverage;

        java.util.List<String> names = new java.util.ArrayList<>();
        for (String g : genuine) names.add(g.replace("_", " "));
        r.featureSummary = names.isEmpty() ? "No features detected" : String.join(", ", names);
        return r;
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
