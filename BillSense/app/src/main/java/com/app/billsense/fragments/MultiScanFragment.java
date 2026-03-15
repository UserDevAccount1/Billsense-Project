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
import android.widget.ImageView;
import android.widget.TextView;

import com.app.billsense.R;
import com.app.billsense.databinding.FragmentMultiScanBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.MultiScan;
import com.app.billsense.model.StandardScan;
import com.app.billsense.utils.DialogUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.Utils;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;

import java.util.Map;
import java.util.stream.Collectors;

public class MultiScanFragment extends Fragment {

    private static final String TAG = "MultiScanFragment";
    private FragmentMultiScanBinding binding;
    private String userId;
    private String scanId;
    private FBUtils fbUtils;

    public MultiScanFragment() {
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
        // Inflate the layout for this fragment using view binding
        binding = FragmentMultiScanBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        fbUtils = new FBUtils();
        getVideoScanResult();
        return view;
    }

    private void getVideoScanResult() {
        showProgressDialog(requireActivity());

        fbUtils.getAllDataFromPath(fbUtils.MULTI_SCAN_PATH + "/" + userId, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Log.d(TAG, "onFetchDataSuccess: " + snapshot.toString());
                    if (snapshot.getKey().equals(scanId)) {
                        MultiScan standardScan = snapshot.getValue(MultiScan.class);
                        Log.d(TAG, "onFetchDataSuccess: model data: " + standardScan.getScanId() +
                                ", " + standardScan.getAuthenticity() +
                                ", " + standardScan.getDetectedFeaturesCount());
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
    private void populateUi(MultiScan response) {
        // --- Overall Result Card ---
        binding.authenticityResultText.setText(response.getAuthenticity());
        binding.authenticityResultText.setTextColor(
                response.isGenuine() ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828")
        );
        binding.denominationResultText.setText(response.getDenomination());
        binding.confidenceResultText.setText("Confidence: " + response.getConfidence());

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
        binding.statusMessageText.setText(String.format("Status: %s", response.getStatus()));
        binding.highDenominationText.setText("High Denomination: " + (response.isHighDenomination() ? "Yes" : "No"));
        binding.storageIdText.setText("Storage ID: " + response.getStorageId());

        // --- Angle Breakdown Section ---
        if (response.getAngleResults() != null && !response.getAngleResults().isEmpty()) {
            binding.angleResultsContainer.removeAllViews();
            Map<Integer, String> angleImageMap = response.getAngleImages().stream()
                    .collect(Collectors.toMap(MultiScan.AngleImage::getAngleNumber, MultiScan.AngleImage::getImageUrl));

            for (MultiScan.AngleResult angle : response.getAngleResults()) {
                MaterialCardView angleCard = (MaterialCardView) LayoutInflater.from(requireActivity())
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
                MultiScan.QualityMetrics metrics = angle.getQualityMetrics();
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
                    DialogUtils.displayFullImageDialog(requireActivity(), "Angle Image", imageUrl, null);
                });
                binding.angleResultsContainer.addView(angleCard);
            }
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
