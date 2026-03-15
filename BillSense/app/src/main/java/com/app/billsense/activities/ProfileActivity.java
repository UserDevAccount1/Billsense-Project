package com.app.billsense.activities;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.app.billsense.utils.EmailSender.sendEmail;
import static com.app.billsense.utils.InputValidator.isValidConfirmPassword;
import static com.app.billsense.utils.InputValidator.isValidEmail;
import static com.app.billsense.utils.InputValidator.isValidName;
import static com.app.billsense.utils.InputValidator.isValidPassword;
import static com.app.billsense.utils.InputValidator.isValidPhoneWithPlus;
import static com.app.billsense.utils.InputValidator.setEditTextError;
import static com.app.billsense.utils.InputValidator.setInputError;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityProfileBinding;
import com.app.billsense.databinding.DialogChangePasswordBinding;
import com.app.billsense.databinding.DialogConfirmBinding;
import com.app.billsense.databinding.DialogEditUserInfoBinding;
import com.app.billsense.fcm.FcmNotificationSender;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Users;
import com.app.billsense.utils.EmailSender;
import com.app.billsense.utils.FBStorageUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.Utils;
import com.bumptech.glide.Glide;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileActivity extends AppCompatActivity {
    private ActivityProfileBinding binding;
    private FBUtils fbUtils;
    private String userId, downloadImageUrl;
    private Users users;
    private ActivityResultLauncher<Intent> imagePickerActivityResultLauncher;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.profile));

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();

        // Initialize the ActivityResultLauncher for Image picker
        imagePickerActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        imageUri = result.getData().getData();
                        showProgressDialog(ProfileActivity.this);
                        uploadProfileImage();
                    } else if (result.getResultCode() == ImagePicker.RESULT_ERROR) {
                        showToast(ProfileActivity.this, ImagePicker.getError(result.getData()));
                    } else {
                        showToast(ProfileActivity.this, "Task Cancelled");
                    }
                }
        );

        binding.editProfileImg.setOnClickListener(view -> {
            ImagePicker.with(ProfileActivity.this)
                    .galleryOnly()
                    .galleryMimeTypes(new String[]{
                            "image/png",
                            "image/jpg",
                            "image/jpeg"
                    })
                    .cropSquare()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .createIntent(intent1 -> {
                        imagePickerActivityResultLauncher.launch(intent1);
                        return null;
                    });
        });

        binding.editInfo.setOnClickListener(view -> {
            showEditInfoDialog();
        });

        binding.changePassword.setOnClickListener(view -> {
            showChangePasswordDialog();
        });

        binding.logout.setOnClickListener(view -> {
            showLogoutDialog();
        });

        getUserData();

    }

    private void showChangePasswordDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        DialogChangePasswordBinding changePasswordBinding = DialogChangePasswordBinding.inflate(
                LayoutInflater.from(this));
        dialog.setContentView(changePasswordBinding.getRoot());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Make background transparent
            window.getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());

            // Set the dialog window width to WRAP_CONTENT.
            // This tells the window to size itself based on the width of its content.
            // Since your LinearLayout (content) is 300dp, the window will wrap to that.
