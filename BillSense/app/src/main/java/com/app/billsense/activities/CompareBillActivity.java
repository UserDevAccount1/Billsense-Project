package com.app.billsense.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.app.billsense.utils.Utils.showToast;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.app.billsense.databinding.ActivityCompareBillBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Bills;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.bumptech.glide.Glide;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.github.dhaval2404.imagepicker.constant.ImageProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Locale;

public class CompareBillActivity extends AppCompatActivity {
    private ActivityCompareBillBinding binding;
    private FBUtils fbUtils;
    private String userId;
    private boolean showControls = true;
    private float currentImageAlpha = 1.0f;
    private float userImageAlpha = 1.0f;
    private float originalImageAlpha = 1.0f;
    private Guideline verticalGuideline;
    private View dividerLineHandle;
    private FrameLayout leftImageContainer;
    private FrameLayout rightImageContainer;
    private ConstraintLayout mainLayout;
    private ImageView userImageViewInstance;
    private ImageView originalImageViewInstance;
    private ImageView activeImageView;
    private float currentRotationAngle = 0f;
    private float userImageRotation = 0f;
    private float originalImageRotation = 0f;
    private static final float ROTATION_INCREMENT = 90f;
    private ActivityResultLauncher<Intent> imagePickerActivityResultLauncher;
    private Uri imageUri;
    private float dX; // For tracking touch offset
    private boolean isSwitchOn = false; // To track the state of the switch_bill
    private boolean previousSwitchState = false;
    // Store original constraints to restore them
    private ConstraintSet originalConstraints = new ConstraintSet();
    private boolean isOverlayActive = false;
    private static final float LEFT_EDGE_THRESHOLD = 0.45f;
    private static final float RIGHT_EDGE_THRESHOLD = 0.55f;
    private static final float EXIT_THRESHOLD_OFFSET = 0.01f;
    private ArrayList<Bills> billsArrayList = new ArrayList<>();
    private int currentBillIndex = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        binding = ActivityCompareBillBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();

