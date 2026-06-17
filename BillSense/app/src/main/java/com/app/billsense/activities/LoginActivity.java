package com.app.billsense.activities;

import static com.app.billsense.utils.DialogUtils.showVerificationDialog;
import static com.app.billsense.utils.EmailSender.sendEmail;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityLoginBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Users;
import com.app.billsense.utils.EmailSender;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.PasswordUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private String email, password;
    private FBUtils fbUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();
        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.forgotPassIv.setOnClickListener(v -> showForgotPasswordDialog());
        binding.forgotPassTv.setOnClickListener(v -> showForgotPasswordDialog());

        binding.loginIv.setOnClickListener(v -> {
            validateAndLogin();
        });
        binding.loginTv.setOnClickListener(v -> {
            validateAndLogin();
        });

        binding.registerIv.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
        binding.registerTv.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void validateAndLogin() {
        email = binding.emailEditText.getText().toString().trim();
        password = binding.passwordEditText.getText().toString().trim();
        if (!InputValidator.isValidEmail(email)) {
            InputValidator.setInputError(binding.emailTextInputLayout,
                    getString(R.string.invalid_email_address));
        } else if (!InputValidator.isValidPassword(password)) {
            InputValidator.setInputError(binding.passwordTextInputLayout,
                    getString(R.string.invalid_password));
        } else {
            showProgressDialog(this);
            loginUser();
        }
    }

    private void loginUser() {
        fbUtils.loginUser(fbUtils.USERS_PATH, email, password, new FBInterface.OnLoginCallBack() {
            @Override
            public void onLoginSuccess(DataSnapshot userSnapshot) {
                hideProgressDialog();
                Users users = userSnapshot.getValue(Users.class);
                if (users == null) return;
                showToast(LoginActivity.this, "Logged in Successfully");
                PrefManager.getInstance().saveUserData(users.getId(), users.getEmail(),
                        users.getName());
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }

            @Override
            public void onLoginSuccessDirect(String userId, String userEmail, String name) {
                android.util.Log.d("==LOGIN", "onLoginSuccessDirect: navigating to HomeActivity");
                if (isFinishing() || isDestroyed()) {
                    android.util.Log.w("==LOGIN", "Activity already finishing, skipping navigation");
                    return;
                }
                hideProgressDialog();
                showToast(LoginActivity.this, "Logged in Successfully");
                runOnUiThread(() -> {
                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onLoginFailed(String errorMessage) {
                hideProgressDialog();
                showToast(LoginActivity.this, "Error: " + errorMessage);
            }

            @Override
            public void onLoginUnverified(DataSnapshot userSnapshot) {
                hideProgressDialog();
                Users users = userSnapshot.getValue(Users.class);
                showVerificationDialog(LoginActivity.this, users);
            }

            @Override
            public void onVerificationCodeNeeded(DataSnapshot userSnapshot) {
                hideProgressDialog();
                Users users = userSnapshot.getValue(Users.class);
                if (users == null) return;
                String verificationCode = Utils.generateUniqueCode();
                String body = "Dear " + users.getName() + " ,\n" +
                        "\n" +
                        "Thank you for registering with " + getString(R.string.app_name) + ". To complete your registration, please use the verification code provided below:\n" +
                        "\n" +
                        "Verification Code: " + verificationCode + "\n" +
                        "\n" +
                        "Please enter this code in the verification field on our website/app to complete the process. The code will expire soon, so be sure to use it promptly.\n" +
                        "\n" +
                        "If you did not request this code or have any questions, please reach out to our support team at support email" +
                        getString(R.string.support_email) + " or phone number " + getString(R.string.support_phone) + ".\n" +
                        "\n" +
                        "Thank you for choosing "+ getString(R.string.app_name)+". We look forward to helping you detect counterfeit currency..\n" +
                        "\n" +
                        "Best regards,\n" +
                        getString(R.string.app_name) +" IT Department";
                sendEmail(LoginActivity.this, users.getEmail(),
                        getString(R.string.app_name) + " Verification Code",
                        body,
                        new EmailSender.EmailSendListener() {
                            @Override
                            public void onEmailSentSuccess() {
                                showProgressDialog(LoginActivity.this);
                                fbUtils.updateVerificationCode(fbUtils.USERS_PATH, users.getId(),
                                        verificationCode, new FBInterface.OnVerificationCodeUpdateCallBack() {
                                            @Override
                                            public void onVerificationCodeUpdated() {
                                                hideProgressDialog();
                                                showVerificationDialog(LoginActivity.this, users);
                                                showToast(LoginActivity.this, "Please check your email for verification code");
                                            }

                                            @Override
                                            public void onVerificationCodeUpdateFailed(String errorMessage) {
                                                hideProgressDialog();
                                                showToast(LoginActivity.this, errorMessage);
                                            }
                                        });
                            }

                            @Override
                            public void onEmailSentFailure(String errorMessage) {
                                showToast(LoginActivity.this, errorMessage);
                            }
                        });
            }
        });
    }

    // ---- Forgot Password Flow ----

    private void showForgotPasswordDialog() {
        TextInputLayout emailLayout = new TextInputLayout(this);
        emailLayout.setHint(getString(R.string.email_address));
        emailLayout.setPadding(48, 16, 48, 0);
        TextInputEditText emailInput = new TextInputEditText(emailLayout.getContext());
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailLayout.addView(emailInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.forgot_password_title)
                .setMessage(R.string.enter_your_email)
                .setView(emailLayout)
                .setPositiveButton(R.string.send_reset_code, (dialog, which) -> {
                    String resetEmail = emailInput.getText() != null
                            ? emailInput.getText().toString().trim() : "";
                    if (!InputValidator.isValidEmail(resetEmail)) {
                        showToast(this, getString(R.string.invalid_email_address));
                        return;
                    }
                    sendPasswordResetCode(resetEmail);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendPasswordResetCode(String resetEmail) {
        showProgressDialog(this);
        String resetCode = Utils.generateUniqueCode();

        fbUtils.storePasswordResetCode(fbUtils.USERS_PATH, resetEmail, resetCode,
                new FBInterface.OnPasswordResetCallBack() {
                    @Override
                    public void onResetCodeStored(String userId) {
                        // Send the code via email
                        String body = "Dear User,\n\n" +
                                "You requested a password reset for your " +
                                getString(R.string.app_name) + " account.\n\n" +
                                "Your password reset code is: " + resetCode + "\n\n" +
                                "If you did not request this, please ignore this email.\n\n" +
                                "Best regards,\n" +
                                getString(R.string.app_name) + " IT Department";

                        EmailSender.sendEmail(LoginActivity.this, resetEmail,
                                getString(R.string.app_name) + " Password Reset Code",
                                body,
                                new EmailSender.EmailSendListener() {
                                    @Override
                                    public void onEmailSentSuccess() {
                                        hideProgressDialog();
                                        showToast(LoginActivity.this,
                                                "Reset code sent to your email");
                                        showVerifyResetCodeDialog(userId);
                                    }

                                    @Override
                                    public void onEmailSentFailure(String errorMessage) {
                                        hideProgressDialog();
                                        showToast(LoginActivity.this, errorMessage);
                                    }
                                });
                    }

                    @Override
                    public void onPasswordResetSuccess() {
                        // Not used in this step
                    }

                    @Override
                    public void onPasswordResetFailed(String errorMessage) {
                        hideProgressDialog();
                        showToast(LoginActivity.this, "Error: " + errorMessage);
                    }
                });
    }

    private void showVerifyResetCodeDialog(String userId) {
        TextInputLayout codeLayout = new TextInputLayout(this);
        codeLayout.setHint(getString(R.string.reset_code));
        codeLayout.setPadding(48, 16, 48, 0);
        TextInputEditText codeInput = new TextInputEditText(codeLayout.getContext());
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeLayout.addView(codeInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.verify_code)
                .setMessage(R.string.enter_reset_code)
                .setView(codeLayout)
                .setCancelable(false)
                .setPositiveButton(R.string.verify_code, (dialog, which) -> {
                    String code = codeInput.getText() != null
                            ? codeInput.getText().toString().trim() : "";
                    if (code.isEmpty()) {
                        showToast(this, "Please enter the reset code");
                        return;
                    }
                    showNewPasswordDialog(userId, code);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNewPasswordDialog(String userId, String resetCode) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        TextInputLayout passwordLayout = new TextInputLayout(this);
        passwordLayout.setHint(getString(R.string.new_password));
        passwordLayout.setPasswordVisibilityToggleEnabled(true);
        TextInputEditText passwordInput = new TextInputEditText(passwordLayout.getContext());
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.addView(passwordInput);
        layout.addView(passwordLayout);

        TextInputLayout confirmLayout = new TextInputLayout(this);
        confirmLayout.setHint(getString(R.string.confirm_new_password));
        confirmLayout.setPasswordVisibilityToggleEnabled(true);
        TextInputEditText confirmInput = new TextInputEditText(confirmLayout.getContext());
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmLayout.addView(confirmInput);
        layout.addView(confirmLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.set_new_password)
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton(R.string.reset_password, (dialog, which) -> {
                    String newPass = passwordInput.getText() != null
                            ? passwordInput.getText().toString().trim() : "";
                    String confirmPass = confirmInput.getText() != null
                            ? confirmInput.getText().toString().trim() : "";

                    if (!InputValidator.isValidPassword(newPass)) {
                        showToast(this, getString(R.string.invalid_password));
                        return;
                    }
                    if (!newPass.equals(confirmPass)) {
                        showToast(this, getString(R.string.password_not_matched));
                        return;
                    }

                    resetPassword(userId, resetCode, newPass);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void resetPassword(String userId, String resetCode, String newPassword) {
        showProgressDialog(this);
        fbUtils.verifyResetCodeAndUpdatePassword(fbUtils.USERS_PATH, userId, resetCode,
                newPassword,
                new FBInterface.OnPasswordResetCallBack() {
                    @Override
                    public void onResetCodeStored(String userId) {
                        // Not used in this step
                    }

                    @Override
                    public void onPasswordResetSuccess() {
                        hideProgressDialog();
                        showToast(LoginActivity.this,
                                "Password reset successfully! Please login with your new password.");
                    }

                    @Override
                    public void onPasswordResetFailed(String errorMessage) {
                        hideProgressDialog();
                        showToast(LoginActivity.this, "Error: " + errorMessage);
                    }
                });
    }
}