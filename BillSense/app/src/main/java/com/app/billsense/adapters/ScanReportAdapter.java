package com.app.billsense.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.app.billsense.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.activities.ViewScanReportActivity;
import com.app.billsense.databinding.ItemScanReportBinding;
import com.app.billsense.model.ScanReport;
import com.app.billsense.utils.DialogUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class ScanReportAdapter extends RecyclerView.Adapter<ScanReportAdapter.ViewHolder> {
    Context context;
    ArrayList<ScanReport> scanReportArrayList;

    public ScanReportAdapter(Context context, ArrayList<ScanReport> scanReportArrayList) {
        this.context = context;
        this.scanReportArrayList = scanReportArrayList;
    }

    @NonNull
    @Override
    public ScanReportAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemScanReportBinding binding = ItemScanReportBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ScanReportAdapter.ViewHolder holder, int position) {
        ScanReport scanReport = scanReportArrayList.get(position);
        holder.binding.scanTitleTv.setText(scanReport.getModel());
        String imageUrl = scanReport.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (imageUrl.startsWith("/")) {
                // Local file path
                Glide.with(context)
                        .load(new java.io.File(imageUrl))
                        .placeholder(R.drawable.scan_bill)
                        .error(R.drawable.scan_bill)
                        .into(holder.binding.scanResultIv);
            } else {
                // Remote URL
                Glide.with(context)
                        .load(imageUrl)
                        .placeholder(R.drawable.scan_bill)
                        .error(R.drawable.scan_bill)
                        .into(holder.binding.scanResultIv);
            }
        } else {
            holder.binding.scanResultIv.setImageResource(R.drawable.scan_bill);
        }
        // Show status and model info
        String title = scanReport.getModel() != null ? scanReport.getModel() : "Scan";
        String status = scanReport.getStatus() != null ? scanReport.getStatus() : "";
        if (!status.isEmpty()) {
            title = status + " — " + title;
        }
        holder.binding.scanTitleTv.setText(title);
        holder.binding.scanResultIv.setOnClickListener(view -> {
            DialogUtils.displayFullImageDialog(context,"Scan Report", scanReport.getImageUrl(), null);
        });
        holder.binding.viewReportBtn.setOnClickListener(view -> {
            context.startActivity(new Intent(context, ViewScanReportActivity.class)
                    .putExtra("model", scanReport.getModel())
                    .putExtra("userId", scanReport.getUserId())
                    .putExtra("scanId", scanReport.getId()));
        });
    }

    @Override
    public int getItemCount() {
        return scanReportArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemScanReportBinding binding;
        public ViewHolder(ItemScanReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
