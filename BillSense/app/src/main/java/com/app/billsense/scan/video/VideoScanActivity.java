package com.app.billsense.scan.video;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityVideoScanBinding;
import com.app.billsense.scan.pojo.RealTimeScanManager;
import com.app.billsense.scan.pojo.RealTimeScanResponse;
import com.app.billsense.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoScanActivity extends AppCompatActivity implements RealTimeScanManager.RealTimeScanListener {
    private static final String TAG = "VideoScanActivity";
    private ActivityVideoScanBinding binding;

    // --- CameraX Components ---
    private VideoCapture<Recorder> videoCapture;
    private ImageAnalysis imageAnalysis; // For live frame analysis
    private Recording currentRecording;
    private ExecutorService cameraExecutor;

    // --- Real-Time Analysis Components ---
    private RealTimeScanManager scanManager;
    private YuvToRgbConverter yuvToRgbConverter;
    private volatile boolean canSendFrame = true;

    // --- State Management ---
    private enum RecordingState { IDLE, RECORDING, STOPPED }
    private RecordingState recordingState = RecordingState.IDLE;
    private Uri lastVideoUri; // Store the URI of the saved video

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
                    startCameraAndWebSocket();
                } else {
                    Toast.makeText(this, "Camera & Audio permissions are required for video.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        binding = ActivityVideoScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Initialization ---
        cameraExecutor = Executors.newSingleThreadExecutor();
        scanManager = new RealTimeScanManager(this);
        yuvToRgbConverter = new YuvToRgbConverter(this);

        requestPermissions();
        // --- MODIFIED: Separate click listener setup ---
        setupClickListeners();
    }

    // --- NEW METHOD to organize click listeners ---
    private void setupClickListeners() {
        binding.startVideoButton.setOnClickListener(v -> toggleRecording());

        binding.showInfoButton.setOnClickListener(v -> {
            boolean isVisible = binding.sideCardsLayout.getVisibility() == View.VISIBLE;
            binding.sideCardsLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            binding.showInfoButton.setText(isVisible ? R.string.show_info : R.string.hide_info);
        });
    }

    private void requestPermissions() {
        // Always request both CAMERA and RECORD_AUDIO for video recording
        // RECORD_AUDIO is still needed on API 33+ for video capture with audio
        activityResultLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
    }

    private void startCameraAndWebSocket() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
                // Connect to the video real-time endpoint
                scanManager.connect(RealTimeScanManager.ENDPOINT_VIDEO_SCAN);
            } catch (Exception e) {
                Log.e(TAG, "CameraX or WebSocket setup failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.videoPreviewView.getSurfaceProvider());

        Recorder recorder = new Recorder.Builder().build();
        videoCapture = VideoCapture.withOutput(recorder);

        // --- ImageAnalysis use case for real-time feed ---
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            if (canSendFrame) { // Send frames regardless of recording state for initial feedback
                canSendFrame = false;
                Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                yuvToRgbConverter.yuvToRgb(image.getImage(), bitmap);
                scanManager.sendFrame(bitmap); // Send frame for live analysis
            }
            image.close();
        });

        cameraProvider.unbindAll();
        // Bind all three use cases: Preview, Video Recording, and Real-time Frame Analysis
        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture, imageAnalysis);
    }

    @SuppressLint("MissingPermission")
    private void toggleRecording() {
        switch (recordingState) {
            case IDLE:
                // Start a new recording session
                startNewRecording();
                break;
            case RECORDING:
                // Stop the current recording
                if (currentRecording != null) {
                    currentRecording.stop();
                }
                break;
            case STOPPED:
                // Launch the post-scan activity with the saved video
                launchPostScanActivity();
                break;
        }
    }

    @SuppressLint("MissingPermission")
    private void startNewRecording() {
        if (videoCapture == null) {
            Log.e(TAG, "VideoCapture not initialized.");
            return;
        }

        binding.videoInstructionsText.setVisibility(View.GONE);
        binding.videoTipsText.setVisibility(View.GONE);

        recordingState = RecordingState.RECORDING;
        binding.startVideoButton.setText(R.string.stop_video);
        binding.startVideoButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red));
        lastVideoUri = null; // Clear previous URI

        String name = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

        MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        PendingRecording pendingRecording = videoCapture.getOutput().prepareRecording(this, outputOptions);
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled();
        }

        currentRecording = pendingRecording.start(ContextCompat.getMainExecutor(this), recordEvent -> {
            if (recordEvent instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                currentRecording = null;

                if (!finalizeEvent.hasError()) {
                    Uri outputUri = finalizeEvent.getOutputResults().getOutputUri();
                    Log.d(TAG, "Video saved to: " + outputUri);
                    Toast.makeText(this, "Video stopped. Press 'Scan Now' to analyze.", Toast.LENGTH_SHORT).show();

                    lastVideoUri = outputUri; // Store the URI for the next activity

                    // --- Change state to STOPPED and update UI ---
                    recordingState = RecordingState.STOPPED;
                    binding.startVideoButton.setText(R.string.scan_now);
                    binding.startVideoButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue));

                } else {
                    Log.e(TAG, "Video capture failed: " + finalizeEvent.getError());
                    resetToIdleState(); // On error, go back to the initial state
                }
            }
        });
    }

    private void launchPostScanActivity() {
        if (lastVideoUri == null) {
            Toast.makeText(this, "No video file found to scan.", Toast.LENGTH_SHORT).show();
            return;
        }

        scanManager.disconnect(); // Disconnect from WebSocket before leaving
        Intent intent = new Intent(this, VideoPostScanActivity.class);
        intent.putExtra("captured_video_uri", lastVideoUri.toString());
        startActivity(intent);
        finish(); // Close this activity
    }

    private void resetToIdleState() {
        binding.videoInstructionsText.setVisibility(View.VISIBLE);
        binding.videoTipsText.setVisibility(View.VISIBLE);

        recordingState = RecordingState.IDLE;
        lastVideoUri = null;
        binding.startVideoButton.setEnabled(true);
        binding.startVideoButton.setText(R.string.start_video);
        binding.startVideoButton.setBackgroundColor(ContextCompat.getColor(this, R.color.green));
        binding.sideCardsLayout.setVisibility(View.GONE);
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController == null) return;
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

    // --- RealTimeScanManager.RealTimeScanListener Implementation ---

    @Override
    public void onConnectionOpen() {
        runOnUiThread(() -> Toast.makeText(this, "Live analysis started.", Toast.LENGTH_SHORT).show());
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    public void onScanUpdate(RealTimeScanResponse response) {
        runOnUiThread(() -> {
            if (response == null) {
                canSendFrame = true;
                return;
            }

            // Only make the side panel visible if the "Show Info" button wants it to be.
            // It will appear automatically on the first message if it's not explicitly hidden.
            if (binding.sideCardsLayout.getVisibility() == View.GONE && binding.showInfoButton.getText().toString().equalsIgnoreCase(getString(R.string.hide_info))) {
                binding.sideCardsLayout.setVisibility(View.VISIBLE);
            }

            // --- FULLY UPDATED UI POPULATION ---

            // Bill Info Card
            if (response.framesProcessed != null) {
                binding.framesProcessedText.setText("Frames: " + response.framesProcessed);
            }
            if (response.denomination != null) {
                binding.denominationText.setText("Denomination: " + response.denomination);
            }
            // The `authenticity` field in the log is a String, not an object.
            if (response.authenticity instanceof String) {
                binding.authenticityText.setText("Authenticity: " + response.authenticity);
            }
            if (response.currentConfidence != null) {
                binding.currentConfidenceText.setText(String.format("Current Conf: %.1f%%", response.currentConfidence));
            }
            if (response.bestConfidence != null) {
                binding.bestConfidenceText.setText(String.format("Best Conf: %.1f%%", response.bestConfidence));
            }
            if (response.featureCount != null) {
                binding.featuresText.setText("Features: " + response.featureCount);
            }

            // Quality Metrics Card
            if (response.qualityMetrics != null) {
                RealTimeScanResponse.QualityMetrics metrics = response.qualityMetrics;
                if (metrics.qualityStatus != null && metrics.overallQuality != null) {
                    binding.qualityStatusText.setText(String.format("Status: %s (%.1f)", metrics.qualityStatus, metrics.overallQuality));
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
            }

            if (response.qualityFeedback != null && !response.qualityFeedback.isEmpty()) {
                binding.qualityFeedbackText.setText("Feedback: " + String.join(", ", response.qualityFeedback));
            } else {
                binding.qualityFeedbackText.setText("Feedback: N/A");
            }

            canSendFrame = true; // Allow next frame to be sent
        });
    }

    @Override
    public void onScanError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "WebSocket Error: " + error);
            canSendFrame = true; // Always allow retrying
        });
    }

    @Override
    public void onConnectionClosing(String reason) {
        runOnUiThread(() -> Log.i(TAG, "WebSocket Closing: " + reason));
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Disconnect from the WebSocket when the activity is not in the foreground.
        scanManager.disconnect();
        Log.d(TAG, "onPause: WebSocket disconnected.");

        // Also, if recording is in progress, stop it to prevent inconsistent states.
        if (recordingState == RecordingState.RECORDING && currentRecording != null) {
            currentRecording.stop();
            // The on-stop logic in startNewRecording() will handle the rest.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // In VideoScan, reconnect only if camera is initialized and we have permission.
        if (imageAnalysis != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onResume: Reconnecting WebSocket for video scan.");
            scanManager.connect(RealTimeScanManager.ENDPOINT_VIDEO_SCAN);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (scanManager != null) {
            scanManager.disconnect(); // Final cleanup
        }
    }
}
