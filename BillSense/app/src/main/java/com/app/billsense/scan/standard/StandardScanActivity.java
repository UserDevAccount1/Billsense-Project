package com.app.billsense.scan.standard;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
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
import androidx.exifinterface.media.ExifInterface;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityStandardScanBinding;
import com.app.billsense.databinding.DialogFeelBillBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.scan.multi.MultiPostScanActivity;
import com.app.billsense.scan.pojo.RealTimeScanManager;
import com.app.billsense.scan.pojo.RealTimeScanResponse;
import com.app.billsense.utils.FBStorageUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.YuvToRgbConverter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("UnsafeOptInUsageError")
public class StandardScanActivity extends AppCompatActivity implements RealTimeScanManager.RealTimeScanListener {
    private ActivityStandardScanBinding binding;
    private static final String TAG = "==StandardScanActivity";
    private FBUtils fbUtils;
    private String userId;
    private Handler mainThreadHandler;

    // --- UI and State Management ---
    private enum UiState { READY_TO_CAPTURE, IMAGE_PREVIEW }
    private UiState currentUiState = UiState.READY_TO_CAPTURE;
    private Uri capturedImageUri; // Will hold the URI of the single captured image
    private boolean areInfoCardsVisible = true; // show the live-analysis panel by default

    // --- Cold-start handling: warm the server + auto-retry the WebSocket ---
    private int wsRetryCount = 0;
    private static final int MAX_WS_RETRIES = 6;
    private final android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // --- CameraX Components ---
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private YuvToRgbConverter yuvToRgbConverter;

    // --- Real-Time Components ---
    private RealTimeScanManager scanManager;
    private volatile boolean canSendFrame = true;
    private RealTimeScanResponse lastSuccessfulResponse; // Store the last good response from the server

    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        binding = ActivityStandardScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialization
        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();
        mainThreadHandler = new Handler(Looper.getMainLooper());
        cameraExecutor = Executors.newSingleThreadExecutor();
        yuvToRgbConverter = new YuvToRgbConverter(this);
        scanManager = new RealTimeScanManager(this);

        initPermissionLauncher();
        setupButtonClickListeners();
        setupHideShowInfoButton();
        updateButtonAndUiState();

