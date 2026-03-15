package com.app.billsense.activities;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.NotificationAdapter;
import com.app.billsense.adapters.ScanReportAdapter;
import com.app.billsense.databinding.ActivityHomeBinding;
import com.app.billsense.databinding.DialogScanBillBinding;
import com.app.billsense.databinding.DialogViewNotificationBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Detections;
import com.app.billsense.model.Notifications;
import com.app.billsense.model.ScanReport;
import com.app.billsense.model.Users;
import com.app.billsense.utils.DialogUtils;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PermissionManager;
import com.app.billsense.utils.PrefManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity implements PermissionManager.LocationPermissionListener{
    private ActivityHomeBinding binding;
    private FBUtils fbUtils;
    private String userId;
    private PermissionManager permissionManager;
    private Detections simpleDetection, multiDetection, videoDetection;
    private NotificationAdapter notificationAdapter;
    private ArrayList<Notifications> notificationsArrayList = new ArrayList<>();
    private ScanReportAdapter scanReportAdapter;
    private ScanReportAdapter scanHistoryAdapter;
    private ArrayList<ScanReport> newestScanReportArrayList = new ArrayList<>();
    private ArrayList<ScanReport> scanReportHistoryArrayList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();
        permissionManager = new PermissionManager(this, this);

        getAllDetectionData();
        getScanReportData();

        binding.educationalContent.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, EducationalContentActivity.class));
        });

        binding.shareVote.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, VotingActivity.class));
        });

        binding.processDetection.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, DetectionActivity.class));
        });

        binding.customerSupport.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, SupportActivity.class));
        });

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dispatch) {
                startActivity(new Intent(HomeActivity.this, ChatBotActivity.class));
            } else if (id == R.id.nav_cases) {
                if (permissionManager.hasLocationPermission()) {
                    startActivity(new Intent(HomeActivity.this, CasesActivity.class));
                }else {
                    permissionManager.requestLocationPermission();
                }
            } else if (id == R.id.nav_evidence) {
                startActivity(new Intent(HomeActivity.this, EvidenceActivity.class));
            }
            return true;
        });

        binding.viewProfile.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
        });

        binding.fabScanBill.setOnClickListener(view -> {
            showScanBillDialog();
        });

        binding.notificationImg.setOnClickListener(view -> {
            showViewNotificationDialog();
        });

        binding.searchBar.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, SearchActivity.class));
        });

        getUserData();
        getAllNotifications();

    }

    private void getScanReportData() {
        binding.scanReportRv.setHasFixedSize(true);
        binding.scanReportRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        scanReportAdapter = new ScanReportAdapter(this, newestScanReportArrayList);
        binding.scanReportRv.setAdapter(scanReportAdapter);
        binding.scanHistoryRv.setHasFixedSize(true);
        binding.scanHistoryRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        scanHistoryAdapter = new ScanReportAdapter(this, scanReportHistoryArrayList);
        binding.scanHistoryRv.setAdapter(scanHistoryAdapter);

        fbUtils.getAllDataFromPath(fbUtils.SCAN_REPORT_PATH + "/" + userId, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                newestScanReportArrayList.clear();
                scanReportHistoryArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    ScanReport scanReport = snapshot.getValue(ScanReport.class);

                    if (scanReport != null && scanReport.getDate() != null) {
                        String timeStamp = scanReport.getDate();
                        try {
                            // This pattern matches "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault());
                            Date scanDate = formatter.parse(timeStamp);
                            Date nowDate = new Date();

                            long diffInMillis = nowDate.getTime() - scanDate.getTime();
                            double hoursDifference = TimeUnit.MILLISECONDS.toHours(diffInMillis);

                            // Check Diff in hours and minutes here for minutes use 0.1 (10 minutes) and 1.0 (1 hour)
                            // Check if the difference is less than 24 hours (1 day)
                            Log.d("==HomeActivity", "Hours Difference: " + hoursDifference + "");
                            if (hoursDifference < 24.0) {
                                newestScanReportArrayList.add(scanReport);
                            }
                        } catch (ParseException e) {
                            Log.e("==HomeActivity", "Error parsing date: " + timeStamp, e);
                            // Optionally, handle older date formats or different parsing logic here
                        }
                    }
                    scanReportHistoryArrayList.add(scanReport);
                    scanReportAdapter.notifyDataSetChanged();
                    scanHistoryAdapter.notifyDataSetChanged();
                }

                // Sorter for the scan reports
                Comparator<ScanReport> dateComparator = (sr1, sr2) -> {
                    try {
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault());
                        Date date1 = formatter.parse(sr1.getDate());
                        Date date2 = formatter.parse(sr2.getDate());
                        return date2.compareTo(date1); // For descending order (newest first)
                    } catch (ParseException e) {
                        Log.e("HomeActivity", "Error parsing date for sorting", e);
                        return 0; // Keep original order if parsing fails
                    }
                };

                // Sort both lists
                if (!newestScanReportArrayList.isEmpty()){
                    newestScanReportArrayList.sort(dateComparator);
                    scanReportHistoryArrayList.sort(dateComparator);
                }
                if (newestScanReportArrayList.isEmpty()){
                    binding.noScanReportTv.setVisibility(View.VISIBLE);
                    binding.scanReportRv.setVisibility(View.GONE);
                }else {
                    binding.noScanReportTv.setVisibility(View.GONE);
                    binding.scanReportRv.setVisibility(View.VISIBLE);
                }
                if (scanReportHistoryArrayList.isEmpty()){
                    binding.noScanHistoryTv.setVisibility(View.VISIBLE);
                    binding.scanHistoryRv.setVisibility(View.GONE);
                }else {
                    binding.noScanHistoryTv.setVisibility(View.GONE);
                    binding.scanHistoryRv.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onDataNotFound() {
                newestScanReportArrayList.clear();
                scanReportAdapter.notifyDataSetChanged();
                scanReportHistoryArrayList.clear();
                scanHistoryAdapter.notifyDataSetChanged();
                binding.noScanReportTv.setVisibility(View.VISIBLE);
                binding.scanReportRv.setVisibility(View.GONE);
                binding.noScanHistoryTv.setVisibility(View.VISIBLE);
                binding.scanHistoryRv.setVisibility(View.GONE);
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                newestScanReportArrayList.clear();
                scanReportAdapter.notifyDataSetChanged();
                scanReportHistoryArrayList.clear();
                scanHistoryAdapter.notifyDataSetChanged();
                binding.noScanReportTv.setVisibility(View.VISIBLE);
                binding.scanReportRv.setVisibility(View.GONE);
                binding.noScanHistoryTv.setVisibility(View.VISIBLE);
                binding.scanHistoryRv.setVisibility(View.GONE);
            }
        });
    }

    private void getAllNotifications() {
        fbUtils.getAllDataFromPath(fbUtils.NOTIFICATIONS_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                notificationsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Notifications notifications = snapshot.getValue(Notifications.class);
                    if (notifications.getReceiverId().equals(userId)){
                        notificationsArrayList.add(notifications);
                    }
                }

                // Sort the list here: Latest notifications first
                Collections.sort(notificationsArrayList, new Comparator<Notifications>() {
                    @Override
                    public int compare(Notifications n1, Notifications n2) {
                        try {
                            long time1 = Long.parseLong(n1.getTime());
                            long time2 = Long.parseLong(n2.getTime());
                            return Long.compare(time2, time1);
                        } catch (NumberFormatException e) {
                            Log.e("SortError", "Invalid time format for notification: " +
                                    e.getMessage());
                            return 0;
                        }
                    }
                });
            }

            @Override
            public void onDataNotFound() {
                notificationsArrayList.clear();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                notificationsArrayList.clear();
            }
        });
    }

    private void showViewNotificationDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this,
                R.style.TransparentBottomSheetDialogTheme);
        DialogViewNotificationBinding notificationBinding = DialogViewNotificationBinding
                .inflate(LayoutInflater.from(this));
        dialog.setContentView(notificationBinding.getRoot());

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheetInternal = d.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior.from(bottomSheetInternal)
                        .setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheetInternal).setSkipCollapsed(true);
                BottomSheetBehavior.from(bottomSheetInternal).setHideable(false);
            }
        });

        notificationBinding.closeDialogIv.setOnClickListener(view -> dialog.dismiss());

        notificationBinding.notificationsRv.setHasFixedSize(true);
        notificationBinding.notificationsRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false
        ));
        notificationAdapter = new NotificationAdapter(this, notificationsArrayList);
        notificationBinding.notificationsRv.setAdapter(notificationAdapter);

        dialog.show();

    }

    private void getAllDetectionData() {
        fbUtils.getAllDataFromPath(fbUtils.DETECTIONS_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Detections detections = snapshot.getValue(Detections.class);
                    if (detections != null) {
                        switch (detections.getType()) {
                            case "simple":
                                simpleDetection = detections;
                                break;
                            case "video":
                                videoDetection = detections;
                                break;
                            case "multiple":
                                multiDetection = detections;
                                break;
                            case "process":
                                break;
                        }
                    }
                    Log.d("==detection", detections.getType());
                }

            }

            @Override
            public void onDataNotFound() {

            }

            @Override
            public void onFetchDataFailed(String errorMessage) {

            }
        });
    }


    @SuppressLint("cutPasteId")
    private void showScanBillDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this,
                R.style.TransparentBottomSheetDialogTheme);

        DialogScanBillBinding billBinding = DialogScanBillBinding.inflate(
                LayoutInflater.from(this));
        dialog.setContentView(billBinding.getRoot());

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheetInternal = d.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior.from(bottomSheetInternal)
                        .setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheetInternal).setSkipCollapsed(true);
                BottomSheetBehavior.from(bottomSheetInternal).setHideable(false);
            }
        });


        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        billBinding.closeIv.setOnClickListener(view -> dialog.dismiss());

        billBinding.scanBillIv.setOnClickListener(view -> {
            DialogUtils.showScanBillDialog(HomeActivity.this, fbUtils, simpleDetection);
            dialog.dismiss(); // Dismiss this dialog after launching another
        });

        billBinding.videoDetectionIv.setOnClickListener(view -> {
            DialogUtils.showVideoDetectionDialog(HomeActivity.this, fbUtils, videoDetection);
            dialog.dismiss();
        });

        billBinding.multipleDetectionIv.setOnClickListener(view -> {
            DialogUtils.showMultipleDetectionDialog(HomeActivity.this, fbUtils, multiDetection);
            dialog.dismiss();
        });


        dialog.show();
    }


    private void getUserData() {
        showProgressDialog(this);
        fbUtils.getUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnGetUserDataCallBack() {
            @Override
            public void onUserDataSuccess(Users user) {
                hideProgressDialog();
                binding.nameTxt.setText(user.getName());
                if (user.getImage() != null) {
                    Glide.with(HomeActivity.this)
                            .load(user.getImage()).into(binding.profileImg);
                }
            }

            @Override
            public void onUserDataNotExist() {
                hideProgressDialog();
                showToast(HomeActivity.this, "User Not Exist");
                finish();
            }

            @Override
            public void onGetUserDataFailure(Exception exception) {
                hideProgressDialog();
                showToast(HomeActivity.this, exception.getMessage());
                finish();
            }
        });
    }

    @Override
    public void onPermissionGranted() {
        showToast(this, "Location permission granted");
        startActivity(new Intent(HomeActivity.this, CasesActivity.class));
    }

    @Override
    public void onPermissionDenied(boolean permanentlyDenied) {
        if (permanentlyDenied) {
            Toast.makeText(this, "Location permission permanently denied. Please enable it in app settings.", Toast.LENGTH_LONG).show();
            // Show a dialog to guide the user to app settings
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs location access to function properly. " +
                            "Since it was permanently denied, please enable it in app settings.")
                    .setPositiveButton("Go to Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        // Handle the case where the user still doesn't want to grant permission
                        Toast.makeText(this, "Location-dependent features will not be available.", Toast.LENGTH_SHORT).show();
                    })
                    .setCancelable(false)
                    .show();
        } else {
            Toast.makeText(this, "Location Access Denied. Some features may not work.", Toast.LENGTH_LONG).show();
        }
    }
}