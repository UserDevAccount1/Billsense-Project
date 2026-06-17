package com.app.billsense.activities;

import static com.app.billsense.utils.InputValidator.isValidConfirmPassword;
import static com.app.billsense.utils.InputValidator.isValidEmail;
import static com.app.billsense.utils.InputValidator.isValidName;
import static com.app.billsense.utils.InputValidator.isValidPassword;
import static com.app.billsense.utils.InputValidator.isValidPhone;
import static com.app.billsense.utils.InputValidator.setEditTextError;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.billsense.R;
import com.app.billsense.databinding.ActivityRegisterBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.utils.FBStorageUtils;
import com.app.billsense.utils.FBUtils;
import com.github.dhaval2404.imagepicker.ImagePicker;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private String email, name, phone, downloadIdUrl, password, confirmPassword;
    private Uri imageUri;
    private FBUtils fbUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        fbUtils = new FBUtils();
        setUpClickListeners();

    }

    private void setUpClickListeners() {
        binding.uploadId.setOnClickListener(v -> {
            ImagePicker.with(RegisterActivity.this)
                    .galleryOnly()
                    .galleryMimeTypes(new String[]{
                            "image/png",
                            "image/jpg",
                            "image/jpeg"
                    })
                    .cropSquare()
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .start();
        });

        binding.registerBtn.setOnClickListener(v -> {
            email = binding.inputEmailAddress.getText().toString();
            name = binding.inputName.getText().toString();
            phone = binding.inputPhone.getText().toString();
            password = binding.inputPassword.getText().toString();
            confirmPassword = binding.inputConfirmPassword.getText().toString();

            if (!isValidEmail(email)) {
                setEditTextError(binding.inputEmailAddress,
                        getString(R.string.invalid_email_address));
            } else if (!isValidName(name)) {
                setEditTextError(binding.inputName,
                        getString(R.string.enter_your_name));
            } else if (!isValidPhone(phone)) {
                setEditTextError(binding.inputPhone,
                        getString(R.string.enter_valid_phone_number));
            } else if (imageUri == null || downloadIdUrl == null || downloadIdUrl.isEmpty()) {
                showToast(RegisterActivity.this,
                        getString(R.string.upload_valid_id));
            } else if (!isValidPassword(password)) {
                setEditTextError(binding.inputPassword,
                        getString(R.string.invalid_password));
            } else if (!isValidConfirmPassword(password, confirmPassword)) {
                setEditTextError(binding.inputConfirmPassword,
                        getString(R.string.password_not_matched));
            } else {
                showProgressDialog(RegisterActivity.this);
                registerUser();
            }

        });

    }

    private void registerUser() {
        fbUtils.saveUserData(fbUtils.USERS_PATH, email, new FBInterface.OnUserDataSaveCallBack() {
            @Override
            public void onUserDataSaveSuccess() {
                hideProgressDialog();
                showToast(RegisterActivity.this, "Registered Successfully.");
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void onUserDataSaveFailure(Exception exception) {
                hideProgressDialog();
                showToast(RegisterActivity.this, "Error: " + exception.getMessage());
            }
                },
                new Pair<>("email", email),
                new Pair<>("name", name),
                new Pair<>("phone", binding.ccp.getSelectedCountryCodeWithPlus() + phone),
                new Pair<>("downloadIdUrl", downloadIdUrl),
                new Pair<>("status", getString(R.string.unverified)),
                new Pair<>("password", com.app.billsense.utils.PasswordUtils.hashPassword(password)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.getData();
            showProgressDialog(this);
            uploadValidIdImage();
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            showToast(this, ImagePicker.getError(data));
        } else {
            showToast(this, "Task Cancelled");
        }

    }

    private void uploadValidIdImage() {
        FBStorageUtils.uploadImage(FBStorageUtils.ID_IMAGE_PATH,
                imageUri, new FBInterface.ImageUploadCallback() {
                    @Override
                    public void onImageUploadSuccess(String downloadUrl) {
                        hideProgressDialog();
                        Log.d("==image url", downloadUrl + "");
                        downloadIdUrl = downloadUrl;
                        showToast(RegisterActivity.this, "ID Image Uploaded");
                    }

                    @Override
                    public void onImageUploadFailure(Exception exception) {
                        hideProgressDialog();
                        Log.d("==fbstorage", exception +"");
                        showToast(RegisterActivity.this, "Error: " + exception.getMessage());
                    }
                });
    }

}