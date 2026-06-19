package com.app.billsense.scan.standard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityUploadScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.ScanReport;
import com.app.billsense.scan.pojo.CurrencyApiService;
import com.app.billsense.scan.pojo.StandardScanResponse;
import com.app.billsense.scan.pojo.TFLiteInference;
import com.app.billsense.scan.pojo.TFLiteModelManager;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.ProgressDialogUtil;
import com.app.billsense.utils.Utils;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Activity that allows users to pick an image from their gallery
 * and send it to the standard-scan HTTP API for analysis.
 */
public class UploadScanActivity extends AppCompatActivity {

    private static final String TAG = "UploadScanActivity";
    private ActivityUploadScanBinding binding;
    private CurrencyApiService apiService;
    private TFLiteModelManager modelManager;
    private TFLiteInference securitycfInference;
    private TFLiteInference denominationInference;
    private FBUtils fbUtils;
    private String userId;
    private boolean onDeviceReady = false;

    private Uri selectedImageUri;

    // Launcher for picking an image from the gallery
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    displaySelectedImage(uri);
                }
            });

    // Launcher for requesting storage permission (API < 33)
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openImagePicker();
                } else {
                    Toast.makeText(this, "Storage permission is needed to select images.",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUploadScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        apiService = new CurrencyApiService();
        modelManager = TFLiteModelManager.getInstance(this);
        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();

        binding.selectImageButton.setOnClickListener(v -> checkPermissionAndPickImage());
        binding.scanButton.setOnClickListener(v -> performUploadScan());

        // Fetch ML config and prepare on-device models if needed
        initMlConfig();
    }

    /**
     * Fetches ML config from Firebase and prepares on-device models if configured.
     */
    private void initMlConfig() {
        modelManager.fetchConfig(new TFLiteModelManager.ConfigCallback() {
            @Override
            public void onConfigLoaded(String mode) {
                Log.d(TAG, "ML config loaded. Mode: " + mode);
                if (modelManager.isOnDeviceMode()) {
                    prepareOnDeviceModels();
                }
            }

            @Override
            public void onConfigError(String error) {
                Log.w(TAG, "ML config fetch failed: " + error + " — using cached/default (cloud)");
            }
        });
    }

    /**
     * Downloads and loads TFLite models for on-device inference.
     */
    private void prepareOnDeviceModels() {
        modelManager.downloadEnabledModels(new TFLiteModelManager.DownloadCallback() {
            @Override
            public void onProgress(String modelName, int percentComplete) {
                Log.d(TAG, "Downloading " + modelName + ": " + percentComplete + "%");
            }

            @Override
            public void onModelReady(String modelName, File modelFile) {
                Log.d(TAG, "Model ready: " + modelName + " at " + modelFile.getAbsolutePath());
            }

            @Override
            public void onAllModelsReady(Map<String, File> modelFiles) {
                Log.d(TAG, "All models ready. Count: " + modelFiles.size());
                // Load the inference engines
                if (modelFiles.containsKey("securitycf")) {
                    securitycfInference = new TFLiteInference("securitycf");
                    securitycfInference.loadModel(modelFiles.get("securitycf"));
                }
                if (modelFiles.containsKey("denomination")) {
                    denominationInference = new TFLiteInference("denomination");
                    denominationInference.loadModel(modelFiles.get("denomination"));
                }
                onDeviceReady = securitycfInference != null && securitycfInference.isLoaded();
                Log.d(TAG, "On-device inference ready: " + onDeviceReady);
            }

            @Override
            public void onError(String modelName, String error) {
                Log.e(TAG, "Model download error for " + modelName + ": " + error);
            }
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Upload & Scan");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: READ_MEDIA_IMAGES is needed, but GetContent uses the system picker
            // which doesn't require the permission. Just open the picker directly.
            openImagePicker();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23-32: Need READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        } else {
            openImagePicker();
        }
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void displaySelectedImage(Uri imageUri) {
        binding.selectedImageView.setVisibility(View.VISIBLE);
        binding.placeholderIcon.setVisibility(View.GONE);
        Glide.with(this).load(imageUri).into(binding.selectedImageView);
        binding.scanButton.setEnabled(true);
        binding.instructionText.setText("Image selected. Tap \"Scan Now\" to analyze.");

        // Hide any previous results
        binding.resultsLayout.setVisibility(View.GONE);
    }

    private void performUploadScan() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first.", Toast.LENGTH_SHORT).show();
            return;
        }

        File imageFile = Utils.uriToFile(this, selectedImageUri);
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(this, "Failed to access the selected image.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialogUtil.showProgressDialog(this);
        binding.scanButton.setEnabled(false);
        Log.d(TAG, "Starting upload scan with file: " + imageFile.getName() + " size: " + imageFile.length());

        // Route based on ML config mode — check local prefs directly as well
        boolean isOffline = modelManager.isOnDeviceMode();
        if (!isOffline) {
            // Double-check local prefs in case Firebase config fetch failed
            String localMode = getSharedPreferences("ml_config_prefs", MODE_PRIVATE)
                    .getString("scan_mode", "cloud");
            isOffline = "on_device".equals(localMode);
        }

        if (isOffline && onDeviceReady) {
            Log.d(TAG, "Using ON-DEVICE inference");
            performOnDeviceScan(imageFile);
        } else if (isOffline && !onDeviceReady) {
            Log.d(TAG, "On-device mode but models not ready, trying to load...");
            // Try to load models from cache
            tryLoadCachedModels();
            if (onDeviceReady) {
                performOnDeviceScan(imageFile);
            } else {
                ProgressDialogUtil.hideProgressDialog();
                binding.scanButton.setEnabled(true);
                showScanError("Offline models not ready. Please install the scanning package in Profile → Scanning Settings.");
            }
        } else {
            Log.d(TAG, "Using CLOUD API inference");
            checkApiHealthThenScan(imageFile);
        }
    }

    /**
     * Performs on-device TFLite inference on the selected image.
     */
    private void tryLoadCachedModels() {
        File modelsDir = new File(getFilesDir(), "tflite_models");
        File securitycfFile = new File(modelsDir, "securitycf_float32.tflite");
        File denominationFile = new File(modelsDir, "denomination2_float32.tflite");

        if (securitycfFile.exists() && securitycfFile.length() > 1_000_000) {
            if (securitycfInference == null) {
                securitycfInference = new TFLiteInference("securitycf");
            }
            if (!securitycfInference.isLoaded()) {
                securitycfInference.loadModel(securitycfFile);
            }
            Log.d(TAG, "securitycf model loaded from cache: " + securitycfInference.isLoaded());
        }

        if (denominationFile.exists() && denominationFile.length() > 1_000_000) {
            if (denominationInference == null) {
                denominationInference = new TFLiteInference("denomination");
            }
            if (!denominationInference.isLoaded()) {
                denominationInference.loadModel(denominationFile);
            }
            Log.d(TAG, "denomination model loaded from cache: " + denominationInference.isLoaded());
        }

        onDeviceReady = securitycfInference != null && securitycfInference.isLoaded();
        Log.d(TAG, "On-device ready after cache load: " + onDeviceReady);
    }

    private void performOnDeviceScan(File imageFile) {
        new Thread(() -> {
            try {
                // Decode bitmap from file
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), opts);

                if (bitmap == null) {
                    runOnUiThread(() -> {
                        ProgressDialogUtil.hideProgressDialog();
                        binding.scanButton.setEnabled(true);
                        showScanError("Failed to decode image file");
                    });
                    return;
                }

                long startTime = System.currentTimeMillis();

                // Run counterfeit model (primary)
                StandardScanResponse response = null;
                if (securitycfInference != null && securitycfInference.isLoaded()) {
                    List<TFLiteInference.Detection> scfDets = securitycfInference.runInference(bitmap);
                    List<TFLiteInference.Detection> denomDets = new java.util.ArrayList<>();
                    if (denominationInference != null && denominationInference.isLoaded()) {
                        denomDets = denominationInference.runInference(bitmap);
                    }
                    // Combined verdict mirrors the server (corroboration rule, v17.17/v17.19)
                    response = TFLiteInference.buildOfflineResponse(denomDets, scfDets);
                }

                bitmap.recycle();

                if (response == null) {
                    runOnUiThread(() -> {
                        ProgressDialogUtil.hideProgressDialog();
                        binding.scanButton.setEnabled(true);
                        showScanError("No inference models are loaded");
                    });
                    return;
                }

                double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                response.processingTime = elapsed;

                // Ensure scan ID exists
                if (response.getScanId() == null || response.getScanId().isEmpty()) {
                    response.scanId = java.util.UUID.randomUUID().toString();
                }
                if (response.getTimestamp() == null || response.getTimestamp().isEmpty()) {
                    response.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
                }
                if (response.getAnalysisType() == null || response.getAnalysisType().isEmpty()) {
                    response.analysisType = "on_device_scan";
                }

                // Save to Firebase via REST (more reliable)
                final StandardScanResponse finalResponse = response;
                saveOnDeviceScanReport(finalResponse);

                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    binding.scanButton.setEnabled(true);
                    navigateToPostScan(finalResponse);
                });

            } catch (Exception e) {
                Log.e(TAG, "On-device scan failed", e);
                saveFailedScanToFirebase("On-device: " + e.getMessage());
                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    binding.scanButton.setEnabled(true);
                    showScanError("On-device scan error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void checkApiHealthThenScan(File imageFile) {
        OkHttpClient healthClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Request healthReq = new Request.Builder()
                .url("https://billsense-api-340624938055.asia-southeast2.run.app/api/health")
                .get().build();

        healthClient.newCall(healthReq).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Health check failed: " + e.getMessage());
                // Still try the scan — might work
                doUploadScan(imageFile);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Health: " + body);
                boolean modelsLoaded = body.contains("\"models_loaded\":true");
                if (!modelsLoaded) {
                    Log.w(TAG, "Models not loaded — warming up server, will still try scan");
                }
                doUploadScan(imageFile);
            }
        });
    }

    private void doUploadScan(File imageFile) {
        apiService.standardScan(imageFile, userId, new CurrencyApiService.ApiCallback<StandardScanResponse>() {
            @Override
            public void onSuccess(StandardScanResponse result) {
                Log.d(TAG, "Scan SUCCESS: " + (result != null ? result.getScanId() : "null"));
                // Save to Firebase
                if (result != null && result.getScanId() != null) {
                    saveScanToFirebase(result);
                }
                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    binding.scanButton.setEnabled(true);
                    navigateToPostScan(result);
                });
            }

            @Override
            public void onFailure(String errorMessage, @NonNull Exception e) {
                Log.e(TAG, "Upload scan failed: " + errorMessage, e);
                // Save failed attempt to scan history too
                saveFailedScanToFirebase(errorMessage);
                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    binding.scanButton.setEnabled(true);
                    showScanError(errorMessage);
                });
            }
        });
    }

    private void saveScanToFirebase(StandardScanResponse result) {
        // Cache full result for View Report
        saveFullScanResult(result.getScanId(), result);
        // Also save locally for offline access
        String status = result.getAuthenticity() != null ? result.getAuthenticity().getStatus() : "Unknown";
        String json = "{\"id\":\"" + result.getScanId() + "\",\"userId\":\"" + userId + "\"," +
                "\"imageUrl\":\"" + (result.getAnnotatedImageUrl() != null ? result.getAnnotatedImageUrl() : "") + "\"," +
                "\"model\":\"" + (result.getAnalysisType() != null ? result.getAnalysisType() : "standard_scan") + "\"," +
                "\"status\":\"" + status + "\",\"date\":\"" + result.getTimestamp() + "\"}";
        saveToLocalCache(result.getScanId(), json);

        try {
            // Save scan data to Firebase
            fbUtils.saveAllScanData(fbUtils.STANDARD_SCAN_PATH, userId, result.getScanId(),
                    new FBInterface.OnScanReportDataSaveCallBack() {
                        @Override
                        public void onScanReportDataSaveSuccess() {
                            ScanReport scanReport = new ScanReport(
                                    result.getScanId(), userId,
                                    result.getAnnotatedImageUrl(),
                                    result.getAnalysisType(),
                                    result.getAuthenticity() != null ? result.getAuthenticity().getStatus() : "Unknown",
                                    result.getTimestamp()
                            );
                            fbUtils.saveScanReport(fbUtils.SCAN_REPORT_PATH, userId, scanReport);
                            Log.d(TAG, "Scan report saved to Firebase");
                        }
                        @Override
                        public void onScanReportDataSaveFailure(Exception e) {
                            Log.e(TAG, "Failed to save scan data", e);
                        }
                    }, result);
        } catch (Exception e) {
            Log.e(TAG, "Error saving to Firebase", e);
        }
    }

    private void saveFailedScanToFirebase(String errorMessage) {
        try {
            String scanId = java.util.UUID.randomUUID().toString();
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            ScanReport failedReport = new ScanReport(scanId, userId, "", "upload_scan", "ERROR: " + errorMessage, timestamp);
            fbUtils.saveScanReport(fbUtils.SCAN_REPORT_PATH, userId, failedReport);
            Log.d(TAG, "Failed scan logged to Firebase");
        } catch (Exception e) {
            Log.e(TAG, "Could not save failed scan report", e);
        }
    }

    private void saveOnDeviceScanReport(StandardScanResponse result) {
        String scanId = result.getScanId();
        String status = "UNKNOWN";
        String confidence = "";
        String denomination = result.getDenomination() != null ? result.getDenomination() : "";
        String features = result.getFeatureSummary() != null ? result.getFeatureSummary() : "";
        double coverage = result.getCoveragePercentage();
        if (result.getAuthenticity() != null) {
            status = result.getAuthenticity().getStatus();
            confidence = result.getAuthenticity().getConfidence() != null ? result.getAuthenticity().getConfidence() : "";
        }
        String timestamp = result.getTimestamp();

        // Save the scanned image locally for offline display
        String localImagePath = "";
        if (selectedImageUri != null) {
            try {
                File scanImagesDir = new File(getFilesDir(), "scan_images");
                if (!scanImagesDir.exists()) scanImagesDir.mkdirs();
                File localImage = new File(scanImagesDir, scanId + ".jpg");
                java.io.InputStream is = getContentResolver().openInputStream(selectedImageUri);
                if (is != null) {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(localImage);
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
                    fos.close();
                    is.close();
                    localImagePath = localImage.getAbsolutePath();
                    Log.d(TAG, "Scan image saved locally: " + localImagePath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save scan image locally: " + e.getMessage());
            }
        }

        String json = "{" +
                "\"id\":\"" + scanId + "\"," +
                "\"userId\":\"" + userId + "\"," +
                "\"imageUrl\":\"" + localImagePath.replace("\\", "\\\\") + "\"," +
                "\"model\":\"on_device_scan\"," +
                "\"status\":\"" + status + "\"," +
                "\"date\":\"" + timestamp + "\"," +
                "\"denomination\":\"" + denomination + "\"," +
                "\"confidence\":\"" + confidence + "\"," +
                "\"features\":\"" + features + "\"," +
                "\"coverage\":" + coverage +
                "}";

        // Always save locally first (works offline)
        saveToLocalCache(scanId, json);

        // Also save full scan result for the View Report screen
        saveFullScanResult(scanId, result);

        // Save to RTDB via the SA-authed cPanel proxy. The app's custom login can't
        // satisfy the auth!=null RTDB write rule, so a direct write is rejected. The
        // proxy (service account) writes into 'Standard Scan' — the node the admin
        // Scan Reports page reads — in the camelCase shape it expects, attributed to
        // the real user. Best-effort: local cache already holds it for offline display.
        final boolean genuine = result.getAuthenticity() != null && result.getAuthenticity().isGenuine();
        final int detected = result.detectedFeaturesCount;
        final int total = result.totalExpectedFeatures;
        final boolean highDenom = result.isHighDenomination;
        final String fStatus = status, fConf = confidence, fDenom = denomination,
                fFeatures = features, fTs = timestamp, fId = scanId;
        new Thread(() -> {
            try {
                String record = "{" +
                        "\"scanId\":\"" + fId + "\"," +
                        "\"userId\":\"" + userId + "\"," +
                        "\"type\":\"Standard\"," +
                        "\"timestamp\":\"" + fTs + "\"," +
                        "\"denomination\":\"" + fDenom + "\"," +
                        "\"authenticity\":\"" + fStatus + "\"," +
                        "\"isGenuine\":" + genuine + "," +
                        "\"confidence\":\"" + fConf + "\"," +
                        "\"coveragePercentage\":" + coverage + "," +
                        "\"detectedFeaturesCount\":" + detected + "," +
                        "\"totalExpectedFeatures\":" + total + "," +
                        "\"featuresDetected\":\"" + fFeatures.replace("\"", "") + "\"," +
                        "\"isHighDenomination\":" + highDenom + "," +
                        "\"annotatedImageUrl\":\"\"," +
                        "\"logicVersion\":\"offline-17.19\"," +
                        "\"processingMode\":\"on_device\"" +
                        "}";
                String reqBody = "{\"path\":\"Standard Scan/" + userId + "/" + fId + "\",\"data\":" + record + "}";
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build();
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("https://billsense.dev-environment.site/api/db/patch")
                        .post(okhttp3.RequestBody.create(reqBody, okhttp3.MediaType.parse("application/json")))
                        .build();
                okhttp3.Response response = client.newCall(request).execute();
                Log.d(TAG, "On-device scan saved via proxy: " + response.code());
                response.close();
                markLocalReportSynced(fId);
            } catch (Exception e) {
                Log.d(TAG, "Proxy save failed (kept local, will sync later): " + e.getMessage());
            }
        }).start();
    }

    private void saveFullScanResult(String scanId, StandardScanResponse result) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("scan_results_cache", MODE_PRIVATE);
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String resultJson = gson.toJson(result);
            prefs.edit().putString(scanId, resultJson).apply();
            Log.d(TAG, "Full scan result cached for: " + scanId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache scan result: " + e.getMessage());
        }
    }

    private void saveToLocalCache(String scanId, String json) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
            String existing = prefs.getString("reports", "{}");
            org.json.JSONObject reports = new org.json.JSONObject(existing);
            reports.put(scanId, new org.json.JSONObject(json));
            prefs.edit().putString("reports", reports.toString()).apply();
            Log.d(TAG, "Scan report saved to local cache: " + scanId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save to local cache: " + e.getMessage());
        }
    }

    private void markLocalReportSynced(String scanId) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
            String existing = prefs.getString("reports", "{}");
            org.json.JSONObject reports = new org.json.JSONObject(existing);
            if (reports.has(scanId)) {
                org.json.JSONObject report = reports.getJSONObject(scanId);
                report.put("synced", true);
                reports.put(scanId, report);
                prefs.edit().putString("reports", reports.toString()).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to mark synced: " + e.getMessage());
        }
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private void showScanError(String errorMessage) {
        binding.resultsLayout.setVisibility(View.VISIBLE);

        if (errorMessage.contains("500") || errorMessage.contains("Scan failed")) {
            binding.authenticityResultText.setText("SERVER WARMING UP");
            binding.authenticityResultText.setTextColor(Color.parseColor("#FF9800"));
            binding.confidenceResultText.setText("The ML models are loading on the server. This usually takes 1-2 minutes on first request.");
            binding.detectedFeaturesCountText.setText("Please try again in a moment.");
            binding.denominationResultText.setText("N/A");
            binding.coverageText.setText("");
            binding.featuresSummaryText.setText("");
        } else {
            binding.authenticityResultText.setText("SCAN FAILED");
            binding.authenticityResultText.setTextColor(Color.parseColor("#C62828"));
            binding.confidenceResultText.setText("Error: " + errorMessage);
            binding.detectedFeaturesCountText.setText("Check your internet connection and try again.");
            binding.denominationResultText.setText("N/A");
            binding.coverageText.setText("");
            binding.featuresSummaryText.setText("");
        }

        binding.annotatedImageCard.setVisibility(View.GONE);
        binding.qualityMetricsCard.setVisibility(View.GONE);
        binding.scanIdText.setText("");
        binding.timestampText.setText("");
        binding.processingTimeText.setText("");
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void populateResultsUi(StandardScanResponse response) {
        if (response == null) return;

        binding.resultsLayout.setVisibility(View.VISIBLE);

        StandardScanResponse.Authenticity auth = response.getAuthenticity();
        if (auth != null) {
            binding.authenticityResultText.setText(auth.getStatus());
            binding.authenticityResultText.setTextColor(
                    auth.isGenuine() ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828")
            );
            binding.confidenceResultText.setText("Confidence: " + auth.getConfidence());
            binding.detectedFeaturesCountText.setText(
                    String.format("Detected Features: %d of %d",
                            auth.getDetectedFeaturesCount(), auth.getTotalExpectedFeatures()));
            if (auth.getReasons() != null) {
                binding.reasonsText.setText("Reasons: " + String.join("\n", auth.getReasons()));
            }
        }

        binding.denominationResultText.setText(response.getDenomination());
        binding.coverageText.setText(String.format("Overall Coverage: %.1f%%", response.getCoveragePercentage()));

        if (response.getFeatureSummary() != null) {
            binding.featuresSummaryText.setText("Summary: " + response.getFeatureSummary());
        }

        // Annotated Image
        if (response.getAnnotatedImageUrl() != null && !response.getAnnotatedImageUrl().isEmpty()) {
            binding.annotatedImageCard.setVisibility(View.VISIBLE);
            Glide.with(this).load(response.getAnnotatedImageUrl()).into(binding.annotatedImageView);
        } else {
            binding.annotatedImageCard.setVisibility(View.GONE);
        }

        // Quality Metrics
        StandardScanResponse.QualityMetrics metrics = response.getQualityMetrics();
        if (metrics != null) {
            binding.qualityMetricsCard.setVisibility(View.VISIBLE);
            binding.qualityStatusText.setText(
                    String.format("Status: %s (%.1f)", metrics.getQualityStatus(), metrics.getOverallQuality()));
            binding.sharpnessText.setText(String.format("Sharpness: %.1f", metrics.getSharpness()));
            binding.brightnessText.setText(String.format("Brightness: %.1f", metrics.getBrightness()));
            binding.contrastText.setText(String.format("Contrast: %.1f", metrics.getContrast()));
        } else {
            binding.qualityMetricsCard.setVisibility(View.GONE);
        }

        // Metadata
        binding.scanIdText.setText("Scan ID: " + response.getScanId());
        binding.timestampText.setText("Timestamp: " + response.getTimestamp());
        binding.processingTimeText.setText(String.format("Processing Time: %.1fs", response.getProcessingTime()));
    }

    /**
     * Navigate to StandardPostScanActivity with the scan result.
     */
    private void navigateToPostScan(StandardScanResponse response) {
        if (response == null) return;
        try {
            Gson gson = new Gson();
            String resultJson = gson.toJson(response);

            Intent intent = new Intent(this, StandardPostScanActivity.class);
            intent.putExtra("scan_result_json", resultJson);
            if (selectedImageUri != null) {
                intent.putExtra("scanned_image_uri", selectedImageUri.toString());
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate to post-scan", e);
            Toast.makeText(this, "Error showing results: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (securitycfInference != null) {
            securitycfInference.close();
        }
        if (denominationInference != null) {
            denominationInference.close();
        }
    }
}
