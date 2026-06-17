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
        modelManager.fetchConfig(new TFLiteModelManager.ConfigCallback() {
            @Override
            public void onConfigLoaded(String mode) {
                runOnUiThread(() -> {
                    boolean isOffline = "on_device".equals(mode);
                    isUpdatingSwitch = true;
                    binding.modeSwitch.setChecked(isOffline);
                    isUpdatingSwitch = false;
                    if (isOffline) {
                        setOfflineMode();
                    } else {
                        setOnlineMode();
                    }
                    updatePackageStatus();
                });
            }

            @Override
            public void onConfigError(String error) {
                runOnUiThread(() -> {
                    isUpdatingSwitch = true;
                    binding.modeSwitch.setChecked(false);
                    isUpdatingSwitch = false;
                    setOnlineMode();
                    updatePackageStatus();
                });
            }
        });
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

    private boolean isPackageInstalled() {
        File modelsDir = new File(getFilesDir(), "tflite_models");
        if (!modelsDir.exists()) return false;
        File counterfeit = new File(modelsDir, "counterfeit_best_float32.tflite");
        File security = new File(modelsDir, "security_best_int8.tflite");
        // Check both exist AND have real content (> 1MB each)
        boolean installed = counterfeit.exists() && counterfeit.length() > 1_000_000
                && security.exists() && security.length() > 1_000_000;
        Log.d(TAG, "Package installed check: " + installed
                + " counterfeit=" + (counterfeit.exists() ? counterfeit.length() + "B" : "missing")
                + " security=" + (security.exists() ? security.length() + "B" : "missing"));
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
            "counterfeit_best_float32.tflite",
            "security_best_int8.tflite"
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
                    String displayName = modelName.contains("counterfeit") ? "Counterfeit Detection" : "Security Features";

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

                isDownloading = false;
                runOnUiThread(() -> {
                    binding.downloadProgress.setProgress(100);
                    binding.downloadProgress.setVisibility(View.GONE);
                    binding.downloadStatus.setVisibility(View.GONE);
                    updatePackageStatus();
                    Toast.makeText(MLSettingsActivity.this,
                            "Scanning package installed! You can now use offline mode.",
                            Toast.LENGTH_LONG).show();
                });

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
        // Save locally first (critical for offline mode to work without network)
        getSharedPreferences("ml_config_prefs", MODE_PRIVATE)
                .edit()
                .putString("scan_mode", mode)
                .apply();
        Log.d(TAG, "Mode saved locally: " + mode);

        // Also save to Firebase RTDB via REST (best-effort, may fail offline)
        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                String json = "{\"scan_mode\":\"" + mode + "\",\"updated_at\":\"" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) +
                        "\",\"updated_by\":\"user\"}";
                okhttp3.RequestBody body = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("https://bill-sense-aec6b-default-rtdb.firebaseio.com/ml_config.json")
                        .patch(body)
                        .build();
                okhttp3.Response response = client.newCall(request).execute();
                Log.d(TAG, "Saved mode to Firebase: " + mode + " status=" + response.code());
                response.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save mode: " + e.getMessage());
            }
        }).start();
    }
}
