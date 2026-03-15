package com.app.billsense.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.app.billsense.databinding.ItemBillsBinding;
import com.app.billsense.model.Bills;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class BillAdapter extends RecyclerView.Adapter<BillAdapter.ViewHolder> {
    Context context;
    ArrayList<Bills> billsArrayList;

    public BillAdapter(Context context, ArrayList<Bills> billsArrayList) {
        this.context = context;
        this.billsArrayList = billsArrayList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBillsBinding binding = ItemBillsBinding.inflate(LayoutInflater
                .from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Bills bills = billsArrayList.get(position);
        holder.binding.billTitle.setText(bills.getTitle());
        Glide.with(context).load(bills.getFrontImage())
                .into(holder.binding.billImg);
    }

    @Override
    public int getItemCount() {
        return billsArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemBillsBinding binding;
        public ViewHolder(ItemBillsBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
