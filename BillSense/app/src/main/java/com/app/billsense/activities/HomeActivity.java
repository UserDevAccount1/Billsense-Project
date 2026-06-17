package com.app.billsense.activities;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.NotificationAdapter;
import com.app.billsense.adapters.ScanReportAdapter;
import com.app.billsense.databinding.ActivityHomeBinding;
import com.app.billsense.databinding.DialogViewNotificationBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Detections;
import com.app.billsense.model.Notifications;
import com.app.billsense.model.ScanReport;
import com.app.billsense.model.Users;
import com.app.billsense.scan.standard.UploadScanActivity;
import com.app.billsense.activities.EducationalContentActivity;
import com.app.billsense.activities.SupportActivity;
import com.app.billsense.activities.ChatBotActivity;
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

import androidx.annotation.NonNull;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeActivity extends AppCompatActivity implements PermissionManager.LocationPermissionListener{
    private ActivityHomeBinding binding;
    private FBUtils fbUtils;
    private String userId;
    private PermissionManager permissionManager;
    private Detections simpleDetection, multiDetection, videoDetection;
    private NotificationAdapter notificationAdapter;
    private ArrayList<Notifications> notificationsArrayList = new ArrayList<>();
    private ScanReportAdapter scanHistoryAdapter;
    private ArrayList<ScanReport> scanReportHistoryArrayList = new ArrayList<>();
    private boolean isFabMenuOpen = false;
    private boolean isAnimating = false;

    // Radial expansion configuration
    private static final float RADIUS_DP = 180f;
    private static final double[] ARC_ANGLES = {150.0, 120.0, 60.0, 30.0}; // degrees for each FAB
    private static final long EXPAND_DURATION = 300L;
    private static final long COLLAPSE_DURATION = 250L;
    private static final long STAGGER_DELAY = 50L;
    private static final long LABEL_FADE_DELAY = 150L;


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
        loadAnnouncements();

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dispatch) {
                startActivity(new Intent(HomeActivity.this, ChatBotActivity.class));
            } else if (id == R.id.nav_cases) {
                if (permissionManager.hasLocationPermission()) {
                    startActivity(new Intent(HomeActivity.this, CasesActivity.class));
                } else {
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
            toggleFabMenu();
        });

        binding.fabOverlay.setOnClickListener(view -> {
            closeFabMenu();
        });

        binding.fabStandardScan.setOnClickListener(view -> {
            closeFabMenu();
            DialogUtils.showScanBillDialog(HomeActivity.this, fbUtils, simpleDetection);
        });

        binding.fabMultiScan.setOnClickListener(view -> {
            closeFabMenu();
            DialogUtils.showMultipleDetectionDialog(HomeActivity.this, fbUtils, multiDetection);
        });

        binding.fabVideoScan.setOnClickListener(view -> {
            closeFabMenu();
            DialogUtils.showVideoDetectionDialog(HomeActivity.this, fbUtils, videoDetection);
        });

        binding.fabUploadScan.setOnClickListener(view -> {
            closeFabMenu();
            startActivity(new Intent(HomeActivity.this, UploadScanActivity.class));
        });

        binding.notificationImg.setOnClickListener(view -> {
            showViewNotificationDialog();
        });

        binding.searchBar.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, SearchActivity.class));
        });

        binding.viewAllScanHistoryBtn.setOnClickListener(view -> {
            startActivity(new Intent(HomeActivity.this, ScanHistoryActivity.class));
        });

        getUserData();
        getAllNotifications();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh scan reports when returning from scan activities
        if (scanHistoryAdapter != null) {
            getScanReportData();
        }
        // Auto-sync offline reports to Firebase when internet is available
        syncOfflineReports();
    }

    private void syncOfflineReports() {
        new Thread(() -> {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
                String reportsJson = prefs.getString("reports", "{}");
                org.json.JSONObject reports = new org.json.JSONObject(reportsJson);
                java.util.Iterator<String> keys = reports.keys();
                boolean anySynced = false;

                while (keys.hasNext()) {
                    String scanId = keys.next();
                    org.json.JSONObject r = reports.getJSONObject(scanId);
                    if (r.optBoolean("synced", false)) continue; // Already synced

                    // Try to push to Firebase
                    try {
                        // Build Firebase-compatible report (no local-only fields)
                        String fbJson = "{\"id\":\"" + r.optString("id") + "\"," +
                                "\"userId\":\"" + r.optString("userId") + "\"," +
                                "\"imageUrl\":\"" + r.optString("imageUrl", "").replace("\\", "\\\\") + "\"," +
                                "\"model\":\"" + r.optString("model") + "\"," +
                                "\"status\":\"" + r.optString("status") + "\"," +
                                "\"date\":\"" + r.optString("date") + "\"}";

                        OkHttpClient client = new OkHttpClient.Builder()
                                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                .build();
                        String url = "https://bill-sense-aec6b-default-rtdb.firebaseio.com/Scan%20Report/"
                                + userId + "/" + scanId + ".json";
                        RequestBody body = RequestBody.create(fbJson,
                                MediaType.parse("application/json"));
                        Request request = new Request.Builder().url(url).put(body).build();
                        Response response = client.newCall(request).execute();

                        if (response.isSuccessful()) {
                            r.put("synced", true);
                            anySynced = true;
                            Log.d("==HomeActivity", "Synced offline report to Firebase: " + scanId);
                        }
                        response.close();
                    } catch (Exception e) {
                        // No internet yet, skip
                        Log.d("==HomeActivity", "Sync skipped (no internet): " + scanId);
                        break; // Don't try more if network is down
                    }
                }

                if (anySynced) {
                    prefs.edit().putString("reports", reports.toString()).apply();
                    Log.d("==HomeActivity", "Offline reports synced to Firebase");
                }
            } catch (Exception e) {
                Log.e("==HomeActivity", "Sync error: " + e.getMessage());
            }
        }).start();
    }

    private void loadAnnouncements() {
        String url = "https://bill-sense-aec6b-default-rtdb.firebaseio.com/Announcements.json";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.d("==HomeActivity", "Announcements fetch failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "null";
                runOnUiThread(() -> {
                    try {
                        if (body.equals("null") || body.isEmpty()) {
                            binding.noAnnouncementsTv.setVisibility(View.VISIBLE);
                            binding.announcementContent.setVisibility(View.GONE);
                            return;
                        }
                        org.json.JSONObject json = new org.json.JSONObject(body);

                        // Find the latest active announcement
                        org.json.JSONObject latestAnnouncement = null;
                        String latestDate = "";
                        java.util.Iterator<String> keys = json.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            org.json.JSONObject a = json.getJSONObject(key);
                            if (!a.optBoolean("active", true)) continue;
                            String date = a.optString("date", "");
                            if (latestAnnouncement == null || date.compareTo(latestDate) >= 0) {
                                latestAnnouncement = a;
                                latestDate = date;
                            }
                        }

                        if (latestAnnouncement != null) {
                            binding.noAnnouncementsTv.setVisibility(View.GONE);
                            binding.announcementContent.setVisibility(View.VISIBLE);

                            binding.announcementTitleTv.setText(latestAnnouncement.optString("title", ""));
                            binding.announcementMessageTv.setText(latestAnnouncement.optString("message", ""));

                            // Format date
                            String dateStr = latestAnnouncement.optString("date", "");
                            if (!dateStr.isEmpty()) {
                                try {
                                    SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                    SimpleDateFormat outFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                                    Date parsed = inFmt.parse(dateStr);
                                    if (parsed != null) {
                                        binding.announcementDateTv.setText(outFmt.format(parsed));
                                    } else {
                                        binding.announcementDateTv.setText(dateStr);
                                    }
                                } catch (ParseException e) {
                                    binding.announcementDateTv.setText(dateStr);
                                }
                            }

                            // Priority badge
                            String priority = latestAnnouncement.optString("priority", "info").toLowerCase(Locale.ROOT);
                            switch (priority) {
                                case "warning":
                                    binding.announcementPriorityBadge.setText("WARNING");
                                    binding.announcementPriorityBadge.getBackground().setTint(
                                            getResources().getColor(R.color.yellow, getTheme()));
                                    binding.announcementPriorityBadge.setTextColor(
                                            getResources().getColor(R.color.text_color_black, getTheme()));
                                    break;
                                case "important":
                                    binding.announcementPriorityBadge.setText("IMPORTANT");
                                    binding.announcementPriorityBadge.getBackground().setTint(
                                            getResources().getColor(R.color.red, getTheme()));
                                    binding.announcementPriorityBadge.setTextColor(
                                            getResources().getColor(R.color.white, getTheme()));
                                    break;
                                default: // info
                                    binding.announcementPriorityBadge.setText("INFO");
                                    binding.announcementPriorityBadge.getBackground().setTint(
                                            getResources().getColor(R.color.colorPrimary, getTheme()));
                                    binding.announcementPriorityBadge.setTextColor(
                                            getResources().getColor(R.color.white, getTheme()));
                                    break;
                            }
                        } else {
                            binding.noAnnouncementsTv.setVisibility(View.VISIBLE);
                            binding.announcementContent.setVisibility(View.GONE);
                        }
                    } catch (Exception e) {
                        Log.e("==HomeActivity", "Announcements parse error: " + e.getMessage());
                        binding.noAnnouncementsTv.setVisibility(View.VISIBLE);
                        binding.announcementContent.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void getScanReportData() {
        binding.scanHistoryRv.setHasFixedSize(false);
        binding.scanHistoryRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false));
        scanHistoryAdapter = new ScanReportAdapter(this, scanReportHistoryArrayList);
        binding.scanHistoryRv.setAdapter(scanHistoryAdapter);

        // Load via direct REST + local cache (bypasses Firebase SDK which hangs)
        loadScanReportsDirectRest();
    }

    private void loadScanReportsDirectRest() {
        scanReportHistoryArrayList.clear();

        // Always load local cache first (instant)
        loadLocalScanReports();

        // Then try REST API for Firebase data
        String url = "https://bill-sense-aec6b-default-rtdb.firebaseio.com/Scan%20Report/" + userId + ".json";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                Log.d("==HomeActivity", "REST fetch failed (offline): " + e.getMessage());
                runOnUiThread(() -> {
                    trimToLatestTwo();
                    scanHistoryAdapter.notifyDataSetChanged();
                    updateScanHistoryVisibility();
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "null";
                runOnUiThread(() -> {
                    try {
                        if (body.equals("null") || body.isEmpty()) {
                            trimToLatestTwo();
                            scanHistoryAdapter.notifyDataSetChanged();
                            updateScanHistoryVisibility();
                            return;
                        }
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        java.util.Iterator<String> keys = json.keys();
                        java.util.Set<String> existingIds = new java.util.HashSet<>();
                        for (ScanReport sr : scanReportHistoryArrayList) {
                            if (sr.getId() != null) existingIds.add(sr.getId());
                        }

                        while (keys.hasNext()) {
                            String key = keys.next();
                            org.json.JSONObject r = json.getJSONObject(key);
                            String id = r.optString("id", key);
                            if (existingIds.contains(id)) continue;

                            ScanReport sr = new ScanReport();
                            sr.setId(id);
                            sr.setUserId(r.optString("userId", ""));
                            sr.setImageUrl(r.optString("imageUrl", ""));
                            sr.setModel(r.optString("model", ""));
                            sr.setStatus(r.optString("status", ""));
                            sr.setDate(r.optString("date", ""));

                            scanReportHistoryArrayList.add(sr);
                        }
                        Log.d("==HomeActivity", "Loaded " + json.length() + " reports from REST");
                    } catch (Exception e) {
                        Log.e("==HomeActivity", "REST parse error: " + e.getMessage());
                    }
                    sortScanReports();
                    trimToLatestTwo();
                    scanHistoryAdapter.notifyDataSetChanged();
                    updateScanHistoryVisibility();
                });
            }
        });
    }

    private void sortScanReports() {
        if (scanReportHistoryArrayList.isEmpty()) return;
        String[] datePatterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        scanReportHistoryArrayList.sort((sr1, sr2) -> {
            if (sr1.getDate() == null && sr2.getDate() == null) return 0;
            if (sr1.getDate() == null) return 1;
            if (sr2.getDate() == null) return -1;
            Date date1 = null, date2 = null;
            for (String pattern : datePatterns) {
                try { if (date1 == null) date1 = new SimpleDateFormat(pattern, Locale.getDefault()).parse(sr1.getDate()); } catch (ParseException ignored) {}
                try { if (date2 == null) date2 = new SimpleDateFormat(pattern, Locale.getDefault()).parse(sr2.getDate()); } catch (ParseException ignored) {}
            }
            if (date1 == null && date2 == null) return 0;
            if (date1 == null) return 1;
            if (date2 == null) return -1;
            return date2.compareTo(date1);
        });
    }

    /**
     * Trim the scan history list to show only the latest 2 items on the home screen preview.
     */
    private void trimToLatestTwo() {
        sortScanReports();
        while (scanReportHistoryArrayList.size() > 1) {
            scanReportHistoryArrayList.remove(scanReportHistoryArrayList.size() - 1);
        }
    }

    private void loadLocalScanReports() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
            String reportsJson = prefs.getString("reports", "{}");
            org.json.JSONObject reports = new org.json.JSONObject(reportsJson);
            java.util.Iterator<String> keys = reports.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                org.json.JSONObject r = reports.getJSONObject(key);
                ScanReport scanReport = new ScanReport();
                scanReport.setId(r.optString("id", key));
                scanReport.setUserId(r.optString("userId", ""));
                scanReport.setImageUrl(r.optString("imageUrl", ""));
                scanReport.setModel(r.optString("model", "on_device_scan"));
                scanReport.setStatus(r.optString("status", "UNKNOWN"));
                scanReport.setDate(r.optString("date", ""));

                // Check if already in list (avoid duplicates)
                boolean exists = false;
                for (ScanReport existing : scanReportHistoryArrayList) {
                    if (existing.getId() != null && existing.getId().equals(scanReport.getId())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    scanReportHistoryArrayList.add(scanReport);
                }
            }
            Log.d("==HomeActivity", "Loaded " + reports.length() + " local scan reports");
        } catch (Exception e) {
            Log.e("==HomeActivity", "Failed to load local scan reports: " + e.getMessage());
        }
    }

    private void updateScanHistoryVisibility() {
        if (scanReportHistoryArrayList.isEmpty()) {
            binding.noScanHistoryTv.setVisibility(View.VISIBLE);
            binding.scanHistoryRv.setVisibility(View.GONE);
            binding.viewAllScanHistoryBtn.setVisibility(View.GONE);
        } else {
            binding.noScanHistoryTv.setVisibility(View.GONE);
            binding.scanHistoryRv.setVisibility(View.VISIBLE);
            binding.viewAllScanHistoryBtn.setVisibility(View.VISIBLE);
        }
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
                    if (detections != null) {
                        Log.d("==detection", detections.getType());
                    }
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


    private void toggleFabMenu() {
        if (isAnimating) return;
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            openFabMenu();
        }
    }

    private void openFabMenu() {
        isFabMenuOpen = true;
        isAnimating = true;

        float density = getResources().getDisplayMetrics().density;
        float radiusPx = RADIUS_DP * density;

        // Rotate main FAB to X (45 degrees)
        ObjectAnimator fabRotate = ObjectAnimator.ofFloat(binding.fabScanBill, "rotation", 0f, 45f);
        fabRotate.setDuration(EXPAND_DURATION);
        fabRotate.setInterpolator(new OvershootInterpolator(2f));
        fabRotate.start();

        // Show overlay with fade
        binding.fabOverlay.setVisibility(View.VISIBLE);
        binding.fabOverlay.setAlpha(0f);
        ObjectAnimator overlayFade = ObjectAnimator.ofFloat(binding.fabOverlay, "alpha", 0f, 1f);
        overlayFade.setDuration(200);
        overlayFade.start();

        // Show the radial container
        binding.fabRadialContainer.setVisibility(View.VISIBLE);

        // Get the FAB layouts and their labels
        View[] fabLayouts = {
                binding.fabStandardScanLayout,
                binding.fabMultiScanLayout,
                binding.fabVideoScanLayout,
                binding.fabUploadScanLayout
        };

        View[] labels = {
                binding.labelStandardScan,
                binding.labelMultiScan,
                binding.labelVideoScan,
                binding.labelUploadScan
        };

        // Animate each mini-FAB to its radial position
        for (int i = 0; i < fabLayouts.length; i++) {
            View layout = fabLayouts[i];
            View label = labels[i];
            double angleRad = Math.toRadians(ARC_ANGLES[i]);

            float targetX = (float) (radiusPx * Math.cos(angleRad));
            float targetY = (float) (-radiusPx * Math.sin(angleRad));

            // Reset position to center
            layout.setTranslationX(0f);
            layout.setTranslationY(0f);
            layout.setScaleX(0f);
            layout.setScaleY(0f);
            layout.setAlpha(0f);
            layout.setVisibility(View.VISIBLE);
            label.setAlpha(0f);

            long delay = i * STAGGER_DELAY;

            // Translation X
            ObjectAnimator animX = ObjectAnimator.ofFloat(layout, "translationX", 0f, targetX);
            animX.setDuration(EXPAND_DURATION);
            animX.setStartDelay(delay);
            animX.setInterpolator(new OvershootInterpolator(1.5f));

            // Translation Y
            ObjectAnimator animY = ObjectAnimator.ofFloat(layout, "translationY", 0f, targetY);
            animY.setDuration(EXPAND_DURATION);
            animY.setStartDelay(delay);
            animY.setInterpolator(new OvershootInterpolator(1.5f));

            // Scale X
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(layout, "scaleX", 0f, 1f);
            scaleX.setDuration(EXPAND_DURATION);
            scaleX.setStartDelay(delay);
            scaleX.setInterpolator(new OvershootInterpolator(2f));

            // Scale Y
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(layout, "scaleY", 0f, 1f);
            scaleY.setDuration(EXPAND_DURATION);
            scaleY.setStartDelay(delay);
            scaleY.setInterpolator(new OvershootInterpolator(2f));

            // Alpha
            ObjectAnimator alpha = ObjectAnimator.ofFloat(layout, "alpha", 0f, 1f);
            alpha.setDuration(EXPAND_DURATION / 2);
            alpha.setStartDelay(delay);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(animX, animY, scaleX, scaleY, alpha);

            // Label fade-in after FAB reaches position
            ObjectAnimator labelFade = ObjectAnimator.ofFloat(label, "alpha", 0f, 1f);
            labelFade.setDuration(200);
            labelFade.setStartDelay(delay + LABEL_FADE_DELAY);

            if (i == fabLayouts.length - 1) {
                labelFade.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimating = false;
                    }
                });
            }

            set.start();
            labelFade.start();
        }
    }

    private void closeFabMenu() {
        if (!isFabMenuOpen) return;
        isFabMenuOpen = false;
        isAnimating = true;

        // Rotate main FAB back
        ObjectAnimator fabRotate = ObjectAnimator.ofFloat(binding.fabScanBill, "rotation", 45f, 0f);
        fabRotate.setDuration(COLLAPSE_DURATION);
        fabRotate.setInterpolator(new AnticipateInterpolator(1.5f));
        fabRotate.start();

        // Fade overlay
        ObjectAnimator overlayFade = ObjectAnimator.ofFloat(binding.fabOverlay, "alpha", 1f, 0f);
        overlayFade.setDuration(200);
        overlayFade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                binding.fabOverlay.setVisibility(View.GONE);
            }
        });
        overlayFade.start();

        // Collapse mini-FABs in reverse order
        View[] fabLayouts = {
                binding.fabUploadScanLayout,
                binding.fabVideoScanLayout,
                binding.fabMultiScanLayout,
                binding.fabStandardScanLayout
        };

        View[] labels = {
                binding.labelUploadScan,
                binding.labelVideoScan,
                binding.labelMultiScan,
                binding.labelStandardScan
        };

        for (int i = 0; i < fabLayouts.length; i++) {
            View layout = fabLayouts[i];
            View label = labels[i];

            long delay = i * 30L;

            // Fade label first
            ObjectAnimator labelFade = ObjectAnimator.ofFloat(label, "alpha", label.getAlpha(), 0f);
            labelFade.setDuration(100);
            labelFade.setStartDelay(delay);
            labelFade.start();

            // Translation back to center
            ObjectAnimator animX = ObjectAnimator.ofFloat(layout, "translationX", layout.getTranslationX(), 0f);
            animX.setDuration(COLLAPSE_DURATION);
            animX.setStartDelay(delay);
            animX.setInterpolator(new AnticipateInterpolator(1.5f));

            ObjectAnimator animY = ObjectAnimator.ofFloat(layout, "translationY", layout.getTranslationY(), 0f);
            animY.setDuration(COLLAPSE_DURATION);
            animY.setStartDelay(delay);
            animY.setInterpolator(new AnticipateInterpolator(1.5f));

            // Scale down
            ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(layout, "scaleX", 1f, 0f);
            scaleXAnim.setDuration(COLLAPSE_DURATION);
            scaleXAnim.setStartDelay(delay);

            ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(layout, "scaleY", 1f, 0f);
            scaleYAnim.setDuration(COLLAPSE_DURATION);
            scaleYAnim.setStartDelay(delay);

            // Alpha
            ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(layout, "alpha", 1f, 0f);
            alphaAnim.setDuration(COLLAPSE_DURATION);
            alphaAnim.setStartDelay(delay);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(animX, animY, scaleXAnim, scaleYAnim, alphaAnim);

            if (i == fabLayouts.length - 1) {
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        binding.fabRadialContainer.setVisibility(View.GONE);
                        for (View v : new View[]{
                                binding.fabStandardScanLayout,
                                binding.fabMultiScanLayout,
                                binding.fabVideoScanLayout,
                                binding.fabUploadScanLayout
                        }) {
                            v.setVisibility(View.INVISIBLE);
                        }
                        isAnimating = false;
                    }
                });
            }

            set.start();
        }
    }


    private void getUserData() {
        // Load cached name from PrefManager first (works offline)
        String cachedName = PrefManager.getInstance().getUserName();
        if (cachedName != null && !cachedName.isEmpty()) {
            binding.nameTxt.setText(cachedName);
        }

        // Then try to refresh from Firebase (best-effort)
        fbUtils.getUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnGetUserDataCallBack() {
            @Override
            public void onUserDataSuccess(Users user) {
                binding.nameTxt.setText(user.getName());
                if (user.getImage() != null && !user.getImage().isEmpty()) {
                    Glide.with(HomeActivity.this)
                            .load(user.getImage()).into(binding.profileImg);
                }
                // Cache for offline
                PrefManager.getInstance().saveUserData(userId, user.getEmail(), user.getName());
            }

            @Override
            public void onUserDataNotExist() {
                // Don't kick user out — use cached data
                Log.d("==HomeActivity", "User data not found in Firebase");
            }

            @Override
            public void onGetUserDataFailure(Exception exception) {
                // Don't kick user out — use cached data (works offline)
                Log.d("==HomeActivity", "User data fetch failed (offline): " + exception.getMessage());
            }
        });
    }

    @Override
    public void onPermissionGranted() {
        showToast(this, "Location permission granted");
        startActivity(new Intent(HomeActivity.this, CasesActivity.class));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
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
