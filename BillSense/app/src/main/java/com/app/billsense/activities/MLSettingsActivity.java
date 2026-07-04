package com.app.billsense.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.databinding.ActivityMlSettingsBinding;
import com.app.billsense.scan.pojo.TFLiteModelManager;

import java.io.File;
import java.util.Map;

public class MLSettingsActivity extends AppCompatActivity {

    private static final String TAG = "MLSettingsActivity";
    private ActivityMlSettingsBinding binding;
    private TFLiteModelManager modelManager;
    private boolean isDownloading = false;
    private boolean isUpdatingSwitch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMlSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        modelManager = TFLiteModelManager.getInstance(this);

        binding.backBtn.setOnClickListener(v -> finish());

        // Load current config
        loadConfig();

        // Mode toggle
        binding.modeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingSwitch) return; // Ignore programmatic changes
            if (isChecked) {
                // Offline mode
                if (!isPackageInstalled()) {
                    Toast.makeText(this, "Please install the scanning package first", Toast.LENGTH_SHORT).show();
                    isUpdatingSwitch = true;
                    binding.modeSwitch.setChecked(false);
                    isUpdatingSwitch = false;
                    return;
                }
                setOfflineMode();
            } else {
                setOnlineMode();
            }
            // Save to Firebase
            saveMode(isChecked ? "on_device" : "cloud");
        });

        // Download button
        binding.downloadBtn.setOnClickListener(v -> {
            if (!isDownloading) {
                startDownload();
            }
        });

        // Delete button
        binding.deleteBtn.setOnClickListener(v -> {
            deletePackage();
        });
    }

    private void loadConfig() {
        // Show the DEVICE's effective mode immediately — works fully offline.
        applyModeToUi(modelManager.isOnDeviceMode());
        updatePackageStatus();

        // Refresh the model list from the server in the background. The
        // result must never flip the user's toggle — only the package status.
        modelManager.fetchConfig(new TFLiteModelManager.ConfigCallback() {
            @Override
            public void onConfigLoaded(String mode) {
                runOnUiThread(() -> {
                    applyModeToUi(modelManager.isOnDeviceMode());
                    updatePackageStatus();
                });
            }

            @Override
            public void onConfigError(String error) {
                // Offline or server unreachable — keep showing the device mode.
                runOnUiThread(() -> {
                    applyModeToUi(modelManager.isOnDeviceMode());
                    updatePackageStatus();
                });
            }
        });
    }

    private void applyModeToUi(boolean isOffline) {
        isUpdatingSwitch = true;
        binding.modeSwitch.setChecked(isOffline);
        isUpdatingSwitch = false;
        if (isOffline) {
            setOfflineMode();
        } else {
            setOnlineMode();
        }
    }

    private void setOnlineMode() {
        binding.cloudLabel.setTextColor(getResources().getColor(com.app.billsense.R.color.colorPrimary));
        binding.offlineLabel.setTextColor(0xFF999999);
        binding.modeDescription.setText(
                "Your scans are processed securely via our cloud servers for maximum accuracy using the full multi-model detection system with 6 specialized AI models.");
    }

    private void setOfflineMode() {
        binding.offlineLabel.setTextColor(getResources().getColor(com.app.billsense.R.color.colorPrimary));
        binding.cloudLabel.setTextColor(0xFF999999);
        binding.modeDescription.setText(
                "Scans are processed directly on your phone using the downloaded models. No internet required — results are instant and your images stay private on your device.");
    }

    /** Returns the best available cached file for a model: float32 first, int8 fallback. */
    public static File findCachedModel(File modelsDir, String baseName) {
        File f32 = new File(modelsDir, baseName + "_float32.tflite");
        if (f32.exists() && f32.length() > 1_000_000) return f32;
        File i8 = new File(modelsDir, baseName + "_int8.tflite");
        if (i8.exists() && i8.length() > 1_000_000) return i8;
        return null;
    }

    private boolean isPackageInstalled() {
        File modelsDir = new File(getFilesDir(), "tflite_models");
        if (!modelsDir.exists()) return false;
        // Either precision counts as installed; loaders prefer float32.
        File denomination = findCachedModel(modelsDir, "denomination2");
        File securitycf = findCachedModel(modelsDir, "securitycf");
        boolean installed = denomination != null && securitycf != null;
        Log.d(TAG, "Package installed check: " + installed
                + " denomination=" + (denomination != null ? denomination.getName() : "missing")
                + " securitycf=" + (securitycf != null ? securitycf.getName() : "missing"));
        return installed;
    }

    private void updatePackageStatus() {
        if (isPackageInstalled()) {
            binding.packageStatus.setText("Installed ✓");
            binding.packageStatus.setTextColor(0xFF4CAF50);
            binding.downloadBtn.setText("Package Installed");
            binding.downloadBtn.setEnabled(false);
            binding.downloadBtn.setAlpha(0.5f);
            binding.deleteBtn.setVisibility(View.VISIBLE);
        } else {
            binding.packageStatus.setText("Not Installed");
            binding.packageStatus.setTextColor(0xFFF44336);
            binding.downloadBtn.setText("Download Scanning Package");
            binding.downloadBtn.setEnabled(true);
            binding.downloadBtn.setAlpha(1f);
            binding.deleteBtn.setVisibility(View.GONE);
        }
    }

    private static final String[] BUNDLED_MODELS = {
            "denomination2_int8.tflite",
            "securitycf_int8.tflite"
    };

    private void startDownload() {
        isDownloading = true;
        binding.downloadProgress.setVisibility(View.VISIBLE);
        binding.downloadStatus.setVisibility(View.VISIBLE);
        binding.downloadBtn.setEnabled(false);
        binding.downloadBtn.setText("Installing...");

        // Models are bundled in APK assets — copy to internal storage
        new Thread(() -> {
            try {
                File modelsDir = new File(getFilesDir(), "tflite_models");
                if (!modelsDir.exists()) modelsDir.mkdirs();

                int totalModels = BUNDLED_MODELS.length;
                for (int i = 0; i < totalModels; i++) {
                    String modelName = BUNDLED_MODELS[i];
                    File destFile = new File(modelsDir, modelName);
                    String displayName = modelName.contains("denomination") ? "Denomination Detection" : "Security & Counterfeit";

                    int modelIndex = i;
                    runOnUiThread(() -> {
                        binding.downloadStatus.setText("Installing " + displayName + "...");
                        binding.downloadProgress.setMax(100);
                    });

                    // Copy from assets
                    try (java.io.InputStream is = getAssets().open("models/" + modelName);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {

                        long totalBytes = is.available();
                        byte[] buffer = new byte[8192];
                        long copied = 0;
                        int read;
                        int lastPercent = 0;

                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            copied += read;
                            if (totalBytes > 0) {
                                int basePercent = (modelIndex * 100) / totalModels;
                                int modelPercent = (int) ((copied * 100) / totalBytes);
                                int totalPercent = basePercent + (modelPercent / totalModels);
                                if (totalPercent != lastPercent) {
                                    lastPercent = totalPercent;
                                    final int p = totalPercent;
                                    final String name = displayName;
                                    runOnUiThread(() -> {
                                        binding.downloadProgress.setProgress(p);
                                        binding.downloadStatus.setText("Installing " + name + "... " + p + "%");
                                    });
                                }
                            }
                        }
                        fos.flush();
                    }

                    Log.d(TAG, "Model installed: " + modelName + " -> " + destFile.length() + " bytes");

                    final String dn = displayName;
                    runOnUiThread(() -> binding.downloadStatus.setText(dn + " installed ✓"));
                }

                // Bundled (compact) models are in place — offline mode is usable
                // right away. Now fetch the high-accuracy float32 models from the
                // server config in the background (needs internet, ~85 MB once).
                runOnUiThread(() -> {
                    updatePackageStatus();
                    binding.downloadStatus.setText("Fetching high-accuracy models…");
                });
                downloadHighAccuracyModels();

            } catch (Exception e) {
                isDownloading = false;
                Log.e(TAG, "Install error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    binding.downloadProgress.setVisibility(View.GONE);
                    binding.downloadStatus.setVisibility(View.GONE);
                    binding.downloadBtn.setEnabled(true);
                    binding.downloadBtn.setText("Retry Install");
                    Toast.makeText(MLSettingsActivity.this,
                            "Installation failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Downloads the high-accuracy models named in ml_config (float32) via the
     * model manager. Non-fatal: the bundled compact models keep offline mode
     * working if this fails (e.g. no internet during install).
     */
    private void downloadHighAccuracyModels() {
        modelManager.downloadEnabledModels(new TFLiteModelManager.DownloadCallback() {
            @Override
            public void onProgress(String modelName, int percentComplete) {
                runOnUiThread(() -> {
                    binding.downloadProgress.setVisibility(View.VISIBLE);
                    binding.downloadStatus.setVisibility(View.VISIBLE);
                    binding.downloadProgress.setProgress(percentComplete);
                    binding.downloadStatus.setText("High-accuracy " + modelName + "… " + percentComplete + "%");
                });
            }

            @Override
            public void onModelReady(String modelName, File modelFile) {
                Log.d(TAG, "High-accuracy model ready: " + modelFile.getName());
            }

            @Override
            public void onAllModelsReady(Map<String, File> modelFiles) {
                isDownloading = false;
                boolean gotAll = modelFiles.size() >= 2;
                runOnUiThread(() -> {
                    binding.downloadProgress.setVisibility(View.GONE);
                    binding.downloadStatus.setVisibility(View.GONE);
                    updatePackageStatus();
                    Toast.makeText(MLSettingsActivity.this,
                            gotAll ? "Scanning package installed with high-accuracy models! Offline mode is ready."
                                   : "Package installed. High-accuracy models will download next time you're online.",
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String modelName, String error) {
                Log.w(TAG, "High-accuracy download failed for " + modelName + ": " + error);
            }
        });
    }

    private void deletePackage() {
        File modelsDir = new File(getFilesDir(), "tflite_models");
        if (modelsDir.exists()) {
            File[] files = modelsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }

        // Switch back to online mode
        binding.modeSwitch.setChecked(false);
        setOnlineMode();
        saveMode("cloud");
        updatePackageStatus();
        Toast.makeText(this, "Scanning package removed", Toast.LENGTH_SHORT).show();
    }

    private void saveMode(String mode) {
        // Per-DEVICE choice — authoritative on this phone, survives config
        // refetches, and needs no network. (The old direct RTDB write was
        // always rejected by the auth!=null rule, and the next online config
        // fetch silently flipped the device back to cloud mode.)
        modelManager.setDeviceScanMode(mode);
        Log.d(TAG, "Device scan mode saved: " + mode);
    }
}
