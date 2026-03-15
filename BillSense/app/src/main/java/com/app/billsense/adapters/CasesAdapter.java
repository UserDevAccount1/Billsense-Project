package com.app.billsense.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemCasesBinding;
import com.app.billsense.model.Cases;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class CasesAdapter extends RecyclerView.Adapter<CasesAdapter.ViewHolder> {
    Context context;
    ArrayList<Cases> casesArrayList;

    public CasesAdapter(Context context, ArrayList<Cases> casesArrayList) {
        this.context = context;
        this.casesArrayList = casesArrayList;
    }

    @NonNull
    @Override
    public CasesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCasesBinding binding = ItemCasesBinding.inflate(LayoutInflater
                .from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CasesAdapter.ViewHolder holder, int position) {
        Cases cases = casesArrayList.get(position);
        holder.binding.textViewCaseTitle.setText(cases.getTitle());
        holder.binding.textViewUserName.setText(cases.getUserName());
        holder.binding.textViewAddress.setText(cases.getAddress());
        holder.binding.textViewDate.setText(cases.getCaseDate());
        holder.binding.textViewTime.setText(cases.getCaseTime());
        holder.binding.textViewDescription.setText(cases.getDescription());
        Glide.with(context).load(cases.getImage()).into(holder.binding.imageViewCaseImage);
    }

    @Override
    public int getItemCount() {
        return casesArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemCasesBinding binding;
        public ViewHolder(ItemCasesBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
