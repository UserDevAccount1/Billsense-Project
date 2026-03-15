package com.app.billsense.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class PermissionManager {

    private static final String TAG = "PermissionManager";
    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private final AppCompatActivity activity;
    private final LocationPermissionListener listener;
    private ActivityResultLauncher<String[]> permissionLauncher;

    public interface LocationPermissionListener {
        void onPermissionGranted();
        void onPermissionDenied(boolean permanentlyDenied);
    }

    public PermissionManager(@NonNull AppCompatActivity activity, @NonNull LocationPermissionListener listener) {
        this.activity = activity;
        this.listener = listener;
        initializeLauncher();
    }

    private void initializeLauncher() {
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::handleResult);
    }

    private Context getContext() {
        return activity;
    }

    private Activity getActivity() {
        return activity;
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /*
    // Optional: Helper to check if permission is actually in the manifest (for pre-M or detailed checks)
    private boolean isPermissionInManifest(String permission) {
        try {
            PackageManager pm = getContext().getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(getContext().getPackageName(), PackageManager.GET_PERMISSIONS);
            if (packageInfo.requestedPermissions != null) {
                for (String requestedPermission : packageInfo.requestedPermissions) {
                    if (requestedPermission.equals(permission)) {
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package name not found", e);
        }
        return false;
    }
    */

    public void requestLocationPermission() {

        if (hasLocationPermission()) {
            Log.d(TAG, "Location permission already granted.");
            listener.onPermissionGranted();
            return;
        }

        boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION);

        if (shouldShowRationale) {
            Log.d(TAG, "Showing rationale for location permission.");
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle("Location Permission Needed")
                    .setMessage("This app requires access to your location to provide location-based features. Please grant the permission.")
                    .setPositiveButton("Grant", (dialog, which) -> permissionLauncher.launch(LOCATION_PERMISSIONS))
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Log.d(TAG, "User cancelled rationale dialog for location.");
                        listener.onPermissionDenied(false); // Not permanently denied from rationale cancel
                    })
                    .setCancelable(false) // Important for forcing a choice
                    .show();
        } else {
            Log.d(TAG, "Requesting location permission directly.");
            permissionLauncher.launch(LOCATION_PERMISSIONS);
        }
    }

    private void handleResult(Map<String, Boolean> grantResults) {
        boolean fineGranted = grantResults.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
        boolean coarseGranted = grantResults.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

        if (fineGranted || coarseGranted) {
            Log.d(TAG, "Location permission granted by user. Fine: " + fineGranted + ", Coarse: " + coarseGranted);
            listener.onPermissionGranted();
        } else {
            Log.d(TAG, "Location permission denied by user.");
            boolean permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;

            if(!permanentlyDenied){
                permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) &&
                        ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
            }


            if (permanentlyDenied) {
                Log.w(TAG, "Location permission appears to be permanently denied.");
            }
            listener.onPermissionDenied(permanentlyDenied);
        }
    }
}
