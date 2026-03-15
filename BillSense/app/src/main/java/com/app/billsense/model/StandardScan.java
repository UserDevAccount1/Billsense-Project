package com.app.billsense.model;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.List;

// This annotation is crucial. It tells Firebase to ignore any fields in the JSON
// that don't have a corresponding variable in this Java class. This prevents crashes.
@IgnoreExtraProperties
public class StandardScan {

    // --- Private Fields ---
    private String analysisType;
    private String annotatedImageUrl;
    private Authenticity authenticity;
    private CounterfeitIndicators counterfeitIndicators;
    private double coveragePercentage;
    private String denomination;
    private int detectedFeaturesCount;
    private String featureSummary;
    private String firebaseStatus;
    private boolean isHighDenomination;
    private String logicVersion;
    private String modelInfo;
    private List<String> numberMapping;
    private String processingMode;
    private double processingTime;
    private QualityMetrics qualityMetrics;
    private String scanId;
    private SecurityFeatures securityFeatures;
    private String status;
    private String timestamp;
    private int totalExpectedFeatures;

    // --- Constructors ---

    // A no-argument constructor is required by Firebase for deserialization
    public StandardScan() {}

    // A full constructor for creating an object manually
    public StandardScan(String analysisType, String annotatedImageUrl, Authenticity authenticity, CounterfeitIndicators counterfeitIndicators, double coveragePercentage, String denomination, int detectedFeaturesCount, String featureSummary, String firebaseStatus, boolean isHighDenomination, String logicVersion, String modelInfo, List<String> numberMapping, String processingMode, double processingTime, QualityMetrics qualityMetrics, String scanId, SecurityFeatures securityFeatures, String status, String timestamp, int totalExpectedFeatures) {
        this.analysisType = analysisType;
        this.annotatedImageUrl = annotatedImageUrl;
        this.authenticity = authenticity;
        this.counterfeitIndicators = counterfeitIndicators;
        this.coveragePercentage = coveragePercentage;
        this.denomination = denomination;
        this.detectedFeaturesCount = detectedFeaturesCount;
        this.featureSummary = featureSummary;
        this.firebaseStatus = firebaseStatus;
        this.isHighDenomination = isHighDenomination;
        this.logicVersion = logicVersion;
        this.modelInfo = modelInfo;
        this.numberMapping = numberMapping;
        this.processingMode = processingMode;
        this.processingTime = processingTime;
        this.qualityMetrics = qualityMetrics;
        this.scanId = scanId;
        this.securityFeatures = securityFeatures;
        this.status = status;
        this.timestamp = timestamp;
        this.totalExpectedFeatures = totalExpectedFeatures;
    }


    // --- Getters and Setters ---

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public String getAnnotatedImageUrl() { return annotatedImageUrl; }
    public void setAnnotatedImageUrl(String annotatedImageUrl) { this.annotatedImageUrl = annotatedImageUrl; }

    public Authenticity getAuthenticity() { return authenticity; }
    public void setAuthenticity(Authenticity authenticity) { this.authenticity = authenticity; }

    public CounterfeitIndicators getCounterfeitIndicators() { return counterfeitIndicators; }
    public void setCounterfeitIndicators(CounterfeitIndicators counterfeitIndicators) { this.counterfeitIndicators = counterfeitIndicators; }

