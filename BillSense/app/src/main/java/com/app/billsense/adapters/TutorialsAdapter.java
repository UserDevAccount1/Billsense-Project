package com.app.billsense.adapters;

import static android.view.View.GONE;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemTutorialsBinding;
import com.app.billsense.model.Tutorials;
import com.app.billsense.utils.DialogUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class TutorialsAdapter extends RecyclerView.Adapter<TutorialsAdapter.ViewHolder> {
    Context context;
    ArrayList<Tutorials> tutorialsArrayList;

    public TutorialsAdapter(Context context, ArrayList<Tutorials> tutorialsArrayList) {
        this.context = context;
        this.tutorialsArrayList = tutorialsArrayList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTutorialsBinding binding = ItemTutorialsBinding.inflate(LayoutInflater
                .from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tutorials tutorials = tutorialsArrayList.get(position);
        holder.tutorialsBinding.tutorialTitle.setText(tutorials.getTitle());
        holder.tutorialsBinding.tutorialDesc.setText(tutorials.getDescription());
        if (tutorials.getMediaType().equals("image")) {
            holder.tutorialsBinding.tutorialImg.setVisibility(View.VISIBLE);
            holder.tutorialsBinding.tutorialVid.setVisibility(View.GONE);
            holder.tutorialsBinding.playVid.setVisibility(View.GONE);
            if (tutorials.getDownloadImageUrl() != null) {
                Glide.with(context).load(tutorials.getDownloadImageUrl())
                        .into(holder.tutorialsBinding.tutorialImg);
            }
        } else if (tutorials.getMediaType().equals("video")){
            holder.tutorialsBinding.tutorialImg.setVisibility(View.GONE);
            holder.tutorialsBinding.tutorialVid.setVisibility(View.VISIBLE);
            holder.tutorialsBinding.playVid.setVisibility(View.VISIBLE);
            if (tutorials.getDownloadVideoUrl() != null) {
                holder.tutorialsBinding.tutorialVid.setVideoPath(tutorials.getDownloadVideoUrl());
                holder.tutorialsBinding.playVid.setOnClickListener(view -> {
                    if (!holder.tutorialsBinding.tutorialVid.isPlaying()){
                        holder.tutorialsBinding.tutorialVid.start();
                        holder.tutorialsBinding.playVid.setVisibility(View.VISIBLE);
                    }
                });
            }
        } else {
            holder.tutorialsBinding.tutorialImg.setVisibility(GONE);
            holder.tutorialsBinding.tutorialVid.setVisibility(GONE);
            holder.tutorialsBinding.playVid.setVisibility(GONE);
        }

        holder.tutorialsBinding.tutorialImg.setOnClickListener(view -> {
            if (tutorials.getDownloadImageUrl() != null) {
                DialogUtils.displayFullImageDialog(context, tutorials.getTitle(),
                        tutorials.getDownloadImageUrl(), null);
            }
        });
        holder.tutorialsBinding.tutorialVid.setOnClickListener(view -> {
            if (tutorials.getDownloadVideoUrl() != null) {
                DialogUtils.displayFullVideoDialog(context, tutorials.getTitle(),
                        tutorials.getDownloadVideoUrl(), null);
            }
        });

    }

    @Override
    public int getItemCount() {
        return tutorialsArrayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemTutorialsBinding tutorialsBinding;

        public ViewHolder(@NonNull ItemTutorialsBinding binding) {
            super(binding.getRoot());
            this.tutorialsBinding = binding;
        }
    }
}