//            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            float density = getResources().getDisplayMetrics().density;
            int widthInPx = (int) (300 * density);
            layoutParams.width = widthInPx;
            // You can keep height as WRAP_CONTENT or set it specifically if needed
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

            // Apply the new attributes
            window.setAttributes(layoutParams);
        }

        TextInputEditText inputCurrentPassword = changePasswordBinding.currentPassword;
        TextInputEditText inputNewPassword = changePasswordBinding.inputNewPassword;
        TextInputEditText inputConfirmPassword = changePasswordBinding.inputConfirmPassword;
        AppCompatButton updatePasswordBtn = changePasswordBinding.updatePasswordBtn;

        updatePasswordBtn.setOnClickListener(view -> {
            String currentPassword = inputCurrentPassword.getText().toString();
            String newPassword = inputNewPassword.getText().toString();
            String confirmPassword = inputConfirmPassword.getText().toString();
            if (!isValidConfirmPassword(currentPassword, users.getPassword())) {
                setEditTextError(inputCurrentPassword, "Current Password is incorrect.");
            } else if (!isValidPassword(newPassword)) {
                setEditTextError(inputNewPassword, "Enter valid new password.");
            } else if (isValidConfirmPassword(currentPassword, newPassword)) {
                setEditTextError(inputNewPassword, "New Password must be different from current password.");
            } else if (!isValidConfirmPassword(newPassword, confirmPassword)) {
                setEditTextError(inputConfirmPassword, "Password does not match.");
            } else {
                showProgressDialog(this);
                fbUtils.updateUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnUserDataSaveCallBack() {
                            @Override
                            public void onUserDataSaveSuccess() {
                                dialog.dismiss();
                                showToast(ProfileActivity.this, "Password Updated. Please check your email for verification code.");
                                String verificationCode = Utils.generateUniqueCode();
                                String body = "Dear " + users.getName() + " ,\n" +
                                        "\n" +
                                        "Thank you for registering with " + getString(R.string.app_name) + ". To complete your password change request, please use the verification code provided below:\n" +
                                        "\n" +
                                        "Verification Code: " + verificationCode + "\n" +
                                        "\n" +
                                        "Please enter this code in the verification field on our website/app to complete the process. The code will expire soon, so be sure to use it promptly.\n" +
                                        "\n" +
                                        "If you did not request this code or have any questions, please reach out to our support team at support email" +
                                        getString(R.string.support_email) + " or phone number " + getString(R.string.support_phone) + ".\n" +
                                        "\n" +
                                        "Thank you for choosing " + getString(R.string.app_name) + ". We look forward to serving your automotive needs.\n" +
                                        "\n" +
                                        "Best regards,\n" +
                                        getString(R.string.app_name) + " IT Department";
                                sendEmail(ProfileActivity.this, users.getEmail(),
                                        getString(R.string.app_name) + " Verification Code",
                                        body,
                                        new EmailSender.EmailSendListener() {
                                            @Override
                                            public void onEmailSentSuccess() {
                                                fbUtils.updateVerificationCode(fbUtils.USERS_PATH, users.getId(),
                                                        verificationCode, new FBInterface.OnVerificationCodeUpdateCallBack() {
                                                            @Override
                                                            public void onVerificationCodeUpdated() {
                                                                hideProgressDialog();
                                                                showVerificationDialog(ProfileActivity.this, users);
                                                                FcmNotificationSender.get().sendNotification(
                                                                        users.getFcmToken(), "Password Updated",
                                                                        "Your password updated successfully. Verification Code: "+ verificationCode + " . Please also check your email for verification code."
                                                                        , userId, userId, "account"
                                                                );
                                                                showToast(ProfileActivity.this, "Please check your email for verification code");
                                                            }

                                                            @Override
                                                            public void onVerificationCodeUpdateFailed(String errorMessage) {
                                                                hideProgressDialog();
                                                                showToast(ProfileActivity.this, errorMessage);
                                                            }
                                                        });
                                            }

                                            @Override
                                            public void onEmailSentFailure(String errorMessage) {
                                                showToast(ProfileActivity.this, errorMessage);
                                                fbUtils.updateVerificationCode(fbUtils.USERS_PATH, users.getId(),
                                                        verificationCode, new FBInterface.OnVerificationCodeUpdateCallBack() {
                                                            @Override
                                                            public void onVerificationCodeUpdated() {
                                                                hideProgressDialog();
                                                                showVerificationDialog(ProfileActivity.this, users);
                                                                FcmNotificationSender.get().sendNotification(
                                                                        users.getFcmToken(), "Password Updated",
                                                                        "Your password updated successfully. Verification Code: "+ verificationCode + " . Please also check your email for verification code."
                                                                        , userId, userId, "account"
                                                                );
                                                                showToast(ProfileActivity.this, "Please check your email for verification code");
                                                            }

                                                            @Override
                                                            public void onVerificationCodeUpdateFailed(String errorMessage) {
                                                                hideProgressDialog();
                                                                showToast(ProfileActivity.this, errorMessage);
                                                            }
                                                        });
                                            }
                                        });
                            }

                            @Override
                            public void onUserDataSaveFailure(Exception exception) {
                                hideProgressDialog();
                                showToast(ProfileActivity.this, "Error: " + exception.getMessage());
                            }
                        },
                        new Pair<>("password", newPassword),
                        new Pair<>("status", getString(R.string.unverified))
                );
            }
        });

        dialog.show();
    }

    private void showVerificationDialog(Activity activity, Users users) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
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
                                showToast(activity, "Congratulation! you password updated successfully.");
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

    private void showEditInfoDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        DialogEditUserInfoBinding infoBinding = DialogEditUserInfoBinding.inflate(
                LayoutInflater.from(this));
        dialog.setContentView(infoBinding.getRoot());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Make background transparent
            window.getAttributes().windowAnimations = R.style.DialogAnimation; // Set dialog animation

            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());

            float density = getResources().getDisplayMetrics().density;
            int widthInPx = (int) (300 * density);
            layoutParams.width = widthInPx;
//            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;

            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

            // Apply the new attributes
            window.setAttributes(layoutParams);
        }
        TextInputLayout tNameInput = dialog.findViewById(R.id.t_name_input);
        EditText inputTName = tNameInput.getEditText();
        TextInputLayout tEmailInput = dialog.findViewById(R.id.t_email_input);
        EditText inputEmailAddress = tEmailInput.getEditText();
        TextInputLayout tPhoneInput = dialog.findViewById(R.id.t_phone_input);
        EditText inputPhone = tPhoneInput.getEditText();
        AppCompatButton updateInfoBtn = dialog.findViewById(R.id.update_info_btn);

        if (users != null) {
            inputTName.setText(users.getName());
            inputEmailAddress.setText(users.getEmail());
            inputPhone.setText(users.getPhone());
        }

        updateInfoBtn.setOnClickListener(v -> {
            String email = inputEmailAddress.getText().toString();
            String name = inputTName.getText().toString();
            String phone = inputPhone.getText().toString();
            if (!isValidName(name)) {
                setInputError(infoBinding.tNameInput, "Enter your name.");
            } else if (!isValidEmail(email)) {
                setInputError(infoBinding.tEmailInput,
                        getString(R.string.invalid_email_address));
            } else if (!isValidPhoneWithPlus(phone)) {
                setInputError(infoBinding.tPhoneInput,
                        "Enter valid Phone Number");
            } else {
                showProgressDialog(this);
                fbUtils.updateUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnUserDataSaveCallBack() {
                            @Override
                            public void onUserDataSaveSuccess() {
                                hideProgressDialog();
                                FcmNotificationSender.get().sendNotification(
                                        users.getFcmToken(), "Account Info Updated",
                                        "Your account info updated successfully."
                                        , userId, userId, "account"
                                );
                                showToast(ProfileActivity.this, "Info Updated.");
                                dialog.dismiss();
                            }

                            @Override
                            public void onUserDataSaveFailure(Exception exception) {
                                hideProgressDialog();
                                showToast(ProfileActivity.this, "Error: " + exception.getMessage());
                            }
                        },
                        new Pair<>("email", email),
                        new Pair<>("name", name),
                        new Pair<>("phone", phone)
                );
            }
        });

        dialog.show();
    }

    private void showLogoutDialog() {
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this);
        DialogConfirmBinding exitBinding = DialogConfirmBinding.inflate(LayoutInflater.from(this));
        dialogBuilder.setView(exitBinding.getRoot());
        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.setCanceledOnTouchOutside(false);
        exitBinding.yesBtn.setOnClickListener(view -> {
            showProgressDialog(ProfileActivity.this);
            new Handler().postDelayed(() -> {
                alertDialog.dismiss();
                hideProgressDialog();
                PrefManager.getInstance().clearUserData();
                startActivity(new Intent(ProfileActivity.this, MainActivity.class)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            }, 2500);
        });
        exitBinding.noBtn.setOnClickListener(view -> alertDialog.dismiss());
        alertDialog.show();
    }

    private void getUserData() {
        showProgressDialog(this);
        fbUtils.getUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnGetUserDataCallBack() {
            @Override
            public void onUserDataSuccess(Users user) {
                hideProgressDialog();
                users = user;
                binding.userName.setText(user.getName());
                if (user.getImage() != null) {
                    Glide.with(ProfileActivity.this)
                            .load(user.getImage()).into(binding.profileImg);
                }
            }

            @Override
            public void onUserDataNotExist() {
                hideProgressDialog();
                showToast(ProfileActivity.this, "User Not Exist");
                finish();
            }

            @Override
            public void onGetUserDataFailure(Exception exception) {
                hideProgressDialog();
                showToast(ProfileActivity.this, exception.getMessage());
                finish();
            }
        });
    }

    private void uploadProfileImage() {
        FBStorageUtils.uploadFile(FBStorageUtils.PROFILE_IMAGE_PATH, imageUri, FBStorageUtils.FileType.IMAGE,
                new FBInterface.FileUploadCallback() {
                    @Override
                    public void onFileUploadSuccess(String downloadUrl) {
                        downloadImageUrl = downloadUrl;
                        fbUtils.updateUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnUserDataSaveCallBack() {
                                    @Override
                                    public void onUserDataSaveSuccess() {
                                        hideProgressDialog();
                                        FcmNotificationSender.get().sendNotification(
                                                users.getFcmToken(), "Profile Image Updated",
                                                "Your profile image updated successfully."
                                                , userId, userId, "account"
                                        );
                                        showToast(ProfileActivity.this, "Profile Image Updated");
                                        downloadImageUrl = null;
                                    }

                                    @Override
                                    public void onUserDataSaveFailure(Exception exception) {
                                        hideProgressDialog();
                                        showToast(ProfileActivity.this, exception.getMessage());
                                    }
                                },
                                new Pair<>("image", downloadImageUrl)
                        );
                    }

                    @Override
                    public void onFileUploadFailure(Exception exception) {
                        hideProgressDialog();
                        showToast(ProfileActivity.this, "Error: " + exception.getMessage());
                    }

                    @Override
                    public void onFileUploadProgress(double progress) {
                    }
                });
    }

}