package com.app.billsense.scan.video;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityVideoPostScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.ScanReport;
import com.app.billsense.scan.pojo.CurrencyApiService;
import com.app.billsense.scan.pojo.VideoScanResponse;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.ProgressDialogUtil;
import com.app.billsense.utils.Utils;
import com.bumptech.glide.Glide;

import java.io.File;

public class VideoPostScanActivity extends AppCompatActivity {

    private ActivityVideoPostScanBinding binding;
    private CurrencyApiService apiService;
    private String userId;
    private FBUtils fbUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPostScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        apiService = new CurrencyApiService();
        userId = PrefManager.getInstance().getUserId();
        fbUtils = new FBUtils();

        String videoUriString = getIntent().getStringExtra("captured_video_uri");
        if (videoUriString != null) {
            Uri videoUri = Uri.parse(videoUriString);
            File videoFile = Utils.uriToFile(this, videoUri);

            if (videoFile != null && videoFile.exists()) {
                performVideoScan(videoFile, userId);
            } else {
                showErrorAndFinish("Failed to access captured video file.");
            }
        } else {
            showErrorAndFinish("No video URI provided.");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.video_scan_result);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void performVideoScan(File videoFile, String userId) {
        ProgressDialogUtil.showProgressDialog(this);
        apiService.videoScan(videoFile, userId, new CurrencyApiService.ApiCallback<VideoScanResponse>() {
            @Override
            public void onSuccess(VideoScanResponse result) {
                if (result != null && result.getScanId() != null){
                    fbUtils.saveAllScanData(fbUtils.VIDEO_SCAN_PATH, userId, result.getScanId(),
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
                    showErrorAndFinish("Scan Failed: " + errorMessage);
                });
            }
        });
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void populateUi(VideoScanResponse response) {
        if (response == null) return;

        // Overall Result Card
        VideoScanResponse.Authenticity auth = response.getAuthenticity();
        if (auth != null) {
            binding.authenticityResultText.setText(auth.getStatus());
            binding.authenticityResultText.setTextColor(
                    auth.isGenuine() ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828")
            );
            binding.confidenceResultText.setText("Confidence: " + auth.getConfidence());
            binding.reasonsText.setText("Reasons: " + String.join("\n", auth.getReasons()));
        }

        binding.denominationResultText.setText(response.getDenomination());

        // Annotated Image Card
        if (response.getAnnotatedImageUrl() != null && !response.getAnnotatedImageUrl().isEmpty()) {
            binding.annotatedImageCard.setVisibility(View.VISIBLE);
            Glide.with(this).load(response.getAnnotatedImageUrl()).into(binding.annotatedImageView);
        } else {
            binding.annotatedImageCard.setVisibility(View.GONE);
        }

        // Analysis Details Card
        binding.featureSummaryText.setText("Feature Summary: " + response.getFeatureSummary());
        binding.coverageText.setText(String.format("Overall Coverage: %.1f%%", response.getCoveragePercentage()));

        // Metadata Card
        binding.scanIdText.setText("Scan ID: " + response.getScanId());
        binding.timestampText.setText("Timestamp: " + response.getTimestamp());
        binding.processingTimeText.setText(String.format("Processing Time: %.1fs", response.getProcessingTime()));
        binding.framesProcessedText.setText("Frames Processed: " + response.getFramesProcessed());
        binding.modelInfoText.setText("Model: " + response.getModelInfo());

        if (response.getStorageId() != null && !response.getStorageId().isEmpty()) {
            binding.storageIdText.setText("Storage ID: " + response.getStorageId());
            binding.storageIdText.setVisibility(View.VISIBLE);
        } else {
            binding.storageIdText.setVisibility(View.GONE);
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}
