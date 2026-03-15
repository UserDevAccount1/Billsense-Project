package com.app.billsense.scan.standard;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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

import java.io.File;

public class StandardPostScanActivity extends AppCompatActivity {

    private ActivityStandardPostScanBinding binding;
    private CurrencyApiService apiService;
    private String userId;
    private FBUtils fbUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStandardPostScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        apiService = new CurrencyApiService();
        userId = PrefManager.getInstance().getUserId();
        fbUtils = new FBUtils();

        String imageUriString = getIntent().getStringExtra("captured_image_uri");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            File imageFile = Utils.uriToFile(this, imageUri); // Using a helper to get the File

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

    private void performStandardScan(File imageFile, String userId) {
        ProgressDialogUtil.showProgressDialog(this);
        apiService.standardScan(imageFile, userId, new CurrencyApiService.ApiCallback<StandardScanResponse>() {
            @Override
            public void onSuccess(StandardScanResponse result) {
                if (result != null && result.getScanId() != null){
                    fbUtils.saveAllScanData(fbUtils.STANDARD_SCAN_PATH, userId, result.getScanId(),
                            new FBInterface.OnScanReportDataSaveCallBack() {
                                @Override
                                public void onScanReportDataSaveSuccess() {
                                    ScanReport scanReport = new ScanReport(
                                            result.getScanId(),
                                            userId,
                                            result.getAnnotatedImageUrl(),
                                            result.getAnalysisType(),
                                            result.getAuthenticity().getStatus(),
                                            result.getTimestamp()
                                    );

                                    fbUtils.saveScanReport(fbUtils.SCAN_REPORT_PATH, userId, scanReport);
                                }

                                @Override
                                public void onScanReportDataSaveFailure(Exception e) {

                                }
                            }, result);
                }
                runOnUiThread(() -> {
                    ProgressDialogUtil.hideProgressDialog();
                    populateUi(result);
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

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void populateUi(StandardScanResponse response) {
        if (response == null) return;

        StandardScanResponse.Authenticity auth = response.getAuthenticity();
        if (auth != null) {
            binding.authenticityResultText.setText(auth.getStatus());
            binding.authenticityResultText.setTextColor(
                    auth.isGenuine() ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828")
            );
            binding.confidenceResultText.setText("Confidence: " + auth.getConfidence());
            binding.detectedFeaturesCountText.setText(String.format("Detected Features: %d of %d", auth.getDetectedFeaturesCount(), auth.getTotalExpectedFeatures()));
            binding.reasonsText.setText("Reasons: " + String.join("\n", auth.getReasons()));
        }

        binding.denominationResultText.setText(response.getDenomination());
        binding.coverageText.setText(String.format("Overall Coverage: %.1f%%", response.getCoveragePercentage()));
        binding.featuresSummaryText.setText("Summary: " + response.getFeatureSummary());

        // Annotated Image Card
        if (response.getAnnotatedImageUrl() != null && !response.getAnnotatedImageUrl().isEmpty()) {
            binding.annotatedImageCard.setVisibility(View.VISIBLE);
            Glide.with(this).load(response.getAnnotatedImageUrl()).into(binding.annotatedImageView);
        } else {
            binding.annotatedImageCard.setVisibility(View.GONE);
        }

        // Quality Metrics Card
        StandardScanResponse.QualityMetrics metrics = response.getQualityMetrics();
        if (metrics != null) {
            binding.qualityMetricsCard.setVisibility(View.VISIBLE);
            binding.qualityStatusText.setText(String.format("Status: %s (%.1f)", metrics.getQualityStatus(), metrics.getOverallQuality()));
            binding.sharpnessText.setText(String.format("Sharpness: %.1f", metrics.getSharpness()));
            binding.brightnessText.setText(String.format("Brightness: %.1f", metrics.getBrightness()));
            binding.contrastText.setText(String.format("Contrast: %.1f", metrics.getContrast()));
        } else {
            binding.qualityMetricsCard.setVisibility(View.GONE);
        }

        // Metadata Card
        binding.scanIdText.setText("Scan ID: " + response.getScanId());
        binding.timestampText.setText("Timestamp: " + response.getTimestamp());
        binding.processingTimeText.setText(String.format("Processing Time: %.1fs", response.getProcessingTime()));
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}