        // Initialize the ActivityResultLauncher for Image picker
        imagePickerActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        imageUri = result.getData().getData();
                        binding.imageViewUserUploaded.setImageURI(imageUri);
                    } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                        showToast(CompareBillActivity.this, ImagePicker.getError(result.getData()));
                        showUploadImagePromptDialog();
                        imageUri = null;
                    } else {
                        showToast(CompareBillActivity.this, "Task Cancelled");
                        showUploadImagePromptDialog();
                        imageUri = null;
                    }
                }
        );

        // Assign the instances from binding
        mainLayout = binding.mainCompareLayout;
        verticalGuideline = binding.verticalGuideline;
        dividerLineHandle = binding.dividerLineHandle;
        leftImageContainer = binding.leftImageContainer;
        rightImageContainer = binding.rightImageContainer;
        userImageViewInstance = binding.imageViewUserUploaded;
        originalImageViewInstance = binding.imageViewOriginal;

        // Clone the original constraints once
        originalConstraints.clone(mainLayout);
        previousSwitchState = isSwitchOn;
        // Apply initial alpha to the user image view
        applyAlphaSettings();
        setListeners();
        getAllBills();
        initControlsBtn();
        initOpacityButtonDialog();
        initSwitchBillViewSwap();
        initRotateButton();
        // Setup draggable divider
        setupDraggableDivider();
        makeFullScreenOverlay(leftImageContainer, rightImageContainer, RIGHT_EDGE_THRESHOLD);
        initNextBillButton();

        if (imageUri == null) {
            showUploadImagePromptDialog();
        }

    }

    private void initNextBillButton() {
        binding.nextBillBtn.setOnClickListener(v -> {
            if (activeImageView == originalImageViewInstance) {
                if (billsArrayList.isEmpty()) {
                    showToast(CompareBillActivity.this, "No bills available to display.");
                    return;
                }

                if (currentBillIndex < billsArrayList.size() - 1) {
                    currentBillIndex++;
                    loadBillImage(currentBillIndex);
                } else {
                    // Optionally, loop back to the first bill
                    currentBillIndex = 0;
                    loadBillImage(currentBillIndex);
                    showToast(CompareBillActivity.this, "Reached the last bill. Looping back.");

                }
            } else if (activeImageView == userImageViewInstance) {
                showToast(CompareBillActivity.this, "Upload Next Bill");
                ImagePicker.with(CompareBillActivity.this)
                        .provider(ImageProvider.BOTH)
                        .galleryMimeTypes(new String[]{
                                "image/png",
                                "image/jpg",
                                "image/jpeg"
                        })
                        .crop()
                        .compress(1024)
                        .maxResultSize(1080, 1080)
                        .createIntent(intent1 -> {
                            imagePickerActivityResultLauncher.launch(intent1);
                            return null;
                        });
            }
            updateNextBillButtonState(); // Update button state after click
        });

        // Set initial state of the button
        updateNextBillButtonState();
    }

    private void loadBillImage(int index) {
        if (billsArrayList.isEmpty()) {
            binding.imageViewOriginal.setImageDrawable(null); // Clear if no bills
            showToast(CompareBillActivity.this, "No bills to display.");
            return;
        }

        if (index >= 0 && index < billsArrayList.size()) {
            Bills billToLoad = billsArrayList.get(index);
            if (billToLoad != null && billToLoad.getFrontImage() != null && !billToLoad.getFrontImage().isEmpty()) {
                Glide.with(CompareBillActivity.this)
                        .load(billToLoad.getFrontImage()) // Assuming Bills model has getImageUrl()
                        .into(binding.imageViewOriginal);
            } else {
                // Handle case where bill or its URL is invalid
                //binding.imageViewOriginal.setImageResource(R.drawable.ic_error_image); // Show error image
                showToast(CompareBillActivity.this, "Bill data or image URL is invalid.");
            }
        } else {
            // This case should ideally not be reached if logic is correct, but good for safety
            showToast(CompareBillActivity.this, "Invalid bill index.");
        }
    }

    private void updateNextBillButtonState() {
        if (activeImageView == originalImageViewInstance) {
            if (billsArrayList.isEmpty() || currentBillIndex >= billsArrayList.size() - 1) {
                binding.nextBillBtn.setEnabled(false);
                binding.nextBillBtn.setAlpha(0.5f); // Visually indicate disabled state
            } else {
                binding.nextBillBtn.setEnabled(true);
                binding.nextBillBtn.setAlpha(1.0f);
            }
        } else if (activeImageView == userImageViewInstance) {
            binding.nextBillBtn.setEnabled(true);
            binding.nextBillBtn.setAlpha(1.0f);
        }
    }

    private void getAllBills() {
        fbUtils.getAllDataFromPath(fbUtils.BILLS_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                billsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Bills bills = snapshot.getValue(Bills.class);
                    billsArrayList.add(bills);
                }
                if (!billsArrayList.isEmpty()) {
                    currentBillIndex = 0; // Reset to first bill
                    loadBillImage(currentBillIndex);
                    updateNextBillButtonState(); // Update button state after loading
                } else {
                    // Handle case where no bills are found after fetch
                    binding.imageViewOriginal.setImageDrawable(null); // Clear image view
                    showToast(CompareBillActivity.this, "No bills found.");
                    updateNextBillButtonState(); // Update button state
                }
            }

            @Override
            public void onDataNotFound() {
                billsArrayList.clear(); // Ensure list is clear
                binding.imageViewOriginal.setImageDrawable(null);
                showToast(CompareBillActivity.this, "No Bills Found from path.");
                updateNextBillButtonState();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                showToast(CompareBillActivity.this, errorMessage);
                updateNextBillButtonState();
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableDivider() {
        dividerLineHandle.setOnTouchListener((view, event) -> {
            ConstraintLayout.LayoutParams guidelineParams = (ConstraintLayout.LayoutParams) verticalGuideline.getLayoutParams();
            final int parentWidth = ((ViewGroup) view.getParent()).getWidth();
            float currentGuidelineBias = guidelineParams.guidePercent;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = view.getX() - event.getRawX();
                    if (isOverlayActive) {
                        // We are in an overlay. Restore split view constraints for images.
                        restoreSplitViewConstraints(); // This sets isOverlayActive = false

                        // Now, adjust the guideline to be just OUTSIDE the overlay trigger zone
                        // so that the subsequent move can correctly drag into split view.
                        if (currentGuidelineBias <= LEFT_EDGE_THRESHOLD) {
                            guidelineParams.guidePercent = LEFT_EDGE_THRESHOLD + EXIT_THRESHOLD_OFFSET;
                        } else if (currentGuidelineBias >= RIGHT_EDGE_THRESHOLD) {
                            guidelineParams.guidePercent = RIGHT_EDGE_THRESHOLD - EXIT_THRESHOLD_OFFSET;
                        }
                        verticalGuideline.setLayoutParams(guidelineParams);
                        // isOverlayActive is now false, so subsequent MOVE will behave as split view drag
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX;
                    float targetBias = newX / parentWidth;
                    targetBias = Math.max(0f, Math.min(1f, targetBias)); // Clamp to parent bounds

                    if (isOverlayActive) {
                        // If overlay is active, user must drag OUT of the threshold to restore split view
                        boolean draggingOutOfLeftOverlay = currentGuidelineBias <= LEFT_EDGE_THRESHOLD && targetBias > LEFT_EDGE_THRESHOLD;
                        boolean draggingOutOfRightOverlay = currentGuidelineBias >= RIGHT_EDGE_THRESHOLD && targetBias < RIGHT_EDGE_THRESHOLD;

                        if (draggingOutOfLeftOverlay || draggingOutOfRightOverlay) {
                            restoreSplitViewConstraints(); // Sets isOverlayActive = false
                            guidelineParams.guidePercent = targetBias; // Apply the new dragged position
                            verticalGuideline.setLayoutParams(guidelineParams);
                            // updateOverlayState will be called below and will confirm no overlay
                        } else {
                            // Still dragging within or further into an active overlay zone.
                            // Do NOT change guideline position. It's pinned by makeFullScreenOverlay.
                            return true; // Consume event, guideline stays put
                        }
                    } else {
                        // Not in overlay, normal split view drag. Update guideline.
                        guidelineParams.guidePercent = targetBias;
                        verticalGuideline.setLayoutParams(guidelineParams);
                    }

                    updateOverlayState(targetBias); // Check if the new position triggers an overlay
                    return true;
            }
            return false;
        });
    }

    private void updateOverlayState(float currentBias) {
        boolean switchStateChanged = (previousSwitchState != isSwitchOn);
        previousSwitchState = isSwitchOn;

        View topViewForOverlay = null;
        View bottomViewForOverlay = null;
        float edgeToPinGuideline = currentBias; // Default to current, will be overridden by specific edge

        if (currentBias <= LEFT_EDGE_THRESHOLD) { // User dragged to the far left
            topViewForOverlay = rightImageContainer;    // Reveal right container fully
            bottomViewForOverlay = leftImageContainer;
            edgeToPinGuideline = LEFT_EDGE_THRESHOLD;
            if (switchStateChanged || !isOverlayActive || topViewForOverlay.getTranslationZ() < 1f) {
                makeFullScreenOverlay(topViewForOverlay, bottomViewForOverlay, edgeToPinGuideline);
            }
        } else if (currentBias >= RIGHT_EDGE_THRESHOLD) { // User dragged to the far right
            topViewForOverlay = leftImageContainer;     // Reveal left container fully
            bottomViewForOverlay = rightImageContainer;
            edgeToPinGuideline = RIGHT_EDGE_THRESHOLD;
            if (switchStateChanged || !isOverlayActive || topViewForOverlay.getTranslationZ() < 1f) {
                makeFullScreenOverlay(topViewForOverlay, bottomViewForOverlay, edgeToPinGuideline);
            }
        } else { // In split view
            if (isOverlayActive) {
                restoreSplitViewConstraints();
            }
            // Ensure Z-order is reset for normal split view
            leftImageContainer.setTranslationZ(0f);
            rightImageContainer.setTranslationZ(0f);
        }
    }

    private void makeFullScreenOverlay(View topViewVisual, View bottomViewVisual, float edgeBiasToPinGuideline) {
        ConstraintSet set = new ConstraintSet();
        set.clone(mainLayout);

        // Make both views full screen
        set.connect(topViewVisual.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
        set.connect(topViewVisual.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
        set.connect(topViewVisual.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0);
        set.connect(topViewVisual.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
        set.constrainWidth(topViewVisual.getId(), ConstraintSet.MATCH_CONSTRAINT);
        set.constrainHeight(topViewVisual.getId(), ConstraintSet.MATCH_CONSTRAINT);

        set.connect(bottomViewVisual.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
        set.connect(bottomViewVisual.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
        set.connect(bottomViewVisual.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0);
        set.connect(bottomViewVisual.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
        set.constrainWidth(bottomViewVisual.getId(), ConstraintSet.MATCH_CONSTRAINT);
        set.constrainHeight(bottomViewVisual.getId(), ConstraintSet.MATCH_CONSTRAINT);

        set.applyTo(mainLayout);

        topViewVisual.setTranslationZ(1f);
        bottomViewVisual.setTranslationZ(0f);

        ConstraintLayout.LayoutParams guidelineParams = (ConstraintLayout.LayoutParams) verticalGuideline.getLayoutParams();
        guidelineParams.guidePercent = edgeBiasToPinGuideline;
        verticalGuideline.setLayoutParams(guidelineParams);

        isOverlayActive = true;
    }

    private void restoreSplitViewConstraints() {
        // Apply the original constraints for the image containers,
        // making them dependent on the guideline again.
        originalConstraints.applyTo(mainLayout); // This is the key for restoring image view constraints

        // Reset Z-order for image containers
        leftImageContainer.setTranslationZ(0f);
        rightImageContainer.setTranslationZ(0f);

        isOverlayActive = false; // Set overlay state to inactive

    }

    private void showUploadImagePromptDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Upload Image for Comparison")
                .setMessage("To start comparing, please upload your bill image. This feature requires an image to proceed.\n" +
                        "Note: For best results, please capture images in landscape mode when using the camera.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    ImagePicker.with(CompareBillActivity.this)
                            .provider(ImageProvider.BOTH)
                            .galleryMimeTypes(new String[]{
                                    "image/png",
                                    "image/jpg",
                                    "image/jpeg"
                            })
                            .crop()
                            .compress(1024)
                            .maxResultSize(1080, 1080)
                            .createIntent(intent1 -> {
                                imagePickerActivityResultLauncher.launch(intent1);
                                return null;
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void initRotateButton() {
        binding.rotateBtn.setOnClickListener(view -> {

            if (activeImageView == null) {
                showToast(this, "No image selected to rotate.");
                return;
            }

            // Determine which rotation variable to update and apply
            if (activeImageView == userImageViewInstance) {
                userImageRotation += ROTATION_INCREMENT;
                if (userImageRotation >= 360) {
                    userImageRotation = 0f; // Cycle back
                }
                userImageViewInstance.animate().rotation(userImageRotation).setDuration(300).start();
            } else if (activeImageView == originalImageViewInstance) {
                originalImageRotation += ROTATION_INCREMENT;
                if (originalImageRotation >= 360) {
                    originalImageRotation = 0f; // Cycle back
                }
                originalImageViewInstance.animate().rotation(originalImageRotation).setDuration(300).start();
            }

//            currentRotationAngle += ROTATION_INCREMENT;
//            // Ensure the angle stays within 0-359 degrees if you want to cycle
//            if (currentRotationAngle >= 360) {
//                currentRotationAngle -= 360;
//            }
//
//            // Apply rotation with animation
//            if (activeImageView != null){
//            activeImageView.animate().rotation(currentRotationAngle).setDuration(300).start();
//            }
            //originalImageViewInstance.animate().rotation(currentRotationAngle).setDuration(300).start();
        });
    }

    private void initSwitchBillViewSwap() {
        if (binding.switchBill.isChecked()) {
            activeImageView = originalImageViewInstance;
            // Optionally, also make the original image container "primary" if your UI implies it
            // e.g., slightly highlight it or ensure it's the one affected by makeFullScreenOverlay
            // if the switch is the primary way to choose full screen.
        } else {
            activeImageView = userImageViewInstance;
        }
        binding.switchBill.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSwitchOn = isChecked; // Update current state

            if (isChecked) {
                // Switch is ON - Original Image is active
                activeImageView = originalImageViewInstance;
                showToast(CompareBillActivity.this, "Original Bill Selected");

                // If your UI also visually swaps which image is "primary" (e.g., in a split view
                // if one side is visually more prominent or if full-screen overlay logic
                // should favor this one), you might add logic here.
                // For now, we're just focusing on which ImageView is targeted by controls.

            } else {
                // Switch is OFF - User Uploaded Image is active
                activeImageView = userImageViewInstance;
                showToast(CompareBillActivity.this, "User Uploaded Bill Selected");
            }

            FrameLayout currentLeftParent = (FrameLayout) userImageViewInstance.getParent();
            FrameLayout currentRightParent = (FrameLayout) originalImageViewInstance.getParent();
            if (currentLeftParent != null) currentLeftParent.removeAllViews();
            if (currentRightParent != null) currentRightParent.removeAllViews();

            if (isChecked) { // Original on left, User on right
                leftImageContainer.addView(originalImageViewInstance);
                rightImageContainer.addView(userImageViewInstance);
            } else { // User on left, Original on right
                leftImageContainer.addView(userImageViewInstance);
                rightImageContainer.addView(originalImageViewInstance);
            }
            applyAlphaSettings();

            ConstraintLayout.LayoutParams guidelineParams = (ConstraintLayout.LayoutParams) verticalGuideline.getLayoutParams();
            // updateOverlayState will use the new isSwitchOn and the previousSwitchState
            // (which is now different from isSwitchOn) to detect a change if needed.
            updateOverlayState(guidelineParams.guidePercent);
            updateNextBillButtonState();
        });
    }


    private void applyAlphaSettings() {
        if (activeImageView != null) {
            if (activeImageView == userImageViewInstance) {
                activeImageView.setAlpha(userImageAlpha);
            } else if (activeImageView == originalImageViewInstance) {
                activeImageView.setAlpha(originalImageAlpha);
            }
//            activeImageView.setAlpha(currentImageAlpha);
        }
    }

    private void initOpacityButtonDialog() {
        binding.opacityBtn.setOnClickListener(view -> {
            showOpacityDialog();
        });
    }

    private void showOpacityDialog() {
        if (activeImageView == null) {
            showToast(this, "Please select an image first to adjust its opacity.");
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        // We won't set a title on the builder itself to save vertical space.
        // The title can be part of our custom layout if needed.

        // --- Create Views for the Dialog ---
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        float scale = getResources().getDisplayMetrics().density;
        int paddingInPx = (int) (16 * scale + 0.5f); // 16dp
        int smallPaddingInPx = (int) (8 * scale + 0.5f); // 8dp

        dialogLayout.setPadding(paddingInPx, smallPaddingInPx, paddingInPx, smallPaddingInPx);

        // TextView for "Adjust Opacity" title (optional, if you want it in the dialog content)
        TextView dialogTitleText = new TextView(this);
        dialogTitleText.setText("Adjust Opacity");
        dialogTitleText.setTextSize(18f); // Slightly smaller title
        dialogTitleText.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.CENTER_HORIZONTAL;
        titleParams.bottomMargin = smallPaddingInPx;
        dialogTitleText.setLayoutParams(titleParams);
        // dialogLayout.addView(dialogTitleText); // Uncomment if you want this title

        // TextView for percentage display
        final TextView opacityPercentageText = new TextView(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.END;
        textParams.bottomMargin = (int) (4 * scale + 0.5f); // Reduced margin
        opacityPercentageText.setLayoutParams(textParams);
        opacityPercentageText.setTypeface(null, Typeface.ITALIC);

        // SeekBar
        final SeekBar opacitySeekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        opacitySeekBar.setLayoutParams(seekBarParams);
        opacitySeekBar.setMax(100);

        final Runnable updateOpacityUi = () -> {
            int percentage = 0;
            float alphaToApply = 0.0f;

            if (activeImageView == null) {
                opacityPercentageText.setText("N/A");
                return;
            }

            if (activeImageView == userImageViewInstance) {
                percentage = (int) (userImageAlpha * 100);
                alphaToApply = userImageAlpha;
            } else if (activeImageView == originalImageViewInstance) {
                percentage = (int) (originalImageAlpha * 100);
                alphaToApply = originalImageAlpha;
            }

            opacityPercentageText.setText(String.format(Locale.getDefault(), "%d%%", percentage));
            activeImageView.setAlpha(alphaToApply);
        };

        if (activeImageView == userImageViewInstance) {
            opacitySeekBar.setProgress((int) (userImageAlpha * 100));
        } else if (activeImageView == originalImageViewInstance) {
            opacitySeekBar.setProgress((int) (originalImageAlpha * 100));
        }
        updateOpacityUi.run();

        opacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && activeImageView != null) {
                    float newAlpha = progress / 100.0f;
                    if (activeImageView == userImageViewInstance) {
                        userImageAlpha = newAlpha;
                    } else if (activeImageView == originalImageViewInstance) {
                        originalImageAlpha = newAlpha;
                    }
                    updateOpacityUi.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        dialogLayout.addView(opacityPercentageText);
        dialogLayout.addView(opacitySeekBar);

        builder.setView(dialogLayout);
        // No PositiveButton to save space, user can tap outside or press back to dismiss.
        // If you need a button, it will add to the height.
        // builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());


        AlertDialog dialog = builder.create();

        // --- Positioning and Sizing ---
        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            WindowManager.LayoutParams wlp = window.getAttributes();

            // 1. Position at the top
            wlp.gravity = Gravity.TOP;

            // 2. Adjust width (e.g., 80% of screen width or a fixed DP value)
            // Option A: Percentage of screen width
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            wlp.width = (int) (screenWidth * 0.8); // 80% of screen width

            // Option B: Fixed width (e.g., 300dp)
            // int widthInDp = 300;
            // wlp.width = (int) (widthInDp * scale + 0.5f);

            // 3. Height will be wrap_content based on the LinearLayout,
            //    but ensure it's not overly constrained by default dialog themes.
            //    We can also set a max height if needed.
            wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND; // Optional: remove screen dimming

            // Add some vertical margin from the top
            wlp.y = (int) (24 * scale + 0.5f); // 24dp margin from top

            window.setAttributes(wlp);
        }

        dialog.show();
    }


    private void initControlsBtn() {
        binding.hideControlsBtn.setOnClickListener(view -> {
            binding.controlsTxt.setText(showControls ? "Hide Controls" : "Show Controls");
            binding.switchBillBtn.setVisibility(showControls ? VISIBLE : GONE);
            binding.nextBillBtn.setVisibility(showControls ? VISIBLE : GONE);
            binding.opacityBtn.setVisibility(showControls ? VISIBLE : GONE);
            binding.rotateBtn.setVisibility(showControls ? VISIBLE : GONE);
            binding.goBackBtn.setVisibility(showControls ? VISIBLE : GONE);
            showControls = !showControls;
        });
    }

    private void setListeners() {
        binding.goBackBtn.setOnClickListener(view -> {
            finish();
        });
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }
        // Configure the behavior of the hidden system bars
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

}