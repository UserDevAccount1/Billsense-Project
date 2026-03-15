package com.app.billsense.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityViewScanReportBinding;
import com.app.billsense.fragments.MultiScanFragment;
import com.app.billsense.fragments.StandardScanFragment;
import com.app.billsense.fragments.VideoScanFragment;
import com.google.android.material.appbar.MaterialToolbar;

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
        String userId = intent.getStringExtra("userId"); // Get userId
        String scanId = intent.getStringExtra("scanId"); // Get scanId

        // Load the appropriate fragment based on the model string
        if (model != null && userId != null && scanId != null) {
            loadFragmentForModel(model, userId, scanId);
        } else {
            Log.e(TAG, "Intent extras are missing. Cannot load a fragment.");
            // Optionally, show an error message or a default fragment
            finish(); // Finish activity if critical data is missing
        }
    }

    private void loadFragmentForModel(String model, String userId, String scanId) {
        Fragment fragment;
        switch (model) {
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
                Log.w(TAG, "Unknown model type: " + model + ". Loading default fragment.");
                // Fallback to a default fragment or handle the error
                fragment = new StandardScanFragment();
                setTitle("Scan Report");
                break;
        }

        // --- KEY CHANGE: Pass data to the fragment using a Bundle ---
        Bundle args = new Bundle();
        args.putString("userId", userId);
        args.putString("scanId", scanId);
        fragment.setArguments(args);
        // -------------------------------------------------------------

        // Begin the transaction to replace the FrameLayout with the chosen fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }
}
