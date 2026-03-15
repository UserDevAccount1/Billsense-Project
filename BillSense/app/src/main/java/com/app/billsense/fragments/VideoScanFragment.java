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

import com.app.billsense.databinding.FragmentVideoScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.VideoScan;
import com.app.billsense.utils.FBUtils;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;

public class VideoScanFragment extends Fragment {
    private static final String TAG = "VideoScanFragment";
    private FragmentVideoScanBinding binding;
    private String userId;
    private String scanId;
    private FBUtils fbUtils;
    public VideoScanFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Receive the arguments here
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
            scanId = getArguments().getString("scanId");
            Log.d(TAG, "Received userId: " + userId + " and scanId: " + scanId);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Use View Binding to inflate the layout
        binding = FragmentVideoScanBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        fbUtils = new FBUtils();
        getVideoScanResult();

        return view;
    }

    private void getVideoScanResult() {
        showProgressDialog(requireActivity());

        fbUtils.getAllDataFromPath(fbUtils.VIDEO_SCAN_PATH + "/" + userId, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Log.d(TAG, "onFetchDataSuccess: " + snapshot.toString());
                    if (snapshot.getKey().equals(scanId)) {
                        VideoScan standardScan = snapshot.getValue(VideoScan.class);
                        Log.d(TAG, "onFetchDataSuccess: model data: " + standardScan.getScanId() +
                                ", " + standardScan.getAuthenticity().getConfidence() +
                                ", " + standardScan.getQualityMetrics());
                        Log.d(TAG, "SUCCESS: Snapshot exists. Parsing manually.");
                        populateUi(standardScan);
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
    private void populateUi(VideoScan response) {
        if (response == null) return;

        // Overall Result Card
        VideoScan.Authenticity auth = response.getAuthenticity();
        if (auth != null) {
            binding.authenticityResultText.setText(auth.getStatus());
            binding.authenticityResultText.setTextColor(
                    auth.isGenuine() ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828")
            );
            binding.confidenceResultText.setText("Confidence: " + auth.getConfidence());
            if (auth.getReasons() != null && !auth.getReasons().isEmpty()) {
                // Get the values from the map and then join them.
                binding.reasonsText.setText("Reasons: " + String.join("\n", auth.getReasons()));
            } else {
                binding.reasonsText.setText("Reasons: N/A");
            }

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
