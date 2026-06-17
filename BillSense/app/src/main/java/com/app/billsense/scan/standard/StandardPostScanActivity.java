package com.app.billsense.scan.standard;

import android.annotation.SuppressLint;
import android.content.Intent;

import com.app.billsense.activities.HomeActivity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityStandardPostScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.ScanReport;
import com.app.billsense.scan.pojo.CurrencyApiService;
import com.app.billsense.scan.pojo.StandardScanResponse;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.ProgressDialogUtil;
import com.app.billsense.utils.Utils;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;

import java.io.File;
import java.util.Locale;

public class StandardPostScanActivity extends AppCompatActivity {

    private static final String TAG = "StandardPostScan";
    private ActivityStandardPostScanBinding binding;
    private CurrencyApiService apiService;
    private String userId;
    private FBUtils fbUtils;
    private StandardScanResponse currentResponse;
    private String scannedImageUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStandardPostScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        apiService = new CurrencyApiService();
        userId = PrefManager.getInstance().getUserId();
        fbUtils = new FBUtils();

        setupActionButtons();

        // Mode 2: Pre-computed scan result passed as JSON
        String scanResultJson = getIntent().getStringExtra("scan_result_json");
        scannedImageUriString = getIntent().getStringExtra("scanned_image_uri");

        if (scanResultJson != null && !scanResultJson.isEmpty()) {
            // Parse and display directly - no API call
            try {
                Gson gson = new Gson();
                currentResponse = gson.fromJson(scanResultJson, StandardScanResponse.class);
                if (currentResponse != null) {
                    populateResultsUI(currentResponse);
                    binding.mainContentLayout.setVisibility(View.VISIBLE);
                } else {
                    showError("Failed to parse scan results.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse scan_result_json", e);
                showError("Failed to parse scan results: " + e.getMessage());
            }
            return;
        }

        // Mode 1: Captured image URI - send to API
        String imageUriString = getIntent().getStringExtra("captured_image_uri");
        if (imageUriString != null) {
            scannedImageUriString = imageUriString;
            Uri imageUri = Uri.parse(imageUriString);
            File imageFile = Utils.uriToFile(this, imageUri);

            if (imageFile != null && imageFile.exists()) {
                performStandardScan(imageFile, userId);
            } else {
                showError("Failed to access captured image file.");
            }
        } else {
            showError("No image URI provided.");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.standard_scan_result);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupActionButtons() {
        binding.retakeBtn.setOnClickListener(v -> {
            finish(); // Go back to scan activity
        });

        binding.saveBtn.setOnClickListener(v -> {
            if (currentResponse != null) {
                saveResultToCache(currentResponse);
                Toast.makeText(this, "Report saved!", Toast.LENGTH_SHORT).show();
                // Navigate back to Home
                Intent homeIntent = new Intent(this, HomeActivity.class);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(homeIntent);
                finish();
            }
        });

        binding.shareBtn.setOnClickListener(v -> {
            if (currentResponse != null) {
                shareReport(currentResponse);
            }
        });
    }

    private void performStandardScan(File imageFile, String userId) {
        ProgressDialogUtil.showProgressDialog(this);
        apiService.standardScan(imageFile, userId, new CurrencyApiService.ApiCallback<StandardScanResponse>() {
            @Override
            public void onSuccess(StandardScanResponse result) {
                currentResponse = result;
                if (result != null && result.getScanId() != null) {
                    saveScanToFirebase(result);
                    saveResultToCache(result);
                }
                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    populateResultsUI(result);
                    binding.mainContentLayout.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onFailure(String errorMessage, @NonNull Exception e) {
                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    showError("Scan Failed: " + errorMessage);
                });
            }
        });
    }

