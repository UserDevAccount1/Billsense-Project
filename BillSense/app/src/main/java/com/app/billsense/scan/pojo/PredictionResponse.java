package com.app.billsense.scan.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PredictionResponse { // Renamed for clarity
    @SerializedName("predictions")
    private List<PredictionItem> predictions;

    // Getter and Setter
    public List<PredictionItem> getPredictions() {
        return predictions;
    }

    public void setPredictions(List<PredictionItem> predictions) {
        this.predictions = predictions;
    }
}