        requestCameraPermissionThenLaunchCamera();
    }

    // --- Lifecycle and Permissions ---
    private void initPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startCameraAndWebSocket();
            } else {
                showPermissionDeniedDialog();
            }
        });
    }

    private void requestCameraPermissionThenLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraAndWebSocket();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraAndWebSocket() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
                warmUpServer(); // wake Cloud Run (scale-to-zero) before connecting
                // Connect to the real-time standard scan endpoint
                scanManager.connect(RealTimeScanManager.ENDPOINT_STANDARD_SCAN);
            } catch (Exception e) {
                Log.e(TAG, "CameraX or WebSocket setup failed", e);
                Toast.makeText(this, "Failed to initialize scanner.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only reconnect if we are in the live scanning state, the camera is initialized,
        // and we have permission. The imageAnalysis check prevents a race on first launch.
        if (currentUiState == UiState.READY_TO_CAPTURE && imageAnalysis != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onResume: Reconnecting WebSocket for live scan.");
            warmUpServer();
            scanManager.connect(RealTimeScanManager.ENDPOINT_STANDARD_SCAN);
        }
    }

    /**
     * Wake the Cloud Run ML service (scale-to-zero) so the real-time WebSocket
     * upgrade succeeds and models start loading before the user points at a bill.
     * Fire-and-forget; failures are non-fatal.
     */
    private void warmUpServer() {
        try {
            okhttp3.OkHttpClient c = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Request r = new okhttp3.Request.Builder()
                    .url(com.app.billsense.BuildConfig.API_BASE_URL + "/api/health").build();
            c.newCall(r).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                    Log.w(TAG, "Warm-up failed: " + e.getMessage());
                }
                @Override public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response resp) {
                    Log.d(TAG, "Warm-up ok: " + resp.code());
                    resp.close();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Warm-up error: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        retryHandler.removeCallbacksAndMessages(null);
        cameraExecutor.shutdown();
        scanManager.disconnect(); // Final cleanup
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Disconnect from the WebSocket when the activity is not in the foreground.
        // This is the key fix for the EOFException.
        scanManager.disconnect();
        Log.d(TAG, "onPause: WebSocket disconnected.");
    }

    // --- CameraX Setup ---
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

                scanManager.sendFrame(bitmap);
            }
            image.close();
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis);
    }


    // --- Button Clicks and UI State ---
    private void setupButtonClickListeners() {
        binding.scanButton.setOnClickListener(v -> {
            if (currentUiState == UiState.READY_TO_CAPTURE) {
                // First click: CAPTURE a high-res image
                captureHighResImage();
            } else {
                // Second click: SCAN (proceed to the next activity)
                launchPostScanActivity();
            }
        });

        binding.retakeButton.setOnClickListener(v -> switchToReadyToCaptureState());
        binding.viewButton.setOnClickListener(view -> showFeelBillDialog());
    }

    private void captureHighResImage() {
        if (imageCapture == null) return;
        showProgressDialog(this);

        File photoFile = new File(getCacheDir(), "standard_scan_" + System.currentTimeMillis() + ".jpeg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                capturedImageUri = outputFileResults.getSavedUri();
                Log.d(TAG, "High-res image saved: " + capturedImageUri);
                runOnUiThread(() -> {
                    hideProgressDialog();
                    currentUiState = UiState.IMAGE_PREVIEW;
                    displayImageFromUri(capturedImageUri);
                    updateButtonAndUiState();
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: ", exception);
                runOnUiThread(() -> {
                    hideProgressDialog();
                    Toast.makeText(StandardScanActivity.this, "Capture failed.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateButtonAndUiState() {
        runOnUiThread(() -> {
            if (currentUiState == UiState.READY_TO_CAPTURE) {
                binding.cameraPreviewView.setVisibility(View.VISIBLE);
                binding.capturedImageView.setVisibility(View.GONE);
                binding.scanButton.setText(getString(R.string.capture));
                binding.retakeButton.setVisibility(View.GONE);
                binding.scanButton.animate().translationX(0).setDuration(200).start();
            } else { // IMAGE_PREVIEW
                binding.cameraPreviewView.setVisibility(View.GONE);
                binding.capturedImageView.setVisibility(View.VISIBLE);
                binding.scanButton.setText(getString(R.string.scan));
                binding.retakeButton.setVisibility(View.VISIBLE);
                animateButtonsSideBySide();
            }
        });
    }

    private void switchToReadyToCaptureState() {
        currentUiState = UiState.READY_TO_CAPTURE;
        capturedImageUri = null;
        binding.capturedImageView.setImageDrawable(null);
        updateButtonAndUiState();
    }

    private void animateButtonsSideBySide() {
        float buttonWidth = binding.scanButton.getWidth();
        if (buttonWidth == 0) {
            binding.scanButton.post(this::animateButtonsSideBySide);
            return;
        }
        float margin = getResources().getDimension(R.dimen.activity_horizontal_margin) / 2;
        float translation = (buttonWidth / 2f) + margin;
        binding.scanButton.animate().translationX(translation).setDuration(200).start();
        binding.retakeButton.animate().translationX(-translation).setDuration(200).start();
    }

    private void displayImageFromUri(Uri imageUri) {
        try (InputStream imageStream = getContentResolver().openInputStream(imageUri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
            bitmap = rotateBitmapIfRequired(this, imageUri, bitmap);
            binding.capturedImageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load bitmap from URI.", e);
        }
    }

    private void launchPostScanActivity() {
        if (capturedImageUri == null) {
            Toast.makeText(this, "No image was captured.", Toast.LENGTH_SHORT).show();
            return;
        }
        scanManager.disconnect();
        // We now pass a single image path to the PostScanActivity
        Intent intent = new Intent(this, StandardPostScanActivity.class);
        // Pass the URI of the single captured image
        intent.putExtra("captured_image_uri", capturedImageUri.toString());
        startActivity(intent);
        finish();
    }

    // --- WebSocket Listener Callbacks ---
    @Override
    public void onConnectionOpen() {
        wsRetryCount = 0;       // connected — clear cold-start retry state
        canSendFrame = true;    // unstick frame sending
        runOnUiThread(() -> Toast.makeText(this, "Live analysis started...", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onScanUpdate(RealTimeScanResponse response) {
        if (currentUiState == UiState.READY_TO_CAPTURE) { // Only update UI during live preview
            lastSuccessfulResponse = response; // Store the latest good data
            runOnUiThread(() -> updateSidebarUI(response));
        }
        canSendFrame = true; // Allow the next frame to be sent
    }

    @Override
    public void onScanError(String error) {
        canSendFrame = true; // Allow trying again
        // Cold-start: the WS upgrade can fail while Cloud Run spins up. Auto-retry
        // (with warm-up) instead of dead-ending on a one-shot "Scan Error".
        boolean connissue = error != null && error.toLowerCase().contains("connect");
        if (connissue && currentUiState == UiState.READY_TO_CAPTURE && wsRetryCount < MAX_WS_RETRIES) {
            wsRetryCount++;
            runOnUiThread(() -> Toast.makeText(this,
                    "Warming up scanner… (" + wsRetryCount + "/" + MAX_WS_RETRIES + ")", Toast.LENGTH_SHORT).show());
            warmUpServer();
            retryHandler.postDelayed(() -> {
                if (currentUiState == UiState.READY_TO_CAPTURE) {
                    scanManager.connect(RealTimeScanManager.ENDPOINT_STANDARD_SCAN);
                }
            }, 4000);
        } else {
            runOnUiThread(() -> Toast.makeText(this, "Scan Error: " + error, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onConnectionClosing(String reason) {
        runOnUiThread(() -> Toast.makeText(this, "Connection closing: " + reason, Toast.LENGTH_SHORT).show());
    }

    // --- UI Update Methods ---
    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void updateSidebarUI(RealTimeScanResponse response) {
        if (response == null || response.status == null) return;

        // --- Update Confidence Score Card ---
        if (response.confidenceScore != null) {
            int confidence = response.confidenceScore.intValue();
            binding.confidencePercentageText.setText(confidence + "%");
            binding.confidenceProgressBar.setProgress(confidence);
        }

        if (response.authenticity instanceof String) {
            binding.confidenceBillStatusText.setText((String) response.authenticity);
        }

        if (response.isGenuine != null) {
            if (response.isGenuine) {
                binding.confidenceProgressBar.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.custom_progress_bar_green));
            } else {
                binding.confidenceProgressBar.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.custom_progress_bar_red));
            }
        }

        // --- Update Bill Info Card ---
        if (response.denomination != null) {
            binding.currencyInfoText.setText("Currency: PHP"); // Assuming PHP for now
            binding.denominationInfoText.setText("Denomination: " + response.denomination);
        } else {
            binding.denominationInfoText.setText("Denomination: N/A");
        }

        // Update feature count on the Bill Info card
        if (response.featureCount != null && response.totalExpectedFeatures != null) {
            binding.serialNoText.setText(String.format("Features: %d / %d", response.featureCount, response.totalExpectedFeatures));
        } else {
            binding.serialNoText.setText("Features: N/A");
        }

        // --- Update Feature Detection Card ---
        binding.featuresListLayout.removeAllViews(); // Clear old features
        if (response.featuresDetected != null && !response.featuresDetected.isEmpty()) {
            binding.featureDetectionCard.setVisibility(View.VISIBLE);
            for (String feature : response.featuresDetected) {
                TextView featureView = new TextView(this);
                // Prepend a checkmark for a better visual cue
                featureView.setText("✓ " + feature.replace("_", " ").toUpperCase());
                featureView.setTextColor(Color.BLACK);
                featureView.setTextSize(11f);
                binding.featuresListLayout.addView(featureView);
            }
        } else {
            binding.featureDetectionCard.setVisibility(View.GONE);
        }


        // --- Update Quality Metrics Card ---
        if (response.qualityMetrics != null) {
            binding.qualityMetricsCard.setVisibility(View.VISIBLE);
            RealTimeScanResponse.QualityMetrics metrics = response.qualityMetrics;

            if (metrics.qualityStatus != null) {
                binding.qualityStatusText.setText("Status: " + metrics.qualityStatus);
            }
            if (metrics.sharpness != null) {
                binding.sharpnessText.setText(String.format("Sharpness: %.1f", metrics.sharpness));
            }
            if (metrics.brightness != null) {
                binding.brightnessText.setText(String.format("Brightness: %.1f", metrics.brightness));
            }
        } else {
            binding.qualityMetricsCard.setVisibility(View.GONE);
        }
    }



    // --- Utility and Dialog Methods ---

    public static Bitmap rotateBitmapIfRequired(Context context, @NonNull Uri imageUri, @NonNull Bitmap bitmap) {
        try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
            if (is == null) return bitmap;
            ExifInterface exifInterface = new ExifInterface(is);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                default: return bitmap;
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            Log.e(TAG, "Could not get orientation of image", e);
            return bitmap;
        }
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (c != null) {
            c.hide(WindowInsetsCompat.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission Denied")
                .setMessage("Camera permission is required. Please enable it in app settings.")
                .setCancelable(false)
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    private void setupHideShowInfoButton() {
        binding.infoSidebarScrollView.setVisibility(areInfoCardsVisible ? View.VISIBLE : View.GONE);
        binding.hideInfoButton.setText(getString(areInfoCardsVisible ? R.string.hide_info : R.string.show_info));
        binding.hideInfoButton.setOnClickListener(v -> {
            areInfoCardsVisible = !areInfoCardsVisible;
            binding.infoSidebarScrollView.setVisibility(areInfoCardsVisible ? View.VISIBLE : View.GONE);
            binding.hideInfoButton.setText(getString(areInfoCardsVisible ? R.string.hide_info : R.string.show_info));
        });
    }

    private void showFeelBillDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        DialogFeelBillBinding detailsBinding = DialogFeelBillBinding.inflate(getLayoutInflater());
        dialog.setContentView(detailsBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(lp);
        }
        dialog.show();
    }
}

