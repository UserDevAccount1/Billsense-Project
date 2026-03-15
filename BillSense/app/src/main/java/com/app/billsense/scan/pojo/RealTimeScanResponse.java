package com.app.billsense.scan.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * A flexible POJO to handle all possible real-time WebSocket responses.
 * FINAL VERSION based on the latest API documentation.
 */
public class RealTimeScanResponse {

    // Common fields across most responses
    @SerializedName("status")
    public String status;

    @SerializedName("message")
    public String message;

    @SerializedName("denomination")
    public String denomination;

    @SerializedName("authenticity")
    public Object authenticity; // Can be a String ("GENUINE") or an Object

    @SerializedName("is_genuine")
    public Boolean isGenuine;

    @SerializedName("features_detected")
    public List<String> featuresDetected;

    @SerializedName("feature_count")
    public Integer featureCount;

    // --- NEWLY ADDED FIELDS ---
    @SerializedName("total_expected_features")
    public Integer totalExpectedFeatures;

    @SerializedName("is_high_denomination")
    public Boolean isHighDenomination;

    @SerializedName("has_false_evp")
    public Boolean hasFalseEvp;
    // -------------------------

    @SerializedName("coverage_percentage")
    public Double coveragePercentage;

    @SerializedName("quality_metrics")
    public QualityMetrics qualityMetrics;

    @SerializedName("quality_feedback")
    public List<String> qualityFeedback;

    @SerializedName("processing_mode")
    public String processingMode;

    // --- ADDED FOR UNIVERSAL REAL-TIME COMPATIBILITY ---
    @SerializedName("recommendation")
    public String recommendation;

    // Standard Scan 'analyzing' fields
    @SerializedName("frame_number")
    public Integer frameNumber;

    @SerializedName("confidence_score")
    public Double confidenceScore;

    // Final "complete" response fields
    @SerializedName("scan_id")
    public String scanId;

    @SerializedName("confidence")
    public String confidence; // e.g., "HIGH", "LOW"

    @SerializedName("reasons")
    public List<String> reasons;

    @SerializedName("frames_processed")
    public Integer framesProcessed;

    @SerializedName("processing_time")
    public Double processingTime;

    @SerializedName("annotated_image_url")
    public String annotatedImageUrl;

    @SerializedName("firebase_status")
    public String firebaseStatus;

    @SerializedName("storage_id")
    public String storageId;

    // Multi-Angle specific fields
    @SerializedName("angle_number")
    public Integer angleNumber;

    @SerializedName("expected_angle")
    public Integer expectedAngle;

    @SerializedName("total_angles")
    public Integer totalAngles;

    @SerializedName("features_this_angle")
    public List<String> featuresThisAngle;

    @SerializedName("all_features")
    public List<String> allFeatures;

    @SerializedName("total_features_so_far")
    public Integer totalFeaturesSoFar;

    @SerializedName("progress")
    public Double progress;

    @SerializedName("angles_processed")
    public Integer anglesProcessed;

    @SerializedName("total_features")
    public Integer totalFeatures;

    @SerializedName("angle_results")
    public List<AngleResult> angleResults;

    @SerializedName("angle_images")
    public List<AngleImage> angleImages;


    // Video Scan specific fields
    @SerializedName("is_recording")
    public Boolean isRecording;

    @SerializedName("current_confidence")
    public Double currentConfidence;

    @SerializedName("best_confidence")
    public Double bestConfidence;

    @SerializedName("security_features")
    public Map<String, Boolean> securityFeatures;

    @SerializedName("counterfeit_indicators")
    public Map<String, Boolean> counterfeitIndicators;

    // --- NEW FIELD ADDED FROM FINAL VIDEO RESPONSE ---
    @SerializedName("feature_summary")
    public String featureSummary;


    // --- NESTED CLASSES FOR COMPLEX OBJECTS ---

    public static class QualityMetrics {
        @SerializedName("sharpness")
        public Double sharpness;
        @SerializedName("brightness")
        public Double brightness;
        @SerializedName("contrast")
        public Double contrast;
        @SerializedName("sharpness_score")
        public Double sharpnessScore;
        @SerializedName("brightness_score")
        public Double brightnessScore;
        @SerializedName("contrast_score")
        public Double contrastScore;
        @SerializedName("overall_quality")
        public Double overallQuality;
        @SerializedName("quality_status")
        public String qualityStatus;
    }

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
    }

    public static class AngleImage {
        @SerializedName("angle_number")
        public int angleNumber;
        @SerializedName("image_url")
        public String imageUrl;
    }
}
