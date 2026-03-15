package com.app.billsense.scan.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Represents the JSON response from the /api/video-scan endpoint.
 * This class is now updated to be fully compatible with Firebase serialization.
 */
public class VideoScanResponse {

    // --- Core Fields are now PUBLIC ---
    @SerializedName("scan_id")
    public String scanId;

    @SerializedName("timestamp")
    public String timestamp;

    @SerializedName("status")
    public String status;

    @SerializedName("analysis_type")
    public String analysisType;

    @SerializedName("denomination")
    public String denomination;

    @SerializedName("authenticity")
    public Authenticity authenticity;

    // --- Feature & Denomination Details ---
    @SerializedName("security_features")
    public SecurityFeatures securityFeatures;

    @SerializedName("counterfeit_indicators")
    public CounterfeitIndicators counterfeitIndicators;

    @SerializedName("is_high_denomination")
    public boolean isHighDenomination;

    @SerializedName("feature_summary")
    public String featureSummary;

    @SerializedName("detected_features_count")
    public int detectedFeaturesCount;

    @SerializedName("total_expected_features")
    public int totalExpectedFeatures;

    @SerializedName("coverage_percentage")
    public double coveragePercentage;

    // --- Processing & Metadata ---
    @SerializedName("model_info")
    public String modelInfo;

    @SerializedName("logic_version")
    public String logicVersion;

    @SerializedName("processing_time")
    public double processingTime;

    @SerializedName("frames_processed")
    public int framesProcessed;

    @SerializedName("processing_mode")
    public String processingMode;

    @SerializedName("number_mapping")
    public Map<String, String> numberMapping;

    // --- Quality & Storage ---
    @SerializedName("quality_metrics")
    public QualityMetrics qualityMetrics;

    @SerializedName("quality_feedback")
    public List<String> qualityFeedback;

    @SerializedName("annotated_image_url")
    public String annotatedImageUrl;

    @SerializedName("firebase_status")
    public String firebaseStatus;

    @SerializedName("storage_id")
    public String storageId;

    @SerializedName("best_confidence")
    public double bestConfidence;

    // Public default constructor
    public VideoScanResponse() {}


    // --- INNER CLASSES with PUBLIC fields ---

    public static class Authenticity {
        @SerializedName("is_genuine")
        public boolean isGenuine;
        @SerializedName("status")
        public String status;
        @SerializedName("confidence")
        public String confidence;
        @SerializedName("reasons")
        public List<String> reasons;
        @SerializedName("coverage_percentage")
        public double coveragePercentage;
        @SerializedName("detected_features_count")
        public int detectedFeaturesCount;
        @SerializedName("total_expected_features")
        public int totalExpectedFeatures;
        @SerializedName("denomination_type")
        public String denominationType;
        @SerializedName("has_false_evp")
        public boolean hasFalseEvp;

        // Public default constructor
        public Authenticity() {}

        // Original getters remain untouched
        public boolean isGenuine() { return isGenuine; }
        public String getStatus() { return status; }
        public String getConfidence() { return confidence; }
        public List<String> getReasons() { return reasons; }
        public double getCoveragePercentage() { return coveragePercentage; }
        public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
        public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
        public String getDenominationType() { return denominationType; }
        public boolean hasFalseEvp() { return hasFalseEvp; }
    }

    public static class SecurityFeatures {
        @SerializedName("concealed_value")
        public boolean concealedValue;
        @SerializedName("security_thread")
        public boolean securityThread;
        @SerializedName("serial_number")
        public boolean serialNumber;
        @SerializedName("value")
        public boolean value;
        @SerializedName("watermark")
        public boolean watermark;
        @SerializedName("see_through_mark")
        public boolean seeThroughMark;
        @SerializedName("optically_variable_ink")
        public boolean opticallyVariableInk;
        @SerializedName("ovd")
        public boolean ovd;
        @SerializedName("enhanced_value_panel")
        public boolean enhancedValuePanel;

        // Public default constructor
        public SecurityFeatures() {}
    }

    public static class CounterfeitIndicators {
        @SerializedName("false_enhanced_value_panel")
        public boolean falseEnhancedValuePanel;

        // Public default constructor
        public CounterfeitIndicators() {}
    }

    public static class QualityMetrics {
        @SerializedName("sharpness")
        public double sharpness;
        @SerializedName("brightness")
        public double brightness;
        @SerializedName("contrast")
        public double contrast;
        @SerializedName("overall_quality")
        public double overallQuality;
        @SerializedName("quality_status")
        public String qualityStatus;

        // Public default constructor
        public QualityMetrics() {}
    }


    // --- GETTERS for the main VideoScanResponse class remain untouched ---

    public String getScanId() { return scanId; }
    public String getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
    public String getAnalysisType() { return analysisType; }
    public String getDenomination() { return denomination; }
    public Authenticity getAuthenticity() { return authenticity; }
    public SecurityFeatures getSecurityFeatures() { return securityFeatures; }
    public CounterfeitIndicators getCounterfeitIndicators() { return counterfeitIndicators; }
    public boolean isHighDenomination() { return isHighDenomination; }
    public String getFeatureSummary() { return featureSummary; }
    public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
    public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
    public double getCoveragePercentage() { return coveragePercentage; }
    public String getModelInfo() { return modelInfo; }
    public String getLogicVersion() { return logicVersion; }
    public double getProcessingTime() { return processingTime; }
    public int getFramesProcessed() { return framesProcessed; }
    public String getProcessingMode() { return processingMode; }
    public Map<String, String> getNumberMapping() { return numberMapping; }
    public QualityMetrics getQualityMetrics() { return qualityMetrics; }
    public List<String> getQualityFeedback() { return qualityFeedback; }
    public String getAnnotatedImageUrl() { return annotatedImageUrl; }
    public String getFirebaseStatus() { return firebaseStatus; }
    public String getStorageId() { return storageId; }
    public double getBestConfidence() { return bestConfidence; }
}
