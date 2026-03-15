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

import com.app.billsense.databinding.ItemTriviaBinding;
import com.app.billsense.model.Trivia;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class TriviaAdapter extends RecyclerView.Adapter<TriviaAdapter.ViewHolder> {
    Context context;
    ArrayList<Trivia> triviaArrayList;

    public TriviaAdapter(Context context, ArrayList<Trivia> triviaArrayList) {
        this.context = context;
        this.triviaArrayList = triviaArrayList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTriviaBinding binding = ItemTriviaBinding.inflate(LayoutInflater
                .from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Trivia trivia = triviaArrayList.get(position);
        holder.itemTriviaBinding.triviaTitle.setText(trivia.getTitle());
        holder.itemTriviaBinding.triviaDesc.setText(trivia.getDescription());
        if (trivia.getMediaType().equals("image")) {
            holder.itemTriviaBinding.triviaImg.setVisibility(View.VISIBLE);
            holder.itemTriviaBinding.triviaVid.setVisibility(View.GONE);
            holder.itemTriviaBinding.playVid.setVisibility(View.GONE);
            if (trivia.getDownloadImageUrl() != null) {
                Glide.with(context).load(trivia.getDownloadImageUrl())
                        .into(holder.itemTriviaBinding.triviaImg);
            }
        } else if (trivia.getMediaType().equals("video")){
            holder.itemTriviaBinding.triviaImg.setVisibility(View.GONE);
            holder.itemTriviaBinding.triviaVid.setVisibility(View.VISIBLE);
            holder.itemTriviaBinding.playVid.setVisibility(View.VISIBLE);
            if (trivia.getDownloadVideoUrl() != null) {
                holder.itemTriviaBinding.triviaVid.setVideoPath(trivia.getDownloadVideoUrl());
                MediaController mediaController = new MediaController(context);
                mediaController.setPadding(0,0,0,0);
                FrameLayout.LayoutParams controllerLayoutParams = new FrameLayout.LayoutParams(
                        200,
                        80 // Specify the desired height here (e.g., 30dp)
                );
                controllerLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                mediaController.setLayoutParams(controllerLayoutParams);
                holder.itemTriviaBinding.triviaVid.setMediaController(mediaController);
                mediaController.setAnchorView(holder.itemTriviaBinding.triviaVid);
                holder.itemTriviaBinding.playVid.setOnClickListener(view -> {
                    if (!holder.itemTriviaBinding.triviaVid.isPlaying()){
                        holder.itemTriviaBinding.triviaVid.start();
                        holder.itemTriviaBinding.playVid.setVisibility(View.GONE);
                    }
                });
            }
        } else {
            holder.itemTriviaBinding.triviaImg.setVisibility(GONE);
            holder.itemTriviaBinding.triviaVid.setVisibility(GONE);
            holder.itemTriviaBinding.playVid.setVisibility(GONE);
        }
    }

    @Override
    public int getItemCount() {
        return triviaArrayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemTriviaBinding itemTriviaBinding;

        public ViewHolder(@NonNull ItemTriviaBinding binding) {
            super(binding.getRoot());
            this.itemTriviaBinding = binding;
        }
    }
}
