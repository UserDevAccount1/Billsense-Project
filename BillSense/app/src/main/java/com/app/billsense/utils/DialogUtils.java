package com.app.billsense.utils;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.app.billsense.R;
import com.app.billsense.activities.HomeActivity;
import com.app.billsense.databinding.DialogMultipleDetectionBinding;
import com.app.billsense.databinding.DialogScanDetectionBinding;
import com.app.billsense.databinding.DialogVideoDetectionBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Detections;
import com.app.billsense.model.Users;
import com.app.billsense.scan.multi.MultiScanActivity;
import com.app.billsense.scan.standard.StandardScanActivity;
import com.app.billsense.scan.video.VideoScanActivity;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.textfield.TextInputLayout;

public class DialogUtils {

    public static void showVerificationDialog(Activity activity, Users users) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.dialog_verification);

        TextInputLayout textInputLayout = dialog.findViewById(R.id.verification_input);
        EditText inputVerificationCode = textInputLayout.getEditText();
        ImageView verifyBtn = dialog.findViewById(R.id.verify_btn);

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Make background transparent
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

        verifyBtn.setOnClickListener(v -> {
            String verificationCode = inputVerificationCode.getText().toString();
            if (!InputValidator.isValidVerificationCode(verificationCode)) {
                inputVerificationCode.setError("Invalid Verification Code");
                inputVerificationCode.requestFocus();
            } else {
                showProgressDialog(activity);
                FBUtils fbUtils = new FBUtils();
                fbUtils.checkVerificationCode(fbUtils.USERS_PATH, users.getId(),
                        verificationCode, new FBInterface.OnVerificationCallBack() {
                            @Override
                            public void onVerificationSuccess() {
                                hideProgressDialog();
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                }
                                PrefManager.getInstance().saveUserData(users.getId(), users.getEmail(),
                                        users.getName());
                                showToast(activity, "Congratulation! you are verified.");
                                activity.startActivity(new Intent(activity, HomeActivity.class));
                                activity.finish();
                            }

                            @Override
                            public void onVerificationFailed(String errorMessage) {
                                hideProgressDialog();
                                showToast(activity, errorMessage);
                            }
                        });
            }
        });

        dialog.show();

    }


    public static void showVideoDetectionDialog(Activity activity, FBUtils fbUtils, Detections detections) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        DialogVideoDetectionBinding binding = DialogVideoDetectionBinding
                .inflate(LayoutInflater.from(activity));
        dialog.setContentView(binding.getRoot());


        // Make the dialog background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

            // Set dialog to full width
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            // layoutParams.gravity = Gravity.BOTTOM;
            dialog.getWindow().setAttributes(layoutParams);
        }

        if (detections != null){
            binding.videoDetectionContent.setText(detections.getContent());
        }

        binding.beginDetectionBtn.setOnClickListener(view -> {
            activity.startActivity(new Intent(activity, VideoScanActivity.class));
        });

        dialog.show();

    }

    public static void showMultipleDetectionDialog(Activity activity, FBUtils fbUtils, Detections detections) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        DialogMultipleDetectionBinding binding = DialogMultipleDetectionBinding
                .inflate(LayoutInflater.from(activity));
        dialog.setContentView(binding.getRoot());


        // Make the dialog background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

            // Set dialog to full width
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            // layoutParams.gravity = Gravity.BOTTOM;
            dialog.getWindow().setAttributes(layoutParams);
        }

        if (detections != null){
            binding.multipleDetectionContent.setText(detections.getContent());
        }

        binding.beginScanBtn.setOnClickListener(view -> {
            activity.startActivity(new Intent(activity, MultiScanActivity.class));
        });

        dialog.show();
    }

    public static void showScanBillDialog(Activity activity, FBUtils fbUtils, Detections detections) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        DialogScanDetectionBinding binding = DialogScanDetectionBinding
                .inflate(LayoutInflater.from(activity));
        dialog.setContentView(binding.getRoot());


        // Make the dialog background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

            // Set dialog to full width
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            // layoutParams.gravity = Gravity.BOTTOM;
            dialog.getWindow().setAttributes(layoutParams);
        }

        if (detections != null){
            binding.scanDetectionContent.setText(detections.getContent());
        }

        binding.beginScanBtn.setOnClickListener(view -> {
            activity.startActivity(new Intent(activity, StandardScanActivity.class));
        });


        dialog.show();
    }


    public static void displayFullImageDialog(Context context, String title, String imageUrl, Uri uri) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.dialog_view_full_image);

        //dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Make background transparent
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

        PhotoView photoView = dialog.findViewById(R.id.photo_view);
        TextView titleTxt = dialog.findViewById(R.id.title_text);
        ImageView backImg = dialog.findViewById(R.id.back_image);

        titleTxt.setText(title);
        backImg.setOnClickListener(v -> {
            dialog.dismiss();
        });

        // Load image using your preferred image loading library (e.g., Glide, Picasso)
        if (imageUrl != null) {
            Glide.with(context)
                    .load(imageUrl)
                    .into(photoView);
        }
        if (uri != null) {
            Glide.with(context).load(uri).into(photoView);
        }

        dialog.show();

    }


    public static void displayFullVideoDialog(Context context, String title, String videoUrl, Uri videoUri) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        // dialog.setCanceledOnTouchOutside(true); // Allow dismissing by touching outside if desired
        dialog.setContentView(R.layout.dialog_view_full_video);

        if (dialog.getWindow() != null) {
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        PlayerView playerView = dialog.findViewById(R.id.exoplayer_view);
        TextView titleTxtVideo = dialog.findViewById(R.id.title_text_video);
        ImageView backImgVideo = dialog.findViewById(R.id.back_image_video);

        // It's good practice to declare the player as nullable and check before use
        ExoPlayer player = null;

        if (titleTxtVideo != null) {
            titleTxtVideo.setText(title);
        }

        try {
            player = new ExoPlayer.Builder(context).build();
            playerView.setPlayer(player);

            MediaItem mediaItem = null;
            if (videoUrl != null && !videoUrl.isEmpty()) {
                mediaItem = MediaItem.fromUri(videoUrl);
            } else if (videoUri != null) {
                mediaItem = MediaItem.fromUri(videoUri);
            }

            if (mediaItem != null) {
                player.setMediaItem(mediaItem);
                player.prepare(); // Prepares the player (asynchronous)
                player.setPlayWhenReady(true); // Start playback when ready
            } else {
                if (context instanceof Activity) {
                    showToast((Activity) context, "No video source found.");
                } else {
                    showToast(context, "No video source found.");
                }
                dialog.dismiss();
                if (player != null) player.release(); // Release player if created but no media
                return;
            }
        } catch (Exception e) {
            // Log error or show a more user-friendly message
            // Log.e("DialogUtils", "Error initializing player", e);
            if (context instanceof Activity) {
                showToast((Activity) context, "Error playing video.");
            } else {
                showToast(context, "Error playing video.");
            }
            dialog.dismiss();
            if (player != null) player.release();
            return;
        }

        // Final reference for lambda
        final ExoPlayer finalPlayer = player;

        if (backImgVideo != null) {
            backImgVideo.setOnClickListener(v -> {
                if (finalPlayer != null) {
                    finalPlayer.stop(); // Stop playback before release
                    finalPlayer.release();
                }
                dialog.dismiss();
            });
        }

        dialog.setOnDismissListener(dialogInterface -> {
            if (finalPlayer != null) {
                // Check if playing or buffering before stopping
                if (finalPlayer.isPlaying() || finalPlayer.isLoading()) {
                    finalPlayer.stop();
                }
                finalPlayer.release(); // Crucial: Release ExoPlayer resources
            }
        });

        dialog.setOnCancelListener(dialogInterface -> { // Also handle cancel events (e.g. back press not hitting the ImageView)
            if (finalPlayer != null) {
                if (finalPlayer.isPlaying() || finalPlayer.isLoading()) {
                    finalPlayer.stop();
                }
                finalPlayer.release();
            }
        });


        dialog.show();
    }

}
