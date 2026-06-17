package com.app.billsense.scan.multi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityMultiScanBinding;
import com.app.billsense.scan.pojo.RealTimeScanManager;
import com.app.billsense.scan.pojo.RealTimeScanResponse;
import com.app.billsense.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("UnsafeOptInUsageError")
public class MultiScanActivity extends AppCompatActivity implements RealTimeScanManager.RealTimeScanListener {
    private static final String TAG = "MultiScanActivity";
    private static final int MAX_IMAGES = 3;
    private ActivityMultiScanBinding binding;

    // --- Real-Time and Camera Components ---
    private RealTimeScanManager scanManager;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture; // For saving files (Capture Button)
    private ImageAnalysis imageAnalysis; // For live preview analysis (WebSocket Feed)
    private YuvToRgbConverter yuvToRgbConverter;

    // --- Real-time WebSocket Feed Logic ---
    private volatile boolean canSendFrame = true;

    // --- File Capture Logic ---
    private final List<File> capturedImageFiles = new ArrayList<>();
    private int capturedImageCount = 0;

    // --- State Counter for Live Feed ---
    private int liveAngleCounter = 1;

    private final ActivityResultLauncher<String[]> activityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allPermissionsGranted = true;
                for (boolean isGranted : result.values()) {
                    if (!isGranted) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                if (allPermissionsGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permissions are required.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        binding = ActivityMultiScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Initialization ---
        cameraExecutor = Executors.newSingleThreadExecutor();
        scanManager = new RealTimeScanManager(this);
        yuvToRgbConverter = new YuvToRgbConverter(this);

        requestPermissions();
        setupClickListeners();
    }

    private void requestPermissions() {
        activityResultLauncher.launch(new String[]{Manifest.permission.CAMERA});
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
                scanManager.connect(RealTimeScanManager.ENDPOINT_MULTI_ANGLE_SCAN);
            } catch (Exception e) {
                Log.e(TAG, "CameraX setup failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.cameraPreviewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            if (canSendFrame) {
                canSendFrame = false;
                Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                yuvToRgbConverter.yuvToRgb(image.getImage(), bitmap);

                String angleCommand = "ANGLE_" + liveAngleCounter;
                scanManager.sendAngleFrame(angleCommand, bitmap);
            }
            image.close();
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis);
    }


    private void setupClickListeners() {
        binding.showInfoButton.setOnClickListener(view -> {
            binding.sideCardsLayout.setVisibility(
                    binding.sideCardsLayout.getVisibility() == View.GONE ? View.VISIBLE : View.GONE
            );
            binding.showInfoButton.setText(
                    binding.sideCardsLayout.getVisibility() == View.GONE ? R.string.show_info : R.string.hide_info
            );
        });

        binding.scanButton.setOnClickListener(v -> {
            if (capturedImageCount >= MAX_IMAGES) {
                // If 3 images are already taken, the button now means "Scan Now"
                launchPostScanActivity();
            } else {
                // Otherwise, it means "Capture"
                captureImageToFile();
            }
        });
    }

