package com.app.billsense.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemChatMessageBinding;
import com.app.billsense.model.Chats;

import java.util.ArrayList;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ViewHolder> {
    Context context;
    ArrayList<Chats> chatsList;
    String senderId;

    public ChatsAdapter(Context context, ArrayList<Chats> chatsList, String senderId) {
        this.context = context;
        this.chatsList = chatsList;
        this.senderId = senderId;
    }

    @NonNull
    @Override
    public ChatsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatsAdapter.ViewHolder holder, int position) {
        if (senderId.equals(chatsList.get(position).getSenderId())){
            holder.binding.messageReceivedTxt.setVisibility(View.GONE);
            holder.binding.messageSentTxt.setVisibility(View.VISIBLE);
            holder.binding.messageSentTxt.setText(chatsList.get(position).getMessage());
        }
        else {
            holder.binding.messageReceivedTxt.setVisibility(View.VISIBLE);
            holder.binding.messageSentTxt.setVisibility(View.GONE);
            holder.binding.messageReceivedTxt.setText(chatsList.get(position).getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return chatsList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemChatMessageBinding binding;
        public ViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
