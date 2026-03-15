package com.app.billsense.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityDetectionBinding;
import com.app.billsense.databinding.ItemProcessDetectionBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Detections;
import com.app.billsense.utils.FBUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;

public class DetectionActivity extends AppCompatActivity {
    private ActivityDetectionBinding binding;
    private MaterialToolbar toolbar;
    private FBUtils fbUtils;
    private ArrayList<Detections> detectionsArrayList = new ArrayList<>();
    private int currentPageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.step_by_step_enhanced_detection_process));
        }

        toolbar.setNavigationOnClickListener(view -> finish());

        getDetectionContent();

        binding.nextBtn.setOnClickListener(v -> navigateToNextPage());
        binding.previousBtn.setOnClickListener(v -> navigateToPreviousPage());
        binding.submitBtn.setOnClickListener(v -> submitDetections());
    }

    private void getDetectionContent() {
        showProgressDialog(this);
        fbUtils.getAllDataFromPath(fbUtils.DETECTIONS_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                detectionsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Detections detection = snapshot.getValue(Detections.class);
                    if (detection != null && "process".equals(detection.getType())) {
                        detectionsArrayList.add(detection);
                    }
                }
                Log.d("==DETECTION", "onFetchDataSuccess: " + detectionsArrayList.size());
                hideProgressDialog();
                if (!detectionsArrayList.isEmpty()) {
                    currentPageIndex = 0;
                    displayCurrentPageContent();
                    updateButtonStates();
                } else {
                    // Handle case where there's no content
                    Toast.makeText(DetectionActivity.this, "No detection process content found.", Toast.LENGTH_SHORT).show();
                    binding.nextBtn.setVisibility(GONE);
                    binding.previousBtn.setVisibility(GONE);
                    binding.submitBtn.setVisibility(GONE);
                }
            }

            @Override
            public void onDataNotFound() {
                detectionsArrayList.clear();
                hideProgressDialog();
                Toast.makeText(DetectionActivity.this, "Detection content not found.", Toast.LENGTH_SHORT).show();
                updateButtonStates(); // Hide all buttons
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                hideProgressDialog();
                detectionsArrayList.clear();
                Toast.makeText(DetectionActivity.this, "Failed to fetch content: " + errorMessage, Toast.LENGTH_SHORT).show();
                updateButtonStates(); // Hide all buttons
            }
        });
    }

    private void displayCurrentPageContent() {
        if (detectionsArrayList.isEmpty() || currentPageIndex < 0 || currentPageIndex >= detectionsArrayList.size()) {
            binding.pageContentContainer.removeAllViews(); // Clear previous content
            return;
        }

        Detections currentDetection = detectionsArrayList.get(currentPageIndex);
        binding.pageContentContainer.removeAllViews(); // Clear previous content

        ItemProcessDetectionBinding itemBinding = ItemProcessDetectionBinding.inflate(LayoutInflater.from(this), binding.pageContentContainer, false);

        // Set the title
        if (currentDetection.getContent() != null) {
            itemBinding.processDetectionTitle.setText(currentDetection.getContent());
        } else {
            itemBinding.processDetectionTitle.setText(getString(R.string.title_not_available)); // Or some default text
        }

        // Set the image
        if (currentDetection.getImage() != null && !currentDetection.getImage().isEmpty()) {
            itemBinding.processDetectionImg.setVisibility(View.VISIBLE); // Make ImageView visible
            Glide.with(this)
                    .load(currentDetection.getImage())
                    .placeholder(R.drawable.ic_image_placeholder) // Optional: a placeholder image
                    .error(R.drawable.ic_error_image) // Optional: an image to show if loading fails
                    .into(itemBinding.processDetectionImg);
        } else {
            itemBinding.processDetectionImg.setVisibility(View.GONE); // Hide ImageView if no image URL
        }

        binding.pageContentContainer.addView(itemBinding.getRoot());
    }


    private void navigateToNextPage() {
        if (currentPageIndex < detectionsArrayList.size() - 1) {
            currentPageIndex++;
            displayCurrentPageContent();
            updateButtonStates();
        }
    }

    private void navigateToPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            displayCurrentPageContent();
            updateButtonStates();
        }
    }

    private void updateButtonStates() {
        if (detectionsArrayList.isEmpty()) {
            binding.previousBtn.setVisibility(GONE);
            binding.nextBtn.setVisibility(GONE);
            binding.submitBtn.setVisibility(GONE);
            return;
        }

        // Previous button visibility
        binding.previousBtn.setVisibility(currentPageIndex > 0 ? VISIBLE : GONE);

        // Next/Submit button visibility
        if (currentPageIndex == detectionsArrayList.size() - 1) { // Last page
            binding.nextBtn.setVisibility(GONE);
            binding.submitBtn.setVisibility(VISIBLE);
        } else { // Not the last page
            binding.nextBtn.setVisibility(VISIBLE);
            binding.submitBtn.setVisibility(GONE);
        }

        // Handle case for only one page
        if (detectionsArrayList.size() == 1) {
            binding.previousBtn.setVisibility(GONE);
            binding.nextBtn.setVisibility(GONE);
            binding.submitBtn.setVisibility(VISIBLE);
        }
    }

    private void submitDetections() {
        // Handle the submission logic here
        Toast.makeText(this, "Submitting detections...", Toast.LENGTH_SHORT).show();
        // Example: Call a method in fbUtils or navigate to another activity
        // finish(); // Optionally finish this activity after submission
    }
}
