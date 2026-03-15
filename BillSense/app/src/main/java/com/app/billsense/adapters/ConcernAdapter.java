package com.app.billsense.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemConcernBinding;
import com.app.billsense.interfaces.ConcernInterface;
import com.app.billsense.model.Support;

import java.util.ArrayList;
import java.util.Locale;

public class ConcernAdapter extends RecyclerView.Adapter<ConcernAdapter.ViewHolder> implements Filterable {
    Context context;
    private ArrayList<Support> supportArrayListAll;      // Holds the original, unmodified list
    private ArrayList<Support> supportArrayListFiltered; // Holds the list for display
    ConcernInterface concernInterface;

    public ConcernAdapter(Context context, ArrayList<Support> initialList, ConcernInterface concernInterface) {
        this.context = context;
        this.supportArrayListAll = new ArrayList<>(initialList);
        this.supportArrayListFiltered = new ArrayList<>(initialList); // Start with all items
        this.concernInterface = concernInterface;
    }

    @NonNull
    @Override
    public ConcernAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConcernBinding binding = ItemConcernBinding.inflate(LayoutInflater
                .from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConcernAdapter.ViewHolder holder, int position) {
        if (position < supportArrayListFiltered.size()) { // Defensive check
            Support support = supportArrayListFiltered.get(position);
            holder.binding.textViewTicketNo.setText(support.getTicketNo());
            holder.binding.textViewUserName.setText(support.getUserName());
            holder.binding.textViewStatus.setText(support.getStatus());
            holder.binding.textViewDate.setText(support.getDate());
            holder.binding.textViewConcern.setText(support.getConcern());
            holder.binding.buttonDelete.setOnClickListener(view -> {
                concernInterface.onDeleteConcern(support.getId());
            });
            holder.binding.buttonChat.setOnClickListener(view -> {
                concernInterface.onChatConcern(support);
            });
        }
    }

    @Override
    public int getItemCount() {
        return supportArrayListFiltered.size();
    }

    public void updateData(ArrayList<Support> newSupportList) {
        this.supportArrayListAll.clear();
        this.supportArrayListAll.addAll(newSupportList);

        // When data is updated, we need to decide if we re-apply the current filter
        // or just show all. For simplicity, let's reset to all, and let the
        // TextWatcher in the Activity re-trigger the filter if there's text.
        this.supportArrayListFiltered.clear();
        this.supportArrayListFiltered.addAll(newSupportList); // Show all new data initially
        notifyDataSetChanged();
    }


    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String charString = "";
                if (constraint != null) {
                    charString = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                }
                ArrayList<Support> tempListFilteredItems;

                if (charString.isEmpty()) {
                    tempListFilteredItems = new ArrayList<>(supportArrayListAll); // A new list with all original items
                } else {
                    tempListFilteredItems = new ArrayList<>();
                    for (Support row : supportArrayListAll) { // Iterate over the original full list
                        if ((row.getConcern() != null && row.getConcern().toLowerCase(Locale.getDefault()).contains(charString)) ||
                                (row.getTicketNo() != null && row.getTicketNo().toLowerCase(Locale.getDefault()).contains(charString)) ||
                                (row.getStatus() != null && row.getStatus().toLowerCase(Locale.getDefault()).contains(charString)) ||
                                (row.getUserName() != null && row.getUserName().toLowerCase(Locale.getDefault()).contains(charString))) {
                            tempListFilteredItems.add(row);
                        }
                    }
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = tempListFilteredItems;
                filterResults.count = tempListFilteredItems.size();
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values instanceof ArrayList) {
                    supportArrayListFiltered = (ArrayList<Support>) results.values;
                } else {
                    // Fallback, though performFiltering should ensure ArrayList
                    supportArrayListFiltered = new ArrayList<>();
                    if (results.values != null) {
                        try {
                            // This cast might be risky if results.values is not what we expect
                            supportArrayListFiltered.addAll((ArrayList<Support>) results.values);
                        } catch (ClassCastException e) {
                        }
                    }
                }

                notifyDataSetChanged();
            }
        };
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemConcernBinding binding;

        public ViewHolder(ItemConcernBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
