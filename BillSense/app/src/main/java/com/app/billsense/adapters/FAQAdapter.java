package com.app.billsense.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.app.billsense.databinding.ItemFaqBinding;
import com.app.billsense.model.FAQs;

import java.util.ArrayList;

public class FAQAdapter extends RecyclerView.Adapter<FAQAdapter.ViewHolder> {
    Context context;
    ArrayList<FAQs> faQsArrayList;

    public FAQAdapter(Context context, ArrayList<FAQs> faQsArrayList) {
        this.context = context;
        this.faQsArrayList = faQsArrayList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFaqBinding binding = ItemFaqBinding.inflate(LayoutInflater
                .from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FAQs faQs = faQsArrayList.get(position);
        holder.itemFaqBinding.questionTv.setText(faQs.getQuestion());
        holder.itemFaqBinding.answerTv.setText(faQs.getAnswer());
    }

    @Override
    public int getItemCount() {
        return faQsArrayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemFaqBinding itemFaqBinding;

        public ViewHolder(@NonNull ItemFaqBinding binding) {
            super(binding.getRoot());
            this.itemFaqBinding = binding;
        }
    }
}
