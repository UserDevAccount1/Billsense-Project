package com.app.billsense.scan.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Represents the JSON response from the /api/multi-scan endpoint.
 * This class is now updated to be fully compatible with Firebase serialization.
 */
public class MultiScanResponse {

    // --- Fields are now PUBLIC ---
    @SerializedName("scan_id")
    public String scanId;
    @SerializedName("timestamp")
    public String timestamp;
    @SerializedName("analysis_type")
    public String analysisType;
    @SerializedName("authenticity")
    public String authenticity;
    @SerializedName("is_genuine")
    public boolean isGenuine;
    @SerializedName("denomination")
    public String denomination;
    @SerializedName("angles_processed")
    public int anglesProcessed;
    @SerializedName("total_features")
    public int totalFeatures;
    @SerializedName("features_detected")
    public List<String> featuresDetected;
    @SerializedName("coverage_percentage")
    public double coveragePercentage;
    @SerializedName("confidence")
    public String confidence;
    @SerializedName("reasons")
    public List<String> reasons;
    @SerializedName("angle_results")
    public List<AngleResult> angleResults;
    @SerializedName("processing_time")
    public double processingTime;
    @SerializedName("has_false_evp")
    public boolean hasFalseEvp;
    @SerializedName("is_high_denomination")
    public boolean isHighDenomination;
    @SerializedName("total_expected_features")
    public int totalExpectedFeatures;
    @SerializedName("detected_features_count")
    public int detectedFeaturesCount;
    @SerializedName("angle_images")
    public List<AngleImage> angleImages;
    @SerializedName("annotated_image_url")
    public String annotatedImageUrl;
    @SerializedName("firebase_status")
    public String firebaseStatus;
    @SerializedName("status")
    public String status;
    @SerializedName("message")
    public String message;
    @SerializedName("processing_mode")
    public String processingMode;
    @SerializedName("storage_id")
    public String storageId;
    // --- Real-measurement layer (v17.5) ---
    @SerializedName("authenticity_score")
    public int authenticityScore;
    @SerializedName("ovi_color_shift")
    public OviColorShift oviColorShift;

    // Public default constructor
    public MultiScanResponse() {}

    // --- OVI/OVD colour-shift measurement (Multi-Scan) ---
    public static class OviColorShift {
        @SerializedName("delta")
        public double delta;
        @SerializedName("shift_detected")
        public boolean shiftDetected;
        @SerializedName("note")
        public String note;
        public OviColorShift() {}
        public double getDelta() { return delta; }
        public boolean isShiftDetected() { return shiftDetected; }
        public String getNote() { return note; }
    }

    // --- INNER CLASSES with PUBLIC fields ---
    public static class AngleResult {
        @SerializedName("angle_number")
        public int angleNumber;
        @SerializedName("denomination")
        public String denomination;
        @SerializedName("features_detected")
        public List<String> featuresDetected;
        @SerializedName("feature_count")
        public int featureCount;
        @SerializedName("coverage_percentage")
        public double coveragePercentage;
        @SerializedName("authenticity")
        public String authenticityStatus;
        @SerializedName("is_genuine")
        public boolean isGenuine;
        @SerializedName("confidence")
        public String confidence;
        @SerializedName("quality_metrics")
        public QualityMetrics qualityMetrics;

        // Public default constructor
        public AngleResult() {}

        // Original getters remain untouched
        public int getAngleNumber() { return angleNumber; }
        public String getDenomination() { return denomination; }
        public List<String> getFeaturesDetected() { return featuresDetected; }
        public int getFeatureCount() { return featureCount; }
        public double getCoveragePercentage() { return coveragePercentage; }
        public String getAuthenticityStatus() { return authenticityStatus; }
        public boolean isGenuine() { return isGenuine; }
        public String getConfidence() { return confidence; }
        public QualityMetrics getQualityMetrics() { return qualityMetrics; }
    }

    public static class QualityMetrics {
        @SerializedName("sharpness")
        public double sharpness;
        @SerializedName("brightness")
        public double brightness;
        @SerializedName("contrast")
        public double contrast;
        @SerializedName("sharpness_score")
        public double sharpnessScore;
        @SerializedName("brightness_score")
        public double brightnessScore;
        @SerializedName("contrast_score")
        public double contrastScore;
        @SerializedName("overall_quality")
        public double overallQuality;
        @SerializedName("quality_status")
        public String qualityStatus;

        // Public default constructor
        public QualityMetrics() {}

        // Original getters remain untouched
        public double getSharpness() { return sharpness; }
        public double getBrightness() { return brightness; }
        public double getContrast() { return contrast; }
        public double getSharpnessScore() { return sharpnessScore; }
        public double getBrightnessScore() { return brightnessScore; }
        public double getContrastScore() { return contrastScore; }
        public double getOverallQuality() { return overallQuality; }
        public String getQualityStatus() { return qualityStatus; }
    }

    public static class AngleImage {
        @SerializedName("angle_number")
        public int angleNumber;
        @SerializedName("image_url")
        public String imageUrl;

        // Public default constructor
        public AngleImage() {}

        // Original getters remain untouched
        public int getAngleNumber() { return angleNumber; }
        public String getImageUrl() { return imageUrl; }
    }

    // --- Getters for the main response object remain untouched ---
    public String getScanId() { return scanId; }
    public String getTimestamp() { return timestamp; }
    public String getAnalysisType() { return analysisType; }
    public String getAuthenticity() { return authenticity; }
    public boolean isGenuine() { return isGenuine; }
    public String getDenomination() { return denomination; }
    public int getAnglesProcessed() { return anglesProcessed; }
    public int getTotalFeatures() { return totalFeatures; }
    public List<String> getFeaturesDetected() { return featuresDetected; }
    public double getCoveragePercentage() { return coveragePercentage; }
    public String getConfidence() { return confidence; }
    public List<String> getReasons() { return reasons; }
    public List<AngleResult> getAngleResults() { return angleResults; }
    public double getProcessingTime() { return processingTime; }
    public boolean hasFalseEvp() { return hasFalseEvp; }
    public boolean isHighDenomination() { return isHighDenomination; }
    public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
    public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
    public List<AngleImage> getAngleImages() { return angleImages; }
    public String getAnnotatedImageUrl() { return annotatedImageUrl; }
    public String getFirebaseStatus() { return firebaseStatus; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getProcessingMode() { return processingMode; }
    public String getStorageId() { return storageId; }
    public int getAuthenticityScore() { return authenticityScore; }
    public OviColorShift getOviColorShift() { return oviColorShift; }
}
