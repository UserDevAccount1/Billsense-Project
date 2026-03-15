package com.app.billsense.scan.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Represents the JSON response from the /api/health endpoint.
 * Based on BillSense API Documentation v16.0.
 */
public class HealthCheckResponse {

    @SerializedName("status")
    private String status;

    @SerializedName("models_loaded")
    private boolean modelsLoaded;

    @SerializedName("firebase_available")
    private boolean firebaseAvailable;

    @SerializedName("api_version")
    private String apiVersion;

    @SerializedName("scan_types")
    private List<String> scanTypes;

    @SerializedName("real_time_endpoints")
    private List<String> realTimeEndpoints;

    // --- Getters ---
    public String getStatus() { return status; }
    public boolean areModelsLoaded() { return modelsLoaded; }
    public boolean isFirebaseAvailable() { return firebaseAvailable; }
    public String getApiVersion() { return apiVersion; }
    public List<String> getScanTypes() { return scanTypes; }
    public List<String> getRealTimeEndpoints() { return realTimeEndpoints; }

    @Override
    public String toString() {
        return "HealthCheckResponse{" +
                "status='" + status + '\'' +
                ", modelsLoaded=" + modelsLoaded +
                ", firebaseAvailable=" + firebaseAvailable +
                ", apiVersion='" + apiVersion + '\'' +
                '}';
    }
}
