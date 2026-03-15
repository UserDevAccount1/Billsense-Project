package com.app.billsense.model;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.List;

/**
 * The definitive and correct model class for parsing the detailed multi-scan report from Firebase.
 * This version includes ALL fields from the provided JSON, including the top-level isGenuine.
 * Built on 2025-11-26.
 */
@IgnoreExtraProperties
public class MultiScan {

    // --- Private Fields ---
    private String analysisType;
    private List<AngleImage> angleImages;
    private List<AngleResult> angleResults;
    private int anglesProcessed;
    private String annotatedImageUrl;
    private String authenticity;
    private String confidence;
    private double coveragePercentage;
    private String denomination;
    private int detectedFeaturesCount;
    private List<String> featuresDetected;
    private String firebaseStatus;
    private boolean hasFalseEvp;
    private boolean isHighDenomination;
    private boolean isGenuine; // ### THIS IS THE NEWLY ADDED FIELD ###
    private String processingMode;
    private double processingTime;
    private List<String> reasons;
    private String scanId;
    private String status;
    private String storageId;
    private String timestamp;
    private int totalExpectedFeatures;
    private int totalFeatures;

    // --- Constructors ---

    // A no-argument constructor is REQUIRED by Firebase for deserialization.
    public MultiScan() {}

    // The full constructor that includes the new isGenuine field.
    public MultiScan(String analysisType, List<AngleImage> angleImages, List<AngleResult> angleResults, int anglesProcessed, String annotatedImageUrl, String authenticity, String confidence, double coveragePercentage, String denomination, int detectedFeaturesCount, List<String> featuresDetected, String firebaseStatus, boolean hasFalseEvp, boolean isHighDenomination, boolean isGenuine, String processingMode, double processingTime, List<String> reasons, String scanId, String status, String storageId, String timestamp, int totalExpectedFeatures, int totalFeatures) {
        this.analysisType = analysisType;
        this.angleImages = angleImages;
        this.angleResults = angleResults;
        this.anglesProcessed = anglesProcessed;
        this.annotatedImageUrl = annotatedImageUrl;
        this.authenticity = authenticity;
        this.confidence = confidence;
        this.coveragePercentage = coveragePercentage;
        this.denomination = denomination;
        this.detectedFeaturesCount = detectedFeaturesCount;
        this.featuresDetected = featuresDetected;
        this.firebaseStatus = firebaseStatus;
        this.hasFalseEvp = hasFalseEvp;
        this.isHighDenomination = isHighDenomination;
        this.isGenuine = isGenuine; // ### ADDED HERE ###
        this.processingMode = processingMode;
        this.processingTime = processingTime;
        this.reasons = reasons;
        this.scanId = scanId;
        this.status = status;
        this.storageId = storageId;
        this.timestamp = timestamp;
        this.totalExpectedFeatures = totalExpectedFeatures;
        this.totalFeatures = totalFeatures;
    }


    // --- Getters and Setters ---

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public List<AngleImage> getAngleImages() { return angleImages; }
    public void setAngleImages(List<AngleImage> angleImages) { this.angleImages = angleImages; }

    public List<AngleResult> getAngleResults() { return angleResults; }
    public void setAngleResults(List<AngleResult> angleResults) { this.angleResults = angleResults; }

    public int getAnglesProcessed() { return anglesProcessed; }
    public void setAnglesProcessed(int anglesProcessed) { this.anglesProcessed = anglesProcessed; }

    public String getAnnotatedImageUrl() { return annotatedImageUrl; }
    public void setAnnotatedImageUrl(String annotatedImageUrl) { this.annotatedImageUrl = annotatedImageUrl; }

    public String getAuthenticity() { return authenticity; }
    public void setAuthenticity(String authenticity) { this.authenticity = authenticity; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public double getCoveragePercentage() { return coveragePercentage; }
    public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }

    public String getDenomination() { return denomination; }
    public void setDenomination(String denomination) { this.denomination = denomination; }

    public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
    public void setDetectedFeaturesCount(int detectedFeaturesCount) { this.detectedFeaturesCount = detectedFeaturesCount; }

    public List<String> getFeaturesDetected() { return featuresDetected; }
    public void setFeaturesDetected(List<String> featuresDetected) { this.featuresDetected = featuresDetected; }

    public String getFirebaseStatus() { return firebaseStatus; }
    public void setFirebaseStatus(String firebaseStatus) { this.firebaseStatus = firebaseStatus; }

