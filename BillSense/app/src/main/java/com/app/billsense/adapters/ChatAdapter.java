package com.app.billsense.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.billsense.R;
import com.app.billsense.model.ChatMessage;
import com.app.billsense.utils.ThreeDotsView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getMessageType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.TYPE_USER) {
            View view = inflater.inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(view);
        } else { // ChatMessage.TYPE_BOT
            View view = inflater.inflate(R.layout.item_bot_message, parent, false);
            return new BotMessageViewHolder(view);
        }
        // No separate typing view holder needed
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder.getItemViewType() == ChatMessage.TYPE_USER) {
            ((UserMessageViewHolder) holder).bind(message);
        } else {
            ((BotMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.user_message_text);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getMessage());
        }
    }

    static class BotMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ThreeDotsView typingDotsView; // Changed from LinearLayout

        BotMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.bot_message_text);
            typingDotsView = itemView.findViewById(R.id.typing_dots_view); // Use the new ID
        }

        void bind(ChatMessage message) {
            if (message.isTyping()) {
                typingDotsView.setVisibility(View.VISIBLE); // This will also trigger its animation
                messageText.setVisibility(View.GONE);
                messageText.setText("");
            } else {
                typingDotsView.setVisibility(View.GONE); // This will also stop its animation
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(message.getMessage());
            }
        }
    }
}
