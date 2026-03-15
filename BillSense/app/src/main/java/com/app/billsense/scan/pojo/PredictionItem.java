package com.app.billsense.scan.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;


public class PredictionItem {
    @SerializedName("class") // Matches the JSON key "class"
    private String className; // Field name can be different

    @SerializedName("confidence")
    private double confidence; // Changed to double as per JSON example

    @SerializedName("points")
    private List<List<Integer>> points; // List of [x, y] coordinate pairs

    // Getters and Setters
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<List<Integer>> getPoints() {
        return points;
    }

    public void setPoints(List<List<Integer>> points) {
        this.points = points;
    }
}