    public boolean isHasFalseEvp() { return hasFalseEvp; }
    public void setHasFalseEvp(boolean hasFalseEvp) { this.hasFalseEvp = hasFalseEvp; }

    public boolean isHighDenomination() { return isHighDenomination; }
    public void setHighDenomination(boolean highDenomination) { isHighDenomination = highDenomination; }

    // ### GETTER AND SETTER FOR THE NEW FIELD ###
    public boolean isGenuine() { return isGenuine; }
    public void setGenuine(boolean genuine) { isGenuine = genuine; }

    public String getProcessingMode() { return processingMode; }
    public void setProcessingMode(String processingMode) { this.processingMode = processingMode; }

    public double getProcessingTime() { return processingTime; }
    public void setProcessingTime(double processingTime) { this.processingTime = processingTime; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStorageId() { return storageId; }
    public void setStorageId(String storageId) { this.storageId = storageId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
    public void setTotalExpectedFeatures(int totalExpectedFeatures) { this.totalExpectedFeatures = totalExpectedFeatures; }

    public int getTotalFeatures() { return totalFeatures; }
    public void setTotalFeatures(int totalFeatures) { this.totalFeatures = totalFeatures; }

    // --- INNER CLASSES ---

    @IgnoreExtraProperties
    public static class AngleImage {
        private int angleNumber;
        private String imageUrl;

        public AngleImage() {}

        public int getAngleNumber() { return angleNumber; }
        public void setAngleNumber(int angleNumber) { this.angleNumber = angleNumber; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    @IgnoreExtraProperties
    public static class AngleResult {
        private int angleNumber;
        private String authenticityStatus;
        private String confidence;
        private double coveragePercentage;
        private String denomination;
        private int featureCount;
        private List<String> featuresDetected;
        private boolean isGenuine; // This is the inner isGenuine
        private QualityMetrics qualityMetrics;

        public AngleResult() {}

        public int getAngleNumber() { return angleNumber; }
        public void setAngleNumber(int angleNumber) { this.angleNumber = angleNumber; }

        public String getAuthenticityStatus() { return authenticityStatus; }
        public void setAuthenticityStatus(String authenticityStatus) { this.authenticityStatus = authenticityStatus; }

        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }

        public double getCoveragePercentage() { return coveragePercentage; }
        public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }

        public String getDenomination() { return denomination; }
        public void setDenomination(String denomination) { this.denomination = denomination; }

        public int getFeatureCount() { return featureCount; }
        public void setFeatureCount(int featureCount) { this.featureCount = featureCount; }

        public List<String> getFeaturesDetected() { return featuresDetected; }
        public void setFeaturesDetected(List<String> featuresDetected) { this.featuresDetected = featuresDetected; }

        public boolean isGenuine() { return isGenuine; }
        public void setGenuine(boolean genuine) { isGenuine = genuine; }

        public QualityMetrics getQualityMetrics() { return qualityMetrics; }
        public void setQualityMetrics(QualityMetrics qualityMetrics) { this.qualityMetrics = qualityMetrics; }
    }

    @IgnoreExtraProperties
    public static class QualityMetrics {
        private double brightness;
        private double brightnessScore;
        private double contrast;
        private double contrastScore;
        private double overallQuality;
        private String qualityStatus;
        private double sharpness;
        private double sharpnessScore;

        public QualityMetrics() {}

        public double getBrightness() { return brightness; }
        public void setBrightness(double brightness) { this.brightness = brightness; }

        public double getBrightnessScore() { return brightnessScore; }
        public void setBrightnessScore(double brightnessScore) { this.brightnessScore = brightnessScore; }

        public double getContrast() { return contrast; }
        public void setContrast(double contrast) { this.contrast = contrast; }

        public double getContrastScore() { return contrastScore; }
        public void setContrastScore(double contrastScore) { this.contrastScore = contrastScore; }

        public double getOverallQuality() { return overallQuality; }
        public void setOverallQuality(double overallQuality) { this.overallQuality = overallQuality; }

        public String getQualityStatus() { return qualityStatus; }
        public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }

        public double getSharpness() { return sharpness; }
        public void setSharpness(double sharpness) { this.sharpness = sharpness; }

        public double getSharpnessScore() { return sharpnessScore; }
        public void setSharpnessScore(double sharpnessScore) { this.sharpnessScore = sharpnessScore; }
    }
}
