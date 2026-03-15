package com.app.billsense.fragments;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.app.billsense.databinding.FragmentStandardScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.StandardScan;
import com.app.billsense.utils.FBUtils;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class StandardScanFragment extends Fragment {

    private static final String TAG = "StandardScanFragment";
    private String userId;
    private String scanId;

    private FragmentStandardScanBinding binding;
    private FBUtils fbUtils;

    public StandardScanFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
            scanId = getArguments().getString("scanId");
            Log.d(TAG, "Received userId: " + userId + " and scanId: " + scanId);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentStandardScanBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        fbUtils = new FBUtils();
        getStandardScanResult();
        return view;
    }

    private void getStandardScanResult() {
        showProgressDialog(requireActivity());

        fbUtils.getAllDataFromPath(fbUtils.STANDARD_SCAN_PATH + "/" + userId, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Log.d(TAG, "onFetchDataSuccess: " + snapshot.toString());
                    if (snapshot.getKey().equals(scanId)) {
                        StandardScan standardScan = snapshot.getValue(StandardScan.class);
                        Log.d(TAG, "onFetchDataSuccess: model data: " + standardScan.getScanId() +
                                ", " + standardScan.getAuthenticity().getConfidence() +
                                ", " + standardScan.getQualityMetrics().getQualityStatus());
                        Log.d(TAG, "SUCCESS: Snapshot exists. Parsing manually.");
                        populateUiManually(standardScan);
                        break;
                    }
                }
                hideProgressDialog();
            }

            @Override
            public void onDataNotFound() {
                hideProgressDialog();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                hideProgressDialog();
            }
        });


    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void populateUiManually(StandardScan standardScan) {
        if (getContext() == null) return;

        try {
            // --- Extracting every field from the StandardScan object ---
            String scanId = standardScan.getScanId();
            String denomination = standardScan.getDenomination();
            String featureSummary = standardScan.getFeatureSummary();
            String annotatedImageUrl = standardScan.getAnnotatedImageUrl();
            String timestamp = standardScan.getTimestamp();
            Double processingTime = standardScan.getProcessingTime();
            Double coveragePercentage = standardScan.getCoveragePercentage();

            StandardScan.Authenticity authenticity = standardScan.getAuthenticity();
            String authStatus = authenticity.getStatus();
            String authConfidence = authenticity.getConfidence();
            Boolean authIsGenuine = authenticity.isGenuine();
            List<String> reasonsList = authenticity.getReasons();

            StandardScan.QualityMetrics qualityMetrics = standardScan.getQualityMetrics();
            String qualityStatus = qualityMetrics.getQualityStatus();

            Log.d(TAG, "MANUAL PARSE SUCCESS: Scan ID: " + scanId + ", Status: " + authStatus
                    + ", TimeStamp: " + timestamp + ", Denomination: " + denomination);

            // --- UPDATE THE UI ---
            binding.userIdText.setText("User ID: " + userId);
            binding.scanIdText.setText("Scan ID: " + scanId);
            binding.timestampText.setText("Timestamp: " + timestamp);
            binding.denominationResultText.setText(denomination);
            binding.featuresSummaryText.setText("Summary: " + featureSummary);

            if (processingTime != null) {
                binding.processingTimeText.setText(String.format("Processing Time: %.1fs", processingTime));
                binding.processingTimeText.setVisibility(View.VISIBLE);
            }

            if (coveragePercentage != null) {
                binding.coverageText.setText(String.format("Overall Coverage: %.1f%%", coveragePercentage));
                binding.coverageText.setVisibility(View.VISIBLE);
            }

            // Authenticity
            binding.authenticityResultText.setText(authStatus);
            boolean isGenuine = authIsGenuine != null && authIsGenuine;
            binding.authenticityResultText.setTextColor(
                    isGenuine ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828")
            );
            binding.confidenceResultText.setText("Confidence: " + authConfidence);

            if (reasonsList != null && !reasonsList.isEmpty()) {
                binding.reasonsText.setText("Reasons: " + String.join("\n", reasonsList));
                binding.reasonsText.setVisibility(View.VISIBLE);
            } else {
                binding.reasonsText.setVisibility(View.GONE);
            }

            // Quality Metrics
            binding.qualityMetricsCard.setVisibility(View.VISIBLE);
            binding.qualityStatusText.setText("Status: " + qualityStatus);
            binding.sharpnessText.setText(String.format("Sharpness: %.2f (Score: %.2f)", qualityMetrics.getSharpness(), qualityMetrics.getSharpnessScore()));
            binding.brightnessText.setText(String.format("Brightness: %.2f (Score: %.2f)", qualityMetrics.getBrightness(), qualityMetrics.getBrightnessScore()));
            binding.contrastText.setText(String.format("Contrast: %.2f (Score: %.2f)", qualityMetrics.getContrast(), qualityMetrics.getContrastScore()));

            // Image
            if (annotatedImageUrl != null && !annotatedImageUrl.isEmpty()) {
                binding.annotatedImageCard.setVisibility(View.VISIBLE);
                Glide.with(this).load(annotatedImageUrl).into(binding.annotatedImageView);
            } else {
                binding.annotatedImageCard.setVisibility(View.GONE);
            }

            // Hide things we are not populating to avoid confusion
            binding.detectedFeaturesCountText.setVisibility(View.GONE);

        } catch (Exception e) {
            Log.e(TAG, "FATAL EXCEPTION during manual parsing: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to read data. Check logs.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
