package com.app.billsense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityViewScanReportBinding;
import com.app.billsense.fragments.MultiScanFragment;
import com.app.billsense.fragments.StandardScanFragment;
import com.app.billsense.fragments.VideoScanFragment;
import com.app.billsense.scan.pojo.StandardScanResponse;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.io.File;
import java.util.Locale;

public class ViewScanReportActivity extends AppCompatActivity {
    private static final String TAG = "ViewScanReportActivity";
    private ActivityViewScanReportBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewScanReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(view -> finish());

        // Get all data from the intent
        Intent intent = getIntent();
        String model = intent.getStringExtra("model");
        String userId = intent.getStringExtra("userId");
        String scanId = intent.getStringExtra("scanId");

        // Load the appropriate fragment based on the model string
        if (model != null && userId != null && scanId != null) {
            loadFragmentForModel(model, userId, scanId);
        } else {
            Log.e(TAG, "Intent extras are missing. Cannot load a fragment.");
            finish();
        }
    }

    private void loadFragmentForModel(String model, String userId, String scanId) {
        // Check if we have a cached result for on_device, upload, or standard scans
        if (model != null && (model.equals("on_device_scan") || model.equals("upload_scan") || model.equals("standard_scan"))) {
            if (loadCachedScanReport(scanId)) return;
        }

        Fragment fragment;
        switch (model != null ? model : "") {
            case "standard_scan":
                fragment = new StandardScanFragment();
                setTitle("Standard Scan Report");
                break;
            case "multi_scan":
                fragment = new MultiScanFragment();
                setTitle("Multi-Scan Report");
                break;
            case "video_scan":
                fragment = new VideoScanFragment();
                setTitle("Video Scan Report");
                break;
            default:
                // Try cached result first for any unknown model type
                if (loadCachedScanReport(scanId)) return;
                Log.w(TAG, "Unknown model type: " + model + ". Loading default fragment.");
                fragment = new StandardScanFragment();
                setTitle("Scan Report");
                break;
        }

        Bundle args = new Bundle();
        args.putString("userId", userId);
        args.putString("scanId", scanId);
        fragment.setArguments(args);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }

    private boolean loadCachedScanReport(String scanId) {
        try {
            SharedPreferences prefs = getSharedPreferences("scan_results_cache", MODE_PRIVATE);
            String resultJson = prefs.getString(scanId, null);
            if (resultJson == null) {
                Log.w(TAG, "No cached result for scanId: " + scanId);
                return false;
            }

            Gson gson = new Gson();
            StandardScanResponse response = gson.fromJson(resultJson, StandardScanResponse.class);
            if (response == null) return false;

            setTitle("Scan Report");
            buildFullReportView(response, scanId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load cached report: " + e.getMessage());
            return false;
        }
    }

    private void buildFullReportView(StandardScanResponse response, String scanId) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        StandardScanResponse.Authenticity auth = response.getAuthenticity();

        // --- Status Badge ---
        String statusText = auth != null && auth.getStatus() != null ? auth.getStatus() : "UNKNOWN";
        int statusColor = Color.GRAY;
        if (auth != null) {
            if (auth.isGenuine()) {
                statusColor = Color.parseColor("#2E7D32");
            } else if (statusText.toUpperCase(Locale.US).contains("LIKELY")) {
                statusColor = Color.parseColor("#FF8F00");
            } else {
                statusColor = Color.parseColor("#C62828");
            }
        }

        TextView statusTv = new TextView(this);
        statusTv.setText(statusText);
        statusTv.setTextSize(28);
        statusTv.setTypeface(null, Typeface.BOLD);
        statusTv.setGravity(Gravity.CENTER);
        statusTv.setTextColor(statusColor);
        root.addView(statusTv);

        // Denomination
        if (response.getDenomination() != null && !response.getDenomination().isEmpty()) {
            TextView denomTv = new TextView(this);
            denomTv.setText("Detected: " + response.getDenomination());
            denomTv.setTextSize(18);
            denomTv.setGravity(Gravity.CENTER);
            denomTv.setTextColor(0xFF555555);
            denomTv.setPadding(0, 8, 0, 0);
            root.addView(denomTv);
        }

        // Confidence bar
        double confidenceValue = 0;
        if (auth != null && auth.getConfidence() != null) {
            String confStr = auth.getConfidence().replaceAll("[^0-9.]", "");
            try { confidenceValue = Double.parseDouble(confStr); } catch (NumberFormatException ignored) {}

            TextView confTv = new TextView(this);
            confTv.setText("Authenticity Score: " + auth.getConfidence());
            confTv.setTextSize(15);
            confTv.setTypeface(null, Typeface.BOLD);
            confTv.setGravity(Gravity.CENTER);
            confTv.setPadding(0, 24, 0, 8);
            root.addView(confTv);

            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress((int) confidenceValue);
            bar.getProgressDrawable().setColorFilter(statusColor, android.graphics.PorterDuff.Mode.SRC_IN);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 24);
            bar.setLayoutParams(barParams);
            root.addView(bar);
        }

        // Coverage
        int detectedCount = auth != null ? auth.getDetectedFeaturesCount() : 0;
        int totalCount = auth != null ? auth.getTotalExpectedFeatures() : 0;
        double coverage = response.getCoveragePercentage();
        TextView coverageTv = new TextView(this);
        if (totalCount > 0) {
            coverageTv.setText(String.format(Locale.US, "Feature Coverage: %.1f%% (%d/%d features detected)",
                    coverage, detectedCount, totalCount));
        } else {
            coverageTv.setText(String.format(Locale.US, "Overall Coverage: %.1f%%", coverage));
        }
        coverageTv.setTextSize(14);
        coverageTv.setGravity(Gravity.CENTER);
        coverageTv.setTextColor(0xFF666666);
        coverageTv.setPadding(0, 16, 0, 0);
        root.addView(coverageTv);

        addDivider(root);

        // --- Security Features Checklist ---
        StandardScanResponse.SecurityFeatures sf = response.getSecurityFeatures();
        if (sf != null) {
            TextView sfTitle = new TextView(this);
            sfTitle.setText("Security Features");
            sfTitle.setTextSize(16);
            sfTitle.setTypeface(null, Typeface.BOLD);
            sfTitle.setTextColor(0xFF333333);
            sfTitle.setPadding(0, 0, 0, 12);
            root.addView(sfTitle);

            addFeatureRow(root, "Watermark", sf.hasWatermark());
            addFeatureRow(root, "Security Thread", sf.hasSecurityThread());
            addFeatureRow(root, "Serial Number", sf.hasSerialNumber());
            addFeatureRow(root, "See-through Mark", sf.hasSeeThroughMark());
            addFeatureRow(root, "Concealed Value", sf.hasConcealedValue());
            addFeatureRow(root, "OVD Patch", sf.hasOvd());
            addFeatureRow(root, "Enhanced Value Panel", sf.hasEnhancedValuePanel());
            addFeatureRow(root, "Optically Variable Ink", sf.hasOpticallyVariableInk());

            addDivider(root);
        }

        // --- Recommendation ---
        TextView recTitle = new TextView(this);
        recTitle.setText("Recommendation");
        recTitle.setTextSize(16);
        recTitle.setTypeface(null, Typeface.BOLD);
        recTitle.setTextColor(0xFF333333);
        recTitle.setPadding(0, 0, 0, 8);
        root.addView(recTitle);

        TextView recTv = new TextView(this);
        recTv.setText(buildRecommendation(auth, statusText));
        recTv.setTextSize(13);
        recTv.setTextColor(0xFF333333);
        recTv.setPadding(16, 16, 16, 16);
        recTv.setBackgroundColor(0xFFF0F0F0);
        root.addView(recTv);

        addDivider(root);

        // --- Images ---
        // Scanned image from local file
        String imageUrl = null;
        SharedPreferences reportPrefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
        String reportsStr = reportPrefs.getString("reports", "{}");
        try {
            org.json.JSONObject reports = new org.json.JSONObject(reportsStr);
            if (reports.has(scanId)) {
                imageUrl = reports.getJSONObject(scanId).optString("imageUrl", "");
            }
        } catch (Exception ignored) {}

        if (imageUrl != null && !imageUrl.isEmpty()) {
            addImageSection(root, "Scanned Image", imageUrl);
        }

        // Annotated image from API
        if (response.getAnnotatedImageUrl() != null && !response.getAnnotatedImageUrl().isEmpty()) {
            addImageSection(root, "Annotated Overview", response.getAnnotatedImageUrl());
        }

        // --- Quality Metrics ---
        StandardScanResponse.QualityMetrics metrics = response.getQualityMetrics();
        if (metrics != null) {
            addDivider(root);
            TextView qTitle = new TextView(this);
            qTitle.setText("Image Quality");
            qTitle.setTextSize(16);
            qTitle.setTypeface(null, Typeface.BOLD);
            qTitle.setTextColor(0xFF333333);
            qTitle.setPadding(0, 0, 0, 8);
            root.addView(qTitle);

            addDetailRow(root, "Status", String.format(Locale.US, "%s (%.1f)", metrics.getQualityStatus(), metrics.getOverallQuality()));
            addDetailRow(root, "Sharpness", String.format(Locale.US, "%.1f", metrics.getSharpness()));
            addDetailRow(root, "Brightness", String.format(Locale.US, "%.1f", metrics.getBrightness()));
            addDetailRow(root, "Contrast", String.format(Locale.US, "%.1f", metrics.getContrast()));
        }

        addDivider(root);

        // --- Processing Info ---
        TextView piTitle = new TextView(this);
        piTitle.setText("Processing Info");
        piTitle.setTextSize(16);
        piTitle.setTypeface(null, Typeface.BOLD);
        piTitle.setTextColor(0xFF333333);
        piTitle.setPadding(0, 0, 0, 8);
        root.addView(piTitle);

        addDetailRow(root, "Scan ID", response.getScanId());
        addDetailRow(root, "Timestamp", response.getTimestamp());
        addDetailRow(root, "Processing Time", String.format(Locale.US, "%.1fs", response.getProcessingTime()));
        addDetailRow(root, "Model Info", response.getModelInfo());
        addDetailRow(root, "Analysis Type", response.getAnalysisType());
        addDetailRow(root, "Feature Summary", response.getFeatureSummary());

        scrollView.addView(root);
        binding.fragmentContainer.removeAllViews();
        binding.fragmentContainer.addView(scrollView);
    }

    private void addFeatureRow(LinearLayout parent, String featureName, boolean detected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 6, 0, 6);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        icon.setTextSize(16);
        icon.setText(detected ? "\u2705" : "\u274C");
        icon.setPadding(0, 0, 16, 0);
        row.addView(icon);

        TextView name = new TextView(this);
        name.setTextSize(14);
        name.setText(featureName);
        name.setTextColor(detected ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        if (detected) {
            name.setTypeface(null, Typeface.BOLD);
        }
        row.addView(name);

        parent.addView(row);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        divParams.setMargins(0, 24, 0, 24);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(0xFFE0E0E0);
        parent.addView(divider);
    }

    private void addImageSection(LinearLayout parent, String title, String imageUrl) {
        TextView imgTitle = new TextView(this);
        imgTitle.setText(title);
        imgTitle.setTextSize(14);
        imgTitle.setTypeface(null, Typeface.BOLD);
        imgTitle.setTextColor(0xFF333333);
        imgTitle.setPadding(0, 16, 0, 8);
        parent.addView(imgTitle);

        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500);
        imgParams.setMargins(0, 0, 0, 16);
        imageView.setLayoutParams(imgParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (imageUrl.startsWith("/")) {
            Glide.with(this).load(new File(imageUrl)).into(imageView);
        } else {
            Glide.with(this).load(imageUrl).into(imageView);
        }
        parent.addView(imageView);
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

    private void addDetailRow(LinearLayout parent, String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 8);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(12);
        labelTv.setTextColor(0xFF999999);
        row.addView(labelTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(15);
        valueTv.setTextColor(0xFF333333);
        valueTv.setTypeface(null, Typeface.BOLD);
        row.addView(valueTv);

        parent.addView(row);
    }
}
