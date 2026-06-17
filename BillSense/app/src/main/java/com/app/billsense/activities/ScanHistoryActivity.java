package com.app.billsense.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.ScanHistoryAdapter;
import com.app.billsense.databinding.ActivityScanHistoryBinding;
import com.app.billsense.model.ScanReport;
import com.app.billsense.utils.PrefManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class ScanHistoryActivity extends AppCompatActivity {
    private static final String TAG = "ScanHistoryActivity";
    private ActivityScanHistoryBinding binding;
    private ScanHistoryAdapter adapter;
    private ArrayList<ScanReport> allScanReports = new ArrayList<>();
    private ArrayList<ScanReport> filteredScanReports = new ArrayList<>();
    private String userId;

    // Filter state
    private String filterScanType = "All";
    private String filterStatus = "All";
    private String filterDateRange = "All Time";

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = PrefManager.getInstance().getUserId();

        // Toolbar
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Setup filters
        setupFilters();

        // Setup RecyclerView
        binding.scanHistoryRv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScanHistoryAdapter(this, filteredScanReports);
        binding.scanHistoryRv.setAdapter(adapter);

        // Scan Now button (empty state)
        binding.scanNowBtn.setOnClickListener(v -> {
            finish();
        });

        // Clear All button
        binding.clearAllBtn.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear Scan History")
                    .setMessage("Are you sure you want to delete all scan history? This cannot be undone.")
                    .setPositiveButton("Clear All", (dialog, which) -> clearAllScanHistory())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Load data
        loadAllScanReports();
    }

    private void setupFilters() {
        // Scan type filter
        String[] scanTypes = {"All", "Standard", "Multi", "Video", "Upload", "On-Device"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, scanTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.filterScanType.setAdapter(typeAdapter);
        binding.filterScanType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterScanType = scanTypes[position];
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Status filter
        String[] statuses = {"All", "Genuine", "Counterfeit", "Likely Genuine"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statuses);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.filterStatus.setAdapter(statusAdapter);
        binding.filterStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterStatus = statuses[position];
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Date range filter
        String[] dateRanges = {"All Time", "Recent", "Last 7 Days", "Last 30 Days"};
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dateRanges);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.filterDateRange.setAdapter(dateAdapter);
        binding.filterDateRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterDateRange = dateRanges[position];
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadAllScanReports() {
        binding.loadingProgress.setVisibility(View.VISIBLE);
        binding.scanHistoryRv.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);

        allScanReports.clear();

        // Load local offline reports first
        loadLocalScanReports();

        // Then load from Firebase REST
        String url = "https://bill-sense-aec6b-default-rtdb.firebaseio.com/Scan%20Report/" + userId + ".json";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                Log.d(TAG, "REST fetch failed: " + e.getMessage());
                runOnUiThread(() -> {
                    binding.loadingProgress.setVisibility(View.GONE);
                    sortAndApplyFilters();
                });
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "null";
                runOnUiThread(() -> {
                    try {
                        if (!body.equals("null") && !body.isEmpty()) {
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            java.util.Iterator<String> keys = json.keys();
                            java.util.Set<String> existingIds = new java.util.HashSet<>();
                            for (ScanReport sr : allScanReports) {
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

                                allScanReports.add(sr);
                            }
                            Log.d(TAG, "Loaded " + json.length() + " reports from REST");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "REST parse error: " + e.getMessage());
                    }
                    binding.loadingProgress.setVisibility(View.GONE);
                    sortAndApplyFilters();
                });
            }
        });
    }

    private void loadLocalScanReports() {
        try {
            SharedPreferences prefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
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

                boolean exists = false;
                for (ScanReport existing : allScanReports) {
                    if (existing.getId() != null && existing.getId().equals(scanReport.getId())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    allScanReports.add(scanReport);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load local scan reports: " + e.getMessage());
        }
    }

    private void sortAndApplyFilters() {
        // Sort by date descending
        allScanReports.sort((sr1, sr2) -> {
            if (sr1.getDate() == null && sr2.getDate() == null) return 0;
            if (sr1.getDate() == null) return 1;
            if (sr2.getDate() == null) return -1;
            Date date1 = parseDate(sr1.getDate());
            Date date2 = parseDate(sr2.getDate());
            if (date1 == null && date2 == null) return 0;
            if (date1 == null) return 1;
            if (date2 == null) return -1;
            return date2.compareTo(date1);
        });
        applyFilters();
    }

    private void applyFilters() {
        filteredScanReports.clear();

        Date now = new Date();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        long twentyFourHoursMs = 24L * 60 * 60 * 1000;

        for (ScanReport sr : allScanReports) {
            // Scan type filter
            if (!filterScanType.equals("All")) {
                String model = sr.getModel() != null ? sr.getModel().toLowerCase(Locale.ROOT) : "";
                boolean match = false;
                switch (filterScanType) {
                    case "Standard": match = model.contains("standard"); break;
                    case "Multi": match = model.contains("multi"); break;
                    case "Video": match = model.contains("video"); break;
                    case "Upload": match = model.contains("upload"); break;
                    case "On-Device": match = model.contains("on_device") || model.contains("ondevice"); break;
                }
                if (!match) continue;
            }

            // Status filter
            if (!filterStatus.equals("All")) {
                String status = sr.getStatus() != null ? sr.getStatus().toUpperCase(Locale.ROOT) : "";
                boolean match = false;
                switch (filterStatus) {
                    case "Genuine": match = status.contains("GENUINE") || status.contains("REAL"); break;
                    case "Counterfeit": match = status.contains("COUNTERFEIT") || status.contains("FAKE"); break;
                    case "Likely Genuine": match = status.contains("LIKELY"); break;
                }
                if (!match) continue;
            }

            // Date range filter
            if (!filterDateRange.equals("All Time")) {
                Date scanDate = sr.getDate() != null ? parseDate(sr.getDate()) : null;
                if (scanDate == null) continue;
                long diff = now.getTime() - scanDate.getTime();
                switch (filterDateRange) {
                    case "Recent":
                        if (diff > twentyFourHoursMs) continue;
                        break;
                    case "Last 7 Days":
                        if (diff > sevenDaysMs) continue;
                        break;
                    case "Last 30 Days":
                        if (diff > thirtyDaysMs) continue;
                        break;
                }
            }

            filteredScanReports.add(sr);
        }

        // Update count text
        binding.scanCountTv.setText("Showing " + filteredScanReports.size() + " of " + allScanReports.size() + " scans");

        // Reset expanded state
        adapter.setExpandedPosition(-1);
        adapter.notifyDataSetChanged();

        // Update visibility
        if (filteredScanReports.isEmpty()) {
            binding.scanHistoryRv.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
        } else {
            binding.scanHistoryRv.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    private Date parseDate(String dateStr) {
        for (String pattern : DATE_PATTERNS) {
            try {
                return new SimpleDateFormat(pattern, Locale.getDefault()).parse(dateStr);
            } catch (ParseException ignored) {}
        }
        return null;
    }

    private void clearAllScanHistory() {
        // Clear local cache
        SharedPreferences reportPrefs = getSharedPreferences("offline_scan_reports", MODE_PRIVATE);
        reportPrefs.edit().putString("reports", "{}").apply();

        // Clear scan results cache
        SharedPreferences cachePrefs = getSharedPreferences("scan_results_cache", MODE_PRIVATE);
        cachePrefs.edit().clear().apply();

        // Clear local scan images
        java.io.File scanImagesDir = new java.io.File(getFilesDir(), "scan_images");
        if (scanImagesDir.exists()) {
            java.io.File[] files = scanImagesDir.listFiles();
            if (files != null) {
                for (java.io.File f : files) f.delete();
            }
        }

        // Clear Firebase (best-effort)
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS).build();
                Request request = new Request.Builder()
                        .url("https://bill-sense-aec6b-default-rtdb.firebaseio.com/Scan%20Report/" + userId + ".json")
                        .delete()
                        .build();
                client.newCall(request).execute().close();
                Log.d(TAG, "Firebase scan history cleared");
            } catch (Exception e) {
                Log.d(TAG, "Firebase clear skipped (offline): " + e.getMessage());
            }
        }).start();

        // Clear UI
        allScanReports.clear();
        filteredScanReports.clear();
        adapter.notifyDataSetChanged();
        binding.scanCountTv.setText("Showing 0 of 0 scans");
        binding.scanHistoryRv.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        android.widget.Toast.makeText(this, "Scan history cleared", android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