    public double getCoveragePercentage() { return coveragePercentage; }
    public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }

    public String getDenomination() { return denomination; }
    public void setDenomination(String denomination) { this.denomination = denomination; }

    public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
    public void setDetectedFeaturesCount(int detectedFeaturesCount) { this.detectedFeaturesCount = detectedFeaturesCount; }

    public String getFeatureSummary() { return featureSummary; }
    public void setFeatureSummary(String featureSummary) { this.featureSummary = featureSummary; }

    public String getFirebaseStatus() { return firebaseStatus; }
    public void setFirebaseStatus(String firebaseStatus) { this.firebaseStatus = firebaseStatus; }

    // For boolean 'is' fields, the getter is 'isHighDenomination()'
    public boolean isHighDenomination() { return isHighDenomination; }
    public void setHighDenomination(boolean highDenomination) { isHighDenomination = highDenomination; }

    public String getLogicVersion() { return logicVersion; }
    public void setLogicVersion(String logicVersion) { this.logicVersion = logicVersion; }

    public String getModelInfo() { return modelInfo; }
    public void setModelInfo(String modelInfo) { this.modelInfo = modelInfo; }

    public List<String> getNumberMapping() { return numberMapping; }
    public void setNumberMapping(List<String> numberMapping) { this.numberMapping = numberMapping; }

    public String getProcessingMode() { return processingMode; }
    public void setProcessingMode(String processingMode) { this.processingMode = processingMode; }

    public double getProcessingTime() { return processingTime; }
    public void setProcessingTime(double processingTime) { this.processingTime = processingTime; }

    public QualityMetrics getQualityMetrics() { return qualityMetrics; }
    public void setQualityMetrics(QualityMetrics qualityMetrics) { this.qualityMetrics = qualityMetrics; }

    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

    public SecurityFeatures getSecurityFeatures() { return securityFeatures; }
    public void setSecurityFeatures(SecurityFeatures securityFeatures) { this.securityFeatures = securityFeatures; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
    public void setTotalExpectedFeatures(int totalExpectedFeatures) { this.totalExpectedFeatures = totalExpectedFeatures; }


    // --- INNER CLASSES (also with private fields and getters/setters) ---

    @IgnoreExtraProperties
    public static class Authenticity {
        private String confidence;
        private double coveragePercentage;
        private String denominationType;
        private int detectedFeaturesCount;
        private boolean isGenuine;
        private boolean hasFalseEvp;
        private List<String> reasons;
        private String status;
        private int totalExpectedFeatures;

        public Authenticity() {}

        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }

        public double getCoveragePercentage() { return coveragePercentage; }
        public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }

        public String getDenominationType() { return denominationType; }
        public void setDenominationType(String denominationType) { this.denominationType = denominationType; }

        public int getDetectedFeaturesCount() { return detectedFeaturesCount; }
        public void setDetectedFeaturesCount(int detectedFeaturesCount) { this.detectedFeaturesCount = detectedFeaturesCount; }

        public boolean isGenuine() { return isGenuine; }
        public void setGenuine(boolean genuine) { isGenuine = genuine; }

        public boolean isHasFalseEvp() { return hasFalseEvp; }
        public void setHasFalseEvp(boolean hasFalseEvp) { this.hasFalseEvp = hasFalseEvp; }

        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getTotalExpectedFeatures() { return totalExpectedFeatures; }
        public void setTotalExpectedFeatures(int totalExpectedFeatures) { this.totalExpectedFeatures = totalExpectedFeatures; }
    }

    @IgnoreExtraProperties
    public static class CounterfeitIndicators {
        private boolean falseEnhancedValuePanel;

        public CounterfeitIndicators() {}

        public boolean isFalseEnhancedValuePanel() { return falseEnhancedValuePanel; }
        public void setFalseEnhancedValuePanel(boolean falseEnhancedValuePanel) { this.falseEnhancedValuePanel = falseEnhancedValuePanel; }
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

    @IgnoreExtraProperties
    public static class SecurityFeatures {
        private boolean concealedValue;
        private boolean enhancedValuePanel;
        private boolean opticallyVariableInk;
        private boolean ovd;
        private boolean securityThread;
        private boolean seeThroughMark;
        private boolean serialNumber;
        private boolean value;
        private boolean watermark;

        public SecurityFeatures() {}

        public boolean isConcealedValue() { return concealedValue; }
        public void setConcealedValue(boolean concealedValue) { this.concealedValue = concealedValue; }

        public boolean isEnhancedValuePanel() { return enhancedValuePanel; }
        public void setEnhancedValuePanel(boolean enhancedValuePanel) { this.enhancedValuePanel = enhancedValuePanel; }

        public boolean isOpticallyVariableInk() { return opticallyVariableInk; }
        public void setOpticallyVariableInk(boolean opticallyVariableInk) { this.opticallyVariableInk = opticallyVariableInk; }

        public boolean isOvd() { return ovd; }
        public void setOvd(boolean ovd) { this.ovd = ovd; }

        public boolean isSecurityThread() { return securityThread; }
        public void setSecurityThread(boolean securityThread) { this.securityThread = securityThread; }

        public boolean isSeeThroughMark() { return seeThroughMark; }
        public void setSeeThroughMark(boolean seeThroughMark) { this.seeThroughMark = seeThroughMark; }

        public boolean isSerialNumber() { return serialNumber; }
        public void setSerialNumber(boolean serialNumber) { this.serialNumber = serialNumber; }

        public boolean isValue() { return value; }
        public void setValue(boolean value) { this.value = value; }

        public boolean isWatermark() { return watermark; }
        public void setWatermark(boolean watermark) { this.watermark = watermark; }
    }
}
