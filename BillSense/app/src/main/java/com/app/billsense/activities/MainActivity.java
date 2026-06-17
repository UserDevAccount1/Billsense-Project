package com.app.billsense.activities;

import static com.app.billsense.utils.Utils.showToast;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.app.billsense.databinding.ActivityMainBinding;
import com.app.billsense.fcm.FcmNotificationSender;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Tokens;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private static final String TAG = "MainActivityReceiver";
    private FBUtils fbUtils = new FBUtils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();
        // PrefManager initialized in BillSenseApp.onCreate()

        // Fast auto-redirect: skip splash entirely
        if (PrefManager.getInstance().isLoggedIn()) {
            startActivity(new Intent(MainActivity.this, HomeActivity.class));
            finish();
            return;
        }

        binding.mainScanBtn.setOnClickListener(view -> {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        });
        askNotificationPermission();

        // Auto-redirect to LoginActivity after 1.5s splash (user can also tap button)
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !PrefManager.getInstance().isLoggedIn()) {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            }
        }, 1500);

        // Token loading on a true background thread — never blocks UI
        new Thread(() -> {
            try {
                FcmNotificationSender.init(MainActivity.this);
                getAllCustomerTokens();
                getAllTechnicianTokens();
            } catch (Exception e) {
                android.util.Log.w("==MAIN", "Token loading failed (non-critical): " + e.getMessage());
            }
        }).start();

    }

    private void getAllCustomerTokens() {
        fbUtils.getAllUserToken(fbUtils.TOKENS_PATH, fbUtils.USERS_PATH, new FBInterface.OnUserFcmTokensRetrievedListener() {
            @Override
            public void onTokensRetrieved(ArrayList<Tokens> userTokens) {
                if (!userTokens.isEmpty()) {
                    Utils.customerTokens.clear();
                    Utils.customerTokens.addAll(userTokens);
                    cleanUpUserTokens(userTokens);
                    Log.d("==tokenCustomerList", Utils.customerTokens.size() + "");
                } else {
                    Utils.customerTokens.clear();
                }
            }

            @Override
            public void onError(String errorMessage) {
            }
        });
    }

    private void cleanUpUserTokens(ArrayList<Tokens> userTokens) {
        for (Tokens token : userTokens) {
            fbUtils.checkUserIdExists(fbUtils.USERS_PATH, token.getUserId(), new FBInterface.OnUserExistsCallBack() {
                @Override
                public void onUserExists(boolean exists) {
                    if (!exists){
                        Log.d("==token for check", token.getUserId()  + " : " + exists);
                        DatabaseReference reference = FirebaseDatabase.getInstance().getReference()
                                .child(fbUtils.TOKENS_PATH).child(fbUtils.USERS_PATH);
                        reference.child(token.getUserId()).removeValue();
                    }
                }

                @Override
                public void onUserExistsCheckFailed(String errorMessage) {

                }
            });
        }
    }

    private void getAllTechnicianTokens() {
        fbUtils.getAllUserToken(fbUtils.TOKENS_PATH, fbUtils.AGENTS_PATH, new FBInterface.OnUserFcmTokensRetrievedListener() {
            @Override
            public void onTokensRetrieved(ArrayList<Tokens> userTokens) {
                if (!userTokens.isEmpty()) {
                    Utils.technicianTokens.clear();
                    Utils.technicianTokens.addAll(userTokens);
                    Log.d("==tokenTechnicianList", Utils.technicianTokens.size() + "");
                } else {
                    Utils.technicianTokens.clear();
                }
            }

            @Override
            public void onError(String errorMessage) {
            }
        });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted.");
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.d(TAG, "Showing rationale for POST_NOTIFICATIONS permission before requesting again.");
                showPermissionRationaleDialog(); // Show dialog, which can then lead to request or settings
            } else {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            Log.d(TAG, "No runtime notification permission required for this Android version.");
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted.");

                } else {
                    Log.d(TAG, "Notification permission denied.");
                    Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show();
                    // Permission was denied. Show the rationale dialog.
                    showPermissionRationaleDialog();
                }
            });

    private void showPermissionRationaleDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Notification Permission Needed")
                .setMessage("This app requires notification permission to alert you about important updates and messages. Please grant the permission in settings.")
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Notification permission is needed for full app functionality.", Toast.LENGTH_LONG).show();
                })
                .setPositiveButton("Settings", (dialog, which) -> {
                    dialog.dismiss();
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setOnDismissListener(dialog -> {

                })
                .show();
    }

}