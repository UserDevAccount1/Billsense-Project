package com.app.billsense.adapters;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.R;
import com.app.billsense.activities.ViewScanReportActivity;
import com.app.billsense.databinding.ItemScanHistoryAccordionBinding;
import com.app.billsense.model.ScanReport;
import com.bumptech.glide.Glide;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {
    private final Context context;
    private final ArrayList<ScanReport> scanReports;
    private int expandedPosition = -1;

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    public ScanHistoryAdapter(Context context, ArrayList<ScanReport> scanReports) {
        this.context = context;
        this.scanReports = scanReports;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemScanHistoryAccordionBinding binding = ItemScanHistoryAccordionBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanReport report = scanReports.get(position);
        boolean isExpanded = position == expandedPosition;

        // --- Collapsed header data ---

        // Scan type
        String model = report.getModel() != null ? report.getModel() : "Scan";
        holder.binding.scanTypeTv.setText(formatModelName(model));

        // Status badge
        String status = report.getStatus() != null ? report.getStatus().toUpperCase(Locale.ROOT) : "";
        holder.binding.statusBadgeTv.setText(status.isEmpty() ? "UNKNOWN" : status);
        setStatusBadgeColor(holder.binding.statusBadgeTv, status);

        // Date formatting
        String dateStr = report.getDate();
        if (dateStr != null && !dateStr.isEmpty()) {
            Date parsed = parseDate(dateStr);
            if (parsed != null) {
                SimpleDateFormat outFmt = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                holder.binding.scanDateTv.setText(outFmt.format(parsed));
            } else {
                holder.binding.scanDateTv.setText(dateStr);
            }
        } else {
            holder.binding.scanDateTv.setText("Date not available");
        }

        // Thumbnail
        String imageUrl = report.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (imageUrl.startsWith("/")) {
                Glide.with(context)
                        .load(new java.io.File(imageUrl))
                        .placeholder(R.drawable.scan_bill)
                        .error(R.drawable.scan_bill)
                        .centerCrop()
                        .into(holder.binding.scanThumbnailIv);
            } else {
                Glide.with(context)
                        .load(imageUrl)
                        .placeholder(R.drawable.scan_bill)
                        .error(R.drawable.scan_bill)
                        .centerCrop()
                        .into(holder.binding.scanThumbnailIv);
            }
        } else {
            holder.binding.scanThumbnailIv.setImageResource(R.drawable.scan_bill);
        }

        // --- Expanded details ---
        holder.binding.expandedDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.binding.expandArrowIv.setRotation(isExpanded ? 180f : 0f);

        if (isExpanded) {
            loadExpandedDetails(holder, report);
        }

        // Click to toggle
        holder.binding.collapsedHeader.setOnClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;

            int previousExpanded = expandedPosition;
            if (expandedPosition == adapterPos) {
                expandedPosition = -1;
                notifyItemChanged(adapterPos);
            } else {
                expandedPosition = adapterPos;
                notifyItemChanged(adapterPos);
                if (previousExpanded != -1) {
                    notifyItemChanged(previousExpanded);
                }
            }
        });

        // View Full Report button
        holder.binding.viewFullReportBtn.setOnClickListener(v -> {
            context.startActivity(new Intent(context, ViewScanReportActivity.class)
                    .putExtra("model", report.getModel())
                    .putExtra("userId", report.getUserId())
                    .putExtra("scanId", report.getId()));
        });
    }

    private void loadExpandedDetails(ViewHolder holder, ScanReport report) {
        // Show scanned image (full size)
        String imageUrl = report.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            holder.binding.scannedImageIv.setVisibility(View.VISIBLE);
            if (imageUrl.startsWith("/")) {
                Glide.with(context).load(new java.io.File(imageUrl))
                        .placeholder(R.drawable.scan_bill)
                        .into(holder.binding.scannedImageIv);
            } else {
                Glide.with(context).load(imageUrl)
                        .placeholder(R.drawable.scan_bill)
                        .into(holder.binding.scannedImageIv);
            }
        } else {
            holder.binding.scannedImageIv.setVisibility(View.GONE);
        }

        // Try to load cached scan details from SharedPreferences
        try {
            SharedPreferences prefs = context.getSharedPreferences("scan_results_cache", Context.MODE_PRIVATE);
            String scanId = report.getId();
            String cachedJson = prefs.getString(scanId, null);

            if (cachedJson != null) {
                org.json.JSONObject cached = new org.json.JSONObject(cachedJson);

                // Confidence score
                double confidence = cached.optDouble("confidence", -1);
                if (confidence >= 0) {
                    holder.binding.confidenceSection.setVisibility(View.VISIBLE);
                    int confidenceInt = (int) (confidence * 100);
                    if (confidenceInt > 100) confidenceInt = (int) confidence; // Already percentage
                    holder.binding.confidenceProgress.setProgress(confidenceInt);
                    holder.binding.confidenceTextTv.setText(String.format(Locale.US, "%.1f%%",
                            confidence > 1 ? confidence : confidence * 100));

                    // Color the progress bar based on confidence
                    if (confidenceInt >= 70) {
                        holder.binding.confidenceProgress.setProgressTintList(
                                android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.green, context.getTheme())));
                    } else if (confidenceInt >= 40) {
                        holder.binding.confidenceProgress.setProgressTintList(
                                android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.yellow, context.getTheme())));
                    } else {
                        holder.binding.confidenceProgress.setProgressTintList(
                                android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.red, context.getTheme())));
                    }
                } else {
                    holder.binding.confidenceSection.setVisibility(View.GONE);
                }

                // Security features
                org.json.JSONObject features = cached.optJSONObject("security_features");
                if (features == null) features = cached.optJSONObject("securityFeatures");
                if (features != null && features.length() > 0) {
                    holder.binding.securityFeaturesSection.setVisibility(View.VISIBLE);
                    holder.binding.securityFeaturesContainer.removeAllViews();

                    java.util.Iterator<String> keys = features.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        boolean detected = features.optBoolean(key, false);
                        String featureText = features.optString(key, "");

                        TextView featureView = new TextView(context);
                        featureView.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                        featureView.setPadding(0, 4, 0, 4);
                        featureView.setTextSize(12);

                        String displayName = formatFeatureName(key);
                        if (featureText.equals("true") || featureText.equals("false")) {
                            String icon = detected ? "\u2714" : "\u2718";
                            int color = detected ? Color.parseColor("#39b54a") : Color.parseColor("#FF0000");
                            featureView.setText(icon + " " + displayName);
                            featureView.setTextColor(color);
                        } else {
                            featureView.setText("\u2022 " + displayName + ": " + featureText);
                            featureView.setTextColor(Color.parseColor("#333333"));
                        }

                        holder.binding.securityFeaturesContainer.addView(featureView);
                    }
                } else {
                    holder.binding.securityFeaturesSection.setVisibility(View.GONE);
                }

                // Recommendation
                String recommendation = cached.optString("recommendation", "");
                if (recommendation.isEmpty()) recommendation = cached.optString("verdict", "");
                if (!recommendation.isEmpty()) {
                    holder.binding.recommendationTv.setVisibility(View.VISIBLE);
                    holder.binding.recommendationTv.setText(recommendation);
                } else {
                    holder.binding.recommendationTv.setVisibility(View.GONE);
                }

                // Processing info
                double processingTime = cached.optDouble("processing_time", -1);
                String modelUsed = cached.optString("model_type", "");
                if (processingTime >= 0 || !modelUsed.isEmpty()) {
                    holder.binding.processingInfoTv.setVisibility(View.VISIBLE);
                    StringBuilder info = new StringBuilder();
                    if (processingTime >= 0) {
                        info.append(String.format(Locale.US, "Processed in %.1fs", processingTime));
                    }
                    if (!modelUsed.isEmpty()) {
                        if (info.length() > 0) info.append(" | ");
                        info.append("Model: ").append(modelUsed);
                    }
                    holder.binding.processingInfoTv.setText(info.toString());
                } else {
                    holder.binding.processingInfoTv.setVisibility(View.GONE);
                }

            } else {
                // No cached details
                holder.binding.confidenceSection.setVisibility(View.GONE);
                holder.binding.securityFeaturesSection.setVisibility(View.GONE);
                holder.binding.recommendationTv.setVisibility(View.GONE);
                holder.binding.processingInfoTv.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e("ScanHistoryAdapter", "Error loading cached details: " + e.getMessage());
            holder.binding.confidenceSection.setVisibility(View.GONE);
            holder.binding.securityFeaturesSection.setVisibility(View.GONE);
            holder.binding.recommendationTv.setVisibility(View.GONE);
            holder.binding.processingInfoTv.setVisibility(View.GONE);
        }
    }

    private void setStatusBadgeColor(TextView badge, String status) {
        int bgColor;
        switch (status) {
            case "GENUINE":
            case "REAL":
                bgColor = Color.parseColor("#39b54a");
                break;
            case "COUNTERFEIT":
            case "FAKE":
                bgColor = Color.parseColor("#FF0000");
                break;
            case "LIKELY GENUINE":
            case "LIKELY_GENUINE":
                bgColor = Color.parseColor("#FF8800");
                break;
            default:
                bgColor = Color.parseColor("#888888");
                break;
        }
        badge.getBackground().setTint(bgColor);
    }

    private String formatModelName(String model) {
        if (model == null) return "Scan";
        switch (model.toLowerCase(Locale.ROOT)) {
            case "standard_scan": return "Standard Scan";
            case "multi_scan": return "Multi Scan";
            case "video_scan": return "Video Scan";
            case "upload_scan": return "Upload Scan";
            case "on_device_scan": return "On-Device Scan";
            default:
                // Capitalize first letter
                if (model.length() > 0) {
                    return model.substring(0, 1).toUpperCase(Locale.ROOT) + model.substring(1).replace("_", " ");
                }
                return model;
        }
    }

    private String formatFeatureName(String key) {
        if (key == null) return "";
        return key.replace("_", " ").replace("-", " ")
                .substring(0, 1).toUpperCase(Locale.ROOT) + key.replace("_", " ").replace("-", " ").substring(1);
    }

    private Date parseDate(String dateStr) {
        for (String pattern : DATE_PATTERNS) {
            try {
                return new SimpleDateFormat(pattern, Locale.getDefault()).parse(dateStr);
            } catch (ParseException ignored) {}
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return scanReports.size();
    }

    public void setExpandedPosition(int position) {
        this.expandedPosition = position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemScanHistoryAccordionBinding binding;

        public ViewHolder(ItemScanHistoryAccordionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
