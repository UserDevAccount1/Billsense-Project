package com.app.billsense.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemProcessDetectionBinding;
import com.app.billsense.model.Detections;
import com.app.billsense.utils.DialogUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class ProcessDetectionAdapter extends RecyclerView.Adapter<ProcessDetectionAdapter.ViewHolder> {
    Context context;
    ArrayList<Detections> detectionsArrayList;

    public ProcessDetectionAdapter(Context context, ArrayList<Detections> detectionsArrayList) {
        this.context = context;
        this.detectionsArrayList = detectionsArrayList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProcessDetectionBinding binding = ItemProcessDetectionBinding.inflate(LayoutInflater
                .from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Detections detections = detectionsArrayList.get(position);
        holder.binding.processDetectionTitle.setText(detections.getContent());
        if (detections.getImage() != null) {
            holder.binding.processDetectionImg.setVisibility(View.VISIBLE);
            Glide.with(context).load(detections.getImage()).into(holder.binding.processDetectionImg);
        } else {
            holder.binding.processDetectionImg.setVisibility(View.GONE);
        }
        holder.binding.processDetectionImg.setOnClickListener(view -> {
            DialogUtils.displayFullImageDialog(context, "Detection", detections.getImage(), null);
        });
    }

    @Override
    public int getItemCount() {
        return detectionsArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemProcessDetectionBinding binding;
        public ViewHolder(ItemProcessDetectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
