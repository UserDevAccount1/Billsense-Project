package com.app.billsense.adapters;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.activities.SupportActivity;
import com.app.billsense.databinding.ItemConcernBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Support;
import com.app.billsense.utils.FBUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class ConcernHistoryAdapter extends RecyclerView.Adapter<ConcernHistoryAdapter.ViewHolder> {
    Context context;
    ArrayList<Support> supportArrayList;
    FBUtils fbUtils;

    public ConcernHistoryAdapter(Context context, ArrayList<Support> supportArrayList) {
        this.context = context;
        this.supportArrayList = supportArrayList;
        fbUtils = new FBUtils();
    }

    @NonNull
    @Override
    public ConcernHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConcernBinding binding = ItemConcernBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConcernHistoryAdapter.ViewHolder holder, int position) {
        Support support = supportArrayList.get(position);
        holder.binding.textViewTicketNo.setText(support.getTicketNo());
        holder.binding.textViewUserName.setText(support.getUserName());
        holder.binding.textViewStatus.setText(support.getStatus());
        holder.binding.textViewDate.setText(support.getDate());
        holder.binding.textViewConcern.setText(support.getConcern());
        holder.binding.buttonDelete.setOnClickListener(view -> {
            MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(context);
            dialogBuilder.setTitle("Delete Concern");
            dialogBuilder.setMessage("Are you sure you want to delete this concern?");
            dialogBuilder.setPositiveButton("Yes", (dialogInterface, i) -> {
                showProgressDialog(context);
                fbUtils.deleteDataFromPath(fbUtils.SUPPORT_PATH, support.getId(), new FBInterface.OnDeleteDataCallBack() {
                    @Override
                    public void onDeleteDataSuccess() {
                        hideProgressDialog();
                        showToast(context, "Concern deleted successfully");
                        dialogInterface.dismiss();
                    }

                    @Override
                    public void onDeleteDataFailure(Exception e) {
                        hideProgressDialog();
                        showToast(context, "Failed to delete concern: " + e.getMessage());
                        dialogInterface.dismiss();
                    }
                });
            });
            dialogBuilder.setNegativeButton("No", (dialogInterface, i) -> {
                dialogInterface.dismiss();
            });
            dialogBuilder.show();
        });
        holder.binding.buttonChat.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return supportArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemConcernBinding binding;
        public ViewHolder(ItemConcernBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
