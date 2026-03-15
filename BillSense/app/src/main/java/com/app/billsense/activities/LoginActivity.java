package com.app.billsense.activities;

import static com.app.billsense.utils.DialogUtils.showVerificationDialog;
import static com.app.billsense.utils.EmailSender.sendEmail;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityLoginBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Users;
import com.app.billsense.utils.EmailSender;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.Utils;
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
        binding.forgotPassIv.setOnClickListener(v -> {

        });
        binding.forgotPassTv.setOnClickListener(v -> {

        });

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
                assert users != null;
                showToast(LoginActivity.this, "Logged in Successfully");
                PrefManager.getInstance().saveUserData(users.getId(), users.getEmail(),
                        users.getName());
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
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
                assert users != null;
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
                        "Thank you for choosing "+ getString(R.string.app_name)+". We look forward to serving your automotive needs.\n" +
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
}