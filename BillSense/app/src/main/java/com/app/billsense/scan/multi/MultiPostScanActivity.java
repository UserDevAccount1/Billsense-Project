package com.app.billsense.scan.multi;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.app.billsense.R;
import com.app.billsense.databinding.ActivityMultiPostScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.ScanReport;
import com.app.billsense.scan.pojo.CurrencyApiService;
import com.app.billsense.scan.pojo.MultiScanResponse;
import com.app.billsense.utils.DialogUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.ProgressDialogUtil;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MultiPostScanActivity extends AppCompatActivity {
    private ActivityMultiPostScanBinding binding;
    private CurrencyApiService apiService;
    private MultiScanResponse multiScanResponse;
    private String userId;
    private FBUtils fbUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMultiPostScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        // --- NEW TOOLBAR SETUP ---
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.multi_scan_result));
        }
        binding.toolbar.setNavigationOnClickListener(view -> {
            finish(); // Go back to the previous activity
        });
        // --- END NEW TOOLBAR SETUP ---

        apiService = new CurrencyApiService();
        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();

        List<String> imagePaths = getIntent().getStringArrayListExtra("captured_image_paths");

        if (imagePaths != null && !imagePaths.isEmpty()) {
            List<File> imageFiles = imagePaths.stream().map(File::new).collect(Collectors.toList());
            performMultiScan(imageFiles, userId);
        } else {
            Toast.makeText(this, "No images found to scan.", Toast.LENGTH_LONG).show();
            finish();
        }

        setUpListeners();
    }

    private void setUpListeners() {
        binding.annotatedImageView.setOnClickListener(view -> {
            if (multiScanResponse != null){
                if (multiScanResponse.getAnnotatedImageUrl() != null && !multiScanResponse.getAnnotatedImageUrl().isEmpty()) {
                    DialogUtils.displayFullImageDialog(MultiPostScanActivity.this, "Annotated Image", multiScanResponse.getAnnotatedImageUrl(), null);
                }
            }
        });
    }

    private void performMultiScan(List<File> imageFiles, String userId) {
        ProgressDialogUtil.showProgressDialog(this);
        apiService.multiScan(imageFiles, userId, new CurrencyApiService.ApiCallback<MultiScanResponse>() {
            @Override
            public void onSuccess(MultiScanResponse result) {
                multiScanResponse = result;
                if (result != null && result.getScanId() != null){
                    fbUtils.saveAllScanData(fbUtils.MULTI_SCAN_PATH, userId, result.getScanId(),
                            new FBInterface.OnScanReportDataSaveCallBack() {
                                @Override
                                public void onScanReportDataSaveSuccess() {
                                    ScanReport scanReport = new ScanReport(
                                            result.getScanId(),
                                            userId,
                                            result.getAnnotatedImageUrl(),
                                            result.getAnalysisType(),
                                            result.getAuthenticity(),
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
                    Toast.makeText(MultiPostScanActivity.this, "Scan Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void populateUi(MultiScanResponse response) {
        // --- Overall Result Card ---
        String mStatus = response.getAuthenticity() != null ? response.getAuthenticity() : "UNKNOWN";
        String msu = mStatus.toUpperCase(java.util.Locale.US);
        int mStatusColor = msu.contains("COUNTERFEIT") ? Color.parseColor("#C62828")
                : (msu.contains("GENUINE") && !msu.contains("LIKELY")) ? Color.parseColor("#2E7D32")
                : Color.parseColor("#FF8F00");
        binding.authenticityResultText.setText(mStatus);
        binding.authenticityResultText.setTextColor(mStatusColor);
        binding.denominationResultText.setText(response.getDenomination());

        // Calibrated score bar (real measurement)
        int mScore = response.getAuthenticityScore();
        binding.confidenceResultText.setText(String.format("Authenticity Score: %d/100 (%s)", mScore, response.getConfidence()));
        binding.confidenceBar.setProgress(mScore);
        int mBar = mScore >= 75 ? Color.parseColor("#2E7D32") : (mScore >= 25 ? Color.parseColor("#FF8F00") : Color.parseColor("#C62828"));
        binding.confidenceBar.getProgressDrawable().setColorFilter(mBar, android.graphics.PorterDuff.Mode.SRC_IN);

        // OVI/OVD colour-shift (the strongest optical authenticator, measured across angles)
        MultiScanResponse.OviColorShift ovi = response.getOviColorShift();
        if (ovi != null && ovi.isShiftDetected()) {
            binding.oviShiftText.setText(String.format("✅ Optically variable ink shifts colour across angles (Δhue %.0f)", ovi.getDelta()));
            binding.oviShiftText.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            binding.oviShiftText.setText("OVI/OVD colour-shift: not measured — need the OVI region visible in ≥2 angles.");
            binding.oviShiftText.setTextColor(Color.parseColor("#9E9E9E"));
        }

        // --- Annotated Image Card ---
        if (response.getAnnotatedImageUrl() != null && !response.getAnnotatedImageUrl().isEmpty()) {
            binding.annotatedImageCard.setVisibility(View.VISIBLE);
            Glide.with(this).load(response.getAnnotatedImageUrl()).into(binding.annotatedImageView);
        } else {
            binding.annotatedImageCard.setVisibility(View.GONE);
        }

        // --- Analysis Details Card ---
        binding.detectedFeaturesCountText.setText(String.format("Detected Features: %d of %d", response.getDetectedFeaturesCount(), response.getTotalExpectedFeatures()));
        binding.coverageText.setText(String.format("Overall Coverage: %.1f%%", response.getCoveragePercentage()));
        if (response.getReasons() != null && !response.getReasons().isEmpty()) {
            binding.reasonsText.setText("Reasons: " + String.join(", ", response.getReasons()));
        } else {
            binding.reasonsText.setVisibility(View.GONE);
        }
        if (response.getFeaturesDetected() != null && !response.getFeaturesDetected().isEmpty()) {
            binding.featuresListText.setText("Features Found: " + String.join(", ", response.getFeaturesDetected()));
        } else {
            binding.featuresListText.setText("Features Found: None");
        }

        // --- Scan Metadata Card ---
        binding.scanIdText.setText("Scan ID: " + response.getScanId());
        binding.timestampText.setText("Timestamp: " + response.getTimestamp());
        binding.processingTimeText.setText(String.format("Processing Time: %.1fs", response.getProcessingTime()));
        binding.anglesProcessedText.setText("Angles Processed: " + response.getAnglesProcessed());
        binding.statusMessageText.setText(String.format("Status: %s - %s", response.getStatus(), response.getMessage()));
        binding.highDenominationText.setText("High Denomination: " + (response.isHighDenomination() ? "Yes" : "No"));
        binding.storageIdText.setText("Storage ID: " + response.getStorageId());

        // --- Angle Breakdown Section ---
        if (response.getAngleResults() != null && !response.getAngleResults().isEmpty()) {
            binding.angleResultsContainer.removeAllViews();
            Map<Integer, String> angleImageMap = response.getAngleImages().stream()
                    .collect(Collectors.toMap(MultiScanResponse.AngleImage::getAngleNumber, MultiScanResponse.AngleImage::getImageUrl));

            for (MultiScanResponse.AngleResult angle : response.getAngleResults()) {
                MaterialCardView angleCard = (MaterialCardView) LayoutInflater.from(this)
                        .inflate(R.layout.card_angle_result, binding.angleResultsContainer, false);

                ImageView angleImageView = angleCard.findViewById(R.id.angle_image_view);
                TextView angleTitle = angleCard.findViewById(R.id.angle_title);
                TextView angleDetails = angleCard.findViewById(R.id.angle_details);
                TextView angleFeaturesList = angleCard.findViewById(R.id.angle_features_list);
                TextView angleQualityMetrics = angleCard.findViewById(R.id.angle_quality_metrics_text);

                angleTitle.setText(String.format("Angle %d: %s", angle.getAngleNumber(), angle.getDenomination()));
                angleDetails.setText(String.format("Features: %d | Coverage: %.1f%% | Confidence: %s",
                        angle.getFeatureCount(), angle.getCoveragePercentage(), angle.getConfidence()));

                if (angle.getFeaturesDetected() != null && !angle.getFeaturesDetected().isEmpty()) {
                    angleFeaturesList.setText("Detected: " + String.join(", ", angle.getFeaturesDetected()));
                } else {
                    angleFeaturesList.setText("Detected: None");
                }

                // Populate Quality Metrics for the angle
                MultiScanResponse.QualityMetrics metrics = angle.getQualityMetrics();
                if (metrics != null) {
                    angleQualityMetrics.setText(String.format("Quality: %s (S:%.1f, B:%.1f, C:%.1f)",
                            metrics.getQualityStatus(), metrics.getSharpness(), metrics.getBrightness(), metrics.getContrast()));
                } else {
                    angleQualityMetrics.setVisibility(View.GONE);
                }

                angleCard.setCardBackgroundColor(angle.isGenuine() ? Color.parseColor("#E8F5E9") : Color.parseColor("#FFEBEE"));

                String imageUrl = angleImageMap.get(angle.getAngleNumber());
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    angleImageView.setVisibility(View.VISIBLE);
                    Glide.with(this).load(imageUrl).into(angleImageView);
                }
                angleImageView.setOnClickListener(view -> {
                    DialogUtils.displayFullImageDialog(MultiPostScanActivity.this, "Angle Image", imageUrl, null);
                });
                binding.angleResultsContainer.addView(angleCard);
            }
        }
    }
}
