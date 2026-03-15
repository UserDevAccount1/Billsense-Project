package com.app.billsense.scan.pojo;

import com.google.firebase.database.PropertyName;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Represents the JSON response from the /api/standard-scan endpoint.
 * This class is now updated to be fully compatible with API v17.0 and later,
 * and also compatible with Firebase Realtime Database serialization.
 */
public class StandardScanResponse {

    @SerializedName("scan_id")
    public String scanId;

    @SerializedName("timestamp")
    public String timestamp;

    @SerializedName("analysis_type")
    public String analysisType;

    @SerializedName("denomination")
    public String denomination;

    @SerializedName("authenticity")
    public Authenticity authenticity;

    @SerializedName("security_features")
    public SecurityFeatures securityFeatures;

    @SerializedName("is_high_denomination")
    public boolean isHighDenomination;

    @SerializedName("coverage_percentage")
    public double coveragePercentage;

    @SerializedName("processing_time")
    public double processingTime;

    @SerializedName("annotated_image_url")
    public String annotatedImageUrl;

    @SerializedName("status")
    public String status;

    @SerializedName("firebase_status")
    public String firebaseStatus;

    @SerializedName("quality_metrics")
    public QualityMetrics qualityMetrics;

    @SerializedName("counterfeit_indicators")
    public CounterfeitIndicators counterfeitIndicators;

    @SerializedName("feature_summary")
    public String featureSummary;

    @SerializedName("detected_features_count")
    public int detectedFeaturesCount;

    @SerializedName("total_expected_features")
    public int totalExpectedFeatures;

    @SerializedName("number_mapping")
    public Map<String, String> numberMapping;

    @SerializedName("model_info")
    public String modelInfo;

    @SerializedName("logic_version")
    public String logicVersion;

    @SerializedName("quality_feedback")
    public List<String> qualityFeedback;

    @SerializedName("processing_mode")
    public String processingMode;

    // Default constructor required by Firebase
    public StandardScanResponse() {}

    // --- Inner class for the 'authenticity' JSON object ---
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

        // Default constructor required by Firebase
        public Authenticity() {}

        // Getters for Authenticity
        public boolean isGenuine() { return isGenuine; }
        public String getStatus() { return status; }
        public String getConfidence() { return confidence; }
        public List<String> getReasons() { return reasons; }
        public double getCoveragePercentage() { return coveragePercentage; }
        public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
        public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
        public String getDenominationType() { return denominationType; }

        @PropertyName("has_false_evp")
        public boolean hasFalseEvp() { return hasFalseEvp; }
    }

    // --- Inner class for the 'security_features' JSON object ---
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

        // Default constructor required by Firebase
        public SecurityFeatures() {}

        // Getters with @PropertyName to ensure Firebase compatibility
        @PropertyName("concealed_value")
        public boolean hasConcealedValue() { return concealedValue; }
        @PropertyName("security_thread")
        public boolean hasSecurityThread() { return securityThread; }
        @PropertyName("serial_number")
        public boolean hasSerialNumber() { return serialNumber; }
        @PropertyName("value")
        public boolean hasValue() { return value; }
        @PropertyName("watermark")
        public boolean hasWatermark() { return watermark; }
        @PropertyName("see_through_mark")
        public boolean hasSeeThroughMark() { return seeThroughMark; }
        @PropertyName("optically_variable_ink")
        public boolean hasOpticallyVariableInk() { return opticallyVariableInk; }
        @PropertyName("ovd")
        public boolean hasOvd() { return ovd; }
        @PropertyName("enhanced_value_panel")
        public boolean hasEnhancedValuePanel() { return enhancedValuePanel; }
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

        // Default constructor required by Firebase
        public QualityMetrics() {}

        // Getters for QualityMetrics (already in standard format)
        public double getSharpness() { return sharpness; }
        public double getBrightness() { return brightness; }
        public double getContrast() { return contrast; }
        public double getSharpnessScore() { return sharpnessScore; }
        public double getBrightnessScore() { return brightnessScore; }
        public double getContrastScore() { return contrastScore; }
        public double getOverallQuality() { return overallQuality; }
        public String getQualityStatus() { return qualityStatus; }
    }

    public static class CounterfeitIndicators {
        @SerializedName("false_enhanced_value_panel")
        public boolean falseEnhancedValuePanel;

        // Default constructor required by Firebase
        public CounterfeitIndicators() {}

        // Getter with @PropertyName to ensure Firebase compatibility
        @PropertyName("false_enhanced_value_panel")
        public boolean hasFalseEnhancedValuePanel() { return falseEnhancedValuePanel; }
    }


    // --- Getters for the main StandardScanResponse ---
    public String getScanId() { return scanId; }
    public String getTimestamp() { return timestamp; }
    public String getAnalysisType() { return analysisType; }
    public String getDenomination() { return denomination; }
    public Authenticity getAuthenticity() { return authenticity; }
    public SecurityFeatures getSecurityFeatures() { return securityFeatures; }
    public boolean isHighDenomination() { return isHighDenomination; }
    public double getCoveragePercentage() { return coveragePercentage; }
    public double getProcessingTime() { return processingTime; }
    public String getAnnotatedImageUrl() { return annotatedImageUrl; }
    public String getStatus() { return status; }
    public String getFirebaseStatus() { return firebaseStatus; }
    public QualityMetrics getQualityMetrics() { return qualityMetrics; }
    public CounterfeitIndicators getCounterfeitIndicators() { return counterfeitIndicators; }
    public String getFeatureSummary() { return featureSummary; }
    public Map<String, String> getNumberMapping() { return numberMapping; }
    public String getModelInfo() { return modelInfo; }
    public String getLogicVersion() { return logicVersion; }
    public List<String> getQualityFeedback() { return qualityFeedback; }
    public String getProcessingMode() { return processingMode; }
    public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
    public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
}