    private void captureImageToFile() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized.");
            return;
        }

        binding.scanButton.setEnabled(false);

        File photoFile = new File(getCacheDir(), new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpeg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        capturedImageFiles.add(photoFile);
                        capturedImageCount++;

                        runOnUiThread(() -> {
                            updateUiAfterCapture();
                            binding.scanButton.setEnabled(true); // Always re-enable the button
                            Toast.makeText(MultiScanActivity.this, "Image " + capturedImageCount + " captured!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        runOnUiThread(() -> {
                            binding.scanButton.setEnabled(true);
                            Toast.makeText(MultiScanActivity.this, "Capture failed.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    @SuppressLint("SetTextI18n")
    private void updateUiAfterCapture() {
        binding.imageCounter.setText(capturedImageCount + " of " + MAX_IMAGES + " captured");
        if (capturedImageCount >= MAX_IMAGES) {
            // --- NEW LOGIC: Change button text instead of disabling ---
            binding.scanButton.setText("Scan Now");
        }
    }

        private void launchPostScanActivity() {
        scanManager.disconnect();
        Intent intent = new Intent(this, MultiPostScanActivity.class);
        ArrayList<String> filePaths = new ArrayList<>();
        for (File file : capturedImageFiles) {
            filePaths.add(file.getAbsolutePath());
        }
        intent.putExtra("captured_image_paths", filePaths);
        startActivity(intent);
        finish();
    }


    @Override
    public void onConnectionOpen() {
        runOnUiThread(() -> Toast.makeText(this, "Connected! Live analysis started.", Toast.LENGTH_SHORT).show());
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onScanUpdate(RealTimeScanResponse response) {
        runOnUiThread(() -> {
            if (response == null || response.status == null) {
                canSendFrame = true;
                return;
            }

            if ("angle_captured".equals(response.status)) {
                // Increment counter for the next request
                liveAngleCounter++;

                // --- NEW UI LOGIC ---
                // Update all the new text views with data from the response
                binding.sideCardsLayout.setVisibility(View.VISIBLE);
                binding.showInfoButton.setText(R.string.hide_info);

                if (response.denomination != null) {
                    binding.currencyText.setText("Denomination: " + response.denomination);
                }

                if (response.status != null) {
                    binding.statusText.setText("Status: " + response.status);
                }
                if (response.progress != null) {
                    binding.confidenceText.setText("Progress: " + String.format(Locale.US, "%.1f%%", response.progress));
                }
                if (response.coveragePercentage != null) {
                    binding.coverageText.setText("Coverage: " + String.format(Locale.US, "%.1f%%", response.coveragePercentage));
                }
                if (response.featureCount != null) {
                    binding.featuresThisAngleText.setText("Angle Features: " + response.featureCount);
                }
                if (response.totalFeaturesSoFar != null) {
                    binding.totalFeaturesSoFarText.setText("Total Features: " + response.totalFeaturesSoFar);
                }
                if (response.allFeatures != null) {
                    binding.reasonsText.setText("All Features: " + String.join(", ", response.allFeatures));
                }
                // --- END NEW UI LOGIC ---

                if (response.qualityMetrics != null) {
                    binding.qualityMetricsCard.setVisibility(View.VISIBLE);
                    RealTimeScanResponse.QualityMetrics metrics = response.qualityMetrics;

                    if (metrics.qualityStatus != null) {
                        binding.qualityStatusText.setText("Overall: " + metrics.qualityStatus);
                    }
                    if (metrics.sharpness != null) {
                        binding.sharpnessText.setText(String.format("Sharpness: %.1f", metrics.sharpness));
                    }
                    if (metrics.brightness != null) {
                        binding.brightnessText.setText(String.format("Brightness: %.1f", metrics.brightness));
                    }
                    if (metrics.contrast != null) {
                        binding.contrastText.setText(String.format("Contrast: %.1f", metrics.contrast));
                    }
                } else {
                    binding.qualityMetricsCard.setVisibility(View.GONE);
                }

                if (response.angleNumber != null) {
                    updateAngleLog(response);
                }

            } else if ("angle_sequence_error".equals(response.status)) {
                if (response.expectedAngle != null) {
                    liveAngleCounter = response.expectedAngle;
                    Toast.makeText(MultiScanActivity.this, "Re-syncing to angle " + liveAngleCounter, Toast.LENGTH_SHORT).show();
                }
            }

            // No matter the response, allow the next frame to be sent.
            canSendFrame = true;
        });
    }

    /**
     * Dynamically adds or updates a TextView in the angle log.
     */
    @SuppressLint("DefaultLocale")
    private void updateAngleLog(RealTimeScanResponse response) {
        binding.angleResultsCard.setVisibility(View.VISIBLE);

        // --- NEW: Clear log container when the server is ready for Angle 1 ---
        // This handles both the first run and any potential server resets.
        binding.angleLogContainer.removeAllViews();

        // --- CORRECTED: Build a detailed, multi-line summary string for the angle ---
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(String.format("Angle %d: %s", response.angleNumber, response.denomination));

        // Add authenticity status if available
        if (response.authenticity instanceof String) {
            logBuilder.append(String.format(" (%s)", response.authenticity));
        }

        logBuilder.append(String.format("\n  FeatureCount: %d, Coverage: %.1f%%",
                response.featureCount != null ? response.featureCount : 0,
                response.coveragePercentage != null ? response.coveragePercentage : 0.0));

        // Add confidence if available
        if (response.confidence != null) {
            logBuilder.append(String.format(", Confidence: %s", response.confidence));
        }
        // --- END CORRECTION ---

        // Create and add the TextView for this log entry
        TextView logView = new TextView(this);
        logView.setText(logBuilder.toString());
        logView.setTextColor(getResources().getColor(android.R.color.black, getTheme()));
        logView.setTextSize(11);
        logView.setPadding(0, 4, 0, 4); // Add some vertical spacing
        binding.angleLogContainer.addView(logView);
    }


    @Override
    public void onScanError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection Error: " + error, Toast.LENGTH_LONG).show();
            canSendFrame = true;
        });
    }

    @Override
    public void onConnectionClosing(String reason) {
        runOnUiThread(() -> Toast.makeText(this, "Connection closing: " + reason, Toast.LENGTH_SHORT).show());
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Disconnect from the WebSocket when the activity is not in the foreground.
        scanManager.disconnect();
        Log.d(TAG, "onPause: WebSocket disconnected.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // In MultiScan, reconnect if camera is initialized and we have permission.
        if (imageAnalysis != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Reset angle counter on reconnect since the server starts fresh
            liveAngleCounter = 1;
            Log.d(TAG, "onResume: Reconnecting WebSocket for multi-angle scan. Reset angle to 1.");
            scanManager.connect(RealTimeScanManager.ENDPOINT_MULTI_ANGLE_SCAN);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        scanManager.disconnect(); // Final cleanup
    }

}