    private void saveScanToFirebase(StandardScanResponse result) {
        try {
            fbUtils.saveAllScanData(fbUtils.STANDARD_SCAN_PATH, userId, result.getScanId(),
                    new FBInterface.OnScanReportDataSaveCallBack() {
                        @Override
                        public void onScanReportDataSaveSuccess() {
                            ScanReport scanReport = new ScanReport(
                                    result.getScanId(),
                                    userId,
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
                            Log.e(TAG, "Failed to save scan data to Firebase", e);
                        }
                    }, result);
        } catch (Exception e) {
            Log.e(TAG, "Error saving to Firebase", e);
        }
    }

    private void saveResultToCache(StandardScanResponse result) {
        if (result == null || result.getScanId() == null) return;
        try {
            // Save the scanned image locally
            String localImagePath = "";
            if (scannedImageUriString != null && !scannedImageUriString.isEmpty()) {
                try {
                    android.net.Uri imageUri = android.net.Uri.parse(scannedImageUriString);
                    java.io.File scanImagesDir = new java.io.File(getFilesDir(), "scan_images");
                    if (!scanImagesDir.exists()) scanImagesDir.mkdirs();
                    java.io.File localImage = new java.io.File(scanImagesDir, result.getScanId() + ".jpg");
                    if (!localImage.exists()) {
                        java.io.InputStream is = getContentResolver().openInputStream(imageUri);
                        if (is != null) {
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(localImage);
                            byte[] buf = new byte[4096];
                            int read;
                            while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
                            fos.close();
                            is.close();
                            localImagePath = localImage.getAbsolutePath();
                        }
                    } else {
                        localImagePath = localImage.getAbsolutePath();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save scanned image: " + e.getMessage());
                }
            }

            // Use local image path if available, otherwise use annotated URL
            String imageUrl = !localImagePath.isEmpty() ? localImagePath :
                    (result.getAnnotatedImageUrl() != null ? result.getAnnotatedImageUrl() : "");

            // Save full result to scan_results_cache
            SharedPreferences cachePrefs = getSharedPreferences("scan_results_cache", MODE_PRIVATE);
            Gson gson = new Gson();
            String resultJson = gson.toJson(result);
            cachePrefs.edit().putString(result.getScanId(), resultJson).apply();

            // Save summary to offline_scan_reports
            String status = result.getAuthenticity() != null ? result.getAuthenticity().getStatus() : "Unknown";
            String json = "{\"id\":\"" + result.getScanId() + "\",\"userId\":\"" + userId + "\"," +
                    "\"imageUrl\":\"" + imageUrl.replace("\\", "\\\\") + "\"," +
                    "\"model\":\"" + (result.getAnalysisType() != null ? result.getAnalysisType() : "standard_scan") + "\"," +
                    "\"status\":\"" + status + "\",\"date\":\"" + result.getTimestamp() + "\"}";

            SharedPreferences reportPrefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
            String existing = reportPrefs.getString("reports", "{}");
            org.json.JSONObject reports = new org.json.JSONObject(existing);
            reports.put(result.getScanId(), new org.json.JSONObject(json));
            reportPrefs.edit().putString("reports", reports.toString()).apply();

            Log.d(TAG, "Scan result cached with image: " + imageUrl);
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache scan result", e);
        }
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void populateResultsUI(StandardScanResponse response) {
        if (response == null) return;

        StandardScanResponse.Authenticity auth = response.getAuthenticity();

        // 1. Status Badge
        String statusText = "UNKNOWN";
        int statusColor = Color.GRAY;
        if (auth != null) {
            statusText = auth.getStatus() != null ? auth.getStatus() : "UNKNOWN";
            String su = statusText.toUpperCase(Locale.US);
            if (su.contains("COUNTERFEIT")) {
                statusColor = Color.parseColor("#C62828"); // red
            } else if (su.contains("GENUINE") && !su.contains("LIKELY")) {
                statusColor = Color.parseColor("#2E7D32"); // green
            } else {
                statusColor = Color.parseColor("#FF8F00"); // amber: LIKELY GENUINE / NEEDS_RESCAN / UNKNOWN
            }
        }
        binding.authenticityStatusText.setText(statusText);
        binding.authenticityStatusText.setTextColor(statusColor);

        // 2. Confidence Score — calibrated 0-100 (real measurement from the server)
        int score = auth != null ? auth.getAuthenticityScore() : 0;
        String confLabel = (auth != null && auth.getConfidence() != null) ? auth.getConfidence() : "N/A";
        binding.confidenceText.setText(String.format(Locale.US, "Authenticity Score: %d/100 (%s)", score, confLabel));
        binding.confidenceBar.setProgress(score);
        int barColor = score >= 75 ? Color.parseColor("#2E7D32")
                : (score >= 25 ? Color.parseColor("#FF8F00") : Color.parseColor("#C62828"));
        binding.confidenceBar.getProgressDrawable().setColorFilter(
                barColor, android.graphics.PorterDuff.Mode.SRC_IN);

        // 3. Bill Info
        String denom = response.getDenomination();
        if (denom != null && !denom.isEmpty()) {
            binding.denominationText.setText("Detected: " + denom);
        } else {
            binding.denominationText.setText("Denomination: N/A");
        }

        // 4. Security Features Checklist (with measured placement %)
        binding.securityFeaturesLayout.removeAllViews();
        StandardScanResponse.SecurityFeatures sf = response.getSecurityFeatures();
        java.util.Map<String, StandardScanResponse.FeatureGeometry> geom =
                auth != null ? auth.getFeatureGeometry() : null;
        if (sf != null) {
            binding.securityFeaturesCard.setVisibility(View.VISIBLE);
            addFeatureRow("Watermark", sf.hasWatermark(), geom, "watermark");
            addFeatureRow("Security Thread", sf.hasSecurityThread(), geom, "security_thread");
            addFeatureRow("Serial Number", sf.hasSerialNumber(), geom, "serial_number");
            addFeatureRow("See-through Mark", sf.hasSeeThroughMark(), geom, "see_through_mark");
            addFeatureRow("Concealed Value", sf.hasConcealedValue(), geom, "concealed_value");
            addFeatureRow("OVD Patch", sf.hasOvd(), geom, "ovd");
            addFeatureRow("Enhanced Value Panel", sf.hasEnhancedValuePanel(), geom, "enhanced_value_panel");
            addFeatureRow("Optically Variable Ink", sf.hasOpticallyVariableInk(), geom, "optically_variable_ink");
        } else {
            binding.securityFeaturesCard.setVisibility(View.GONE);
        }

        // 5. Coverage
        int detectedCount = auth != null ? auth.getDetectedFeaturesCount() : 0;
        int totalCount = auth != null ? auth.getTotalExpectedFeatures() : 0;
        double coverage = response.getCoveragePercentage();
        if (totalCount > 0) {
            binding.coverageText.setText(String.format(Locale.US,
                    "Feature Coverage: %.1f%% (%d/%d features detected)",
                    coverage, detectedCount, totalCount));
        } else {
            binding.coverageText.setText(String.format(Locale.US, "Overall Coverage: %.1f%%", coverage));
        }

        // 6. Recommendation
        String recommendation = buildRecommendation(auth, statusText);
        binding.recommendationText.setText(recommendation);

        // 7. Scanned Image
        if (scannedImageUriString != null && !scannedImageUriString.isEmpty()) {
            binding.scannedImageCard.setVisibility(View.VISIBLE);
            try {
                if (scannedImageUriString.startsWith("/")) {
                    Glide.with(this).load(new File(scannedImageUriString)).into(binding.scannedImageView);
                } else {
                    Glide.with(this).load(Uri.parse(scannedImageUriString)).into(binding.scannedImageView);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load scanned image", e);
                binding.scannedImageCard.setVisibility(View.GONE);
            }
        } else {
            binding.scannedImageCard.setVisibility(View.GONE);
        }

        // 8. Annotated Image
        if (response.getAnnotatedImageUrl() != null && !response.getAnnotatedImageUrl().isEmpty()) {
            binding.annotatedImageCard.setVisibility(View.VISIBLE);
            Glide.with(this).load(response.getAnnotatedImageUrl()).into(binding.annotatedImageView);
        } else {
            binding.annotatedImageCard.setVisibility(View.GONE);
        }

        // 9. Quality Metrics
        StandardScanResponse.QualityMetrics metrics = response.getQualityMetrics();
        if (metrics != null) {
            binding.qualityMetricsCard.setVisibility(View.VISIBLE);
            binding.qualityStatusText.setText(
                    String.format(Locale.US, "Status: %s (%.1f)", metrics.getQualityStatus(), metrics.getOverallQuality()));
            binding.sharpnessText.setText(String.format(Locale.US, "Sharpness: %.1f", metrics.getSharpness()));
            binding.brightnessText.setText(String.format(Locale.US, "Brightness: %.1f", metrics.getBrightness()));
            binding.contrastText.setText(String.format(Locale.US, "Contrast: %.1f", metrics.getContrast()));
        } else {
            binding.qualityMetricsCard.setVisibility(View.GONE);
        }

        // 10. Processing Info
        binding.scanIdText.setText("Scan ID: " + (response.getScanId() != null ? response.getScanId() : "N/A"));
        binding.timestampText.setText("Timestamp: " + (response.getTimestamp() != null ? response.getTimestamp() : "N/A"));
        binding.processingTimeText.setText(String.format(Locale.US, "Processing Time: %.1fs", response.getProcessingTime()));
        binding.modelInfoText.setText("Model: " + (response.getModelInfo() != null ? response.getModelInfo() : "N/A"));
        binding.analysisTypeText.setText("Analysis Type: " + (response.getAnalysisType() != null ? response.getAnalysisType() : "N/A"));
    }

    private void addFeatureRow(String featureName, boolean detected,
                               java.util.Map<String, StandardScanResponse.FeatureGeometry> geom, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 6, 0, 6);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        icon.setTextSize(16);
        icon.setText(detected ? "\u2705" : "\u274C");
        icon.setPadding(0, 0, 16, 0);
        row.addView(icon);

        // Append measured placement % when the server reported geometry for this feature
        String label = featureName;
        if (detected && geom != null && key != null && geom.get(key) != null
                && geom.get(key).getPositionScore() != null
                && geom.get(key).getPositionScore() >= 0.5) {
            int placed = (int) Math.round(geom.get(key).getPositionScore() * 100);
            label = featureName + "  \u00B7  " + placed + "% placed";
        }

        TextView name = new TextView(this);
        name.setTextSize(14);
        name.setText(label);
        name.setTextColor(detected ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        if (detected) {
            name.setTypeface(null, Typeface.BOLD);
        }
        row.addView(name);

        binding.securityFeaturesLayout.addView(row);
    }

    private String buildRecommendation(StandardScanResponse.Authenticity auth, String status) {
        if (auth == null) return "Unable to determine authenticity. Please try scanning again.";

        if (auth.isGenuine()) {
            return "This banknote appears to be GENUINE based on the detected security features. " +
                    "The bill passed the authenticity check with sufficient feature coverage.";
        } else if (status.toUpperCase(Locale.US).contains("LIKELY")) {
            return "This banknote is LIKELY GENUINE but some security features could not be verified. " +
                    "Consider rescanning under better lighting or verifying manually.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("WARNING: This banknote may be COUNTERFEIT. ");
            sb.append("Insufficient security features were detected. ");
            if (auth.getReasons() != null && !auth.getReasons().isEmpty()) {
                sb.append("Reasons: ").append(String.join("; ", auth.getReasons())).append(". ");
            }
            sb.append("Do NOT accept this bill. Report to authorities if suspicious.");
            return sb.toString();
        }
    }

    private void shareReport(StandardScanResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("BillSense Scan Report\n");
        sb.append("=====================\n\n");

        StandardScanResponse.Authenticity auth = response.getAuthenticity();
        sb.append("Status: ").append(auth != null ? auth.getStatus() : "Unknown").append("\n");
        sb.append("Denomination: ").append(response.getDenomination()).append("\n");
        sb.append("Confidence: ").append(auth != null ? auth.getConfidence() : "N/A").append("\n");
        sb.append("Coverage: ").append(String.format(Locale.US, "%.1f%%", response.getCoveragePercentage())).append("\n");
        sb.append("Scan ID: ").append(response.getScanId()).append("\n");
        sb.append("Timestamp: ").append(response.getTimestamp()).append("\n");
        sb.append("Processing Time: ").append(String.format(Locale.US, "%.1fs", response.getProcessingTime())).append("\n");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "BillSense Scan Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Report"));
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}
