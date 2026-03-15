package com.app.billsense.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.app.billsense.databinding.ItemSearchResultUnifiedBinding;
import com.app.billsense.model.UnifiedSearchResult;

public class SearchResultsAdapter extends ListAdapter<UnifiedSearchResult, SearchResultsAdapter.ResultViewHolder> {

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(UnifiedSearchResult result);
    }

    public SearchResultsAdapter(OnItemClickListener listener) {
        super(new UnifiedDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchResultUnifiedBinding binding = ItemSearchResultUnifiedBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ResultViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        UnifiedSearchResult item = getItem(position);
        if (item != null) {
            holder.bind(item);
        }
    }

    class ResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultUnifiedBinding binding;

        ResultViewHolder(ItemSearchResultUnifiedBinding binding, OnItemClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(getItem(position));
                }
            });
        }

        void bind(UnifiedSearchResult result) {
            binding.textViewResultType.setText(result.getType());
            binding.textViewResultTitle.setText(result.getTitle());
            binding.textViewResultSnippet.setText(result.getSnippet());
        }
    }

    static class UnifiedDiffCallback extends DiffUtil.ItemCallback<UnifiedSearchResult> {
        @Override
        public boolean areItemsTheSame(@NonNull UnifiedSearchResult oldItem, @NonNull UnifiedSearchResult newItem) {
            return oldItem.getId().equals(newItem.getId()) && oldItem.getType().equals(newItem.getType());
        }

        @Override
        public boolean areContentsTheSame(@NonNull UnifiedSearchResult oldItem, @NonNull UnifiedSearchResult newItem) {
            // For simplicity, we are comparing titles and snippets.
            // For a more robust check, compare all relevant fields or implement equals() in your models.
            return oldItem.getTitle().equals(newItem.getTitle()) &&
                    oldItem.getSnippet().equals(newItem.getSnippet());
        }
    }
}

