package com.app.billsense.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemNotificationBinding;
import com.app.billsense.model.Notifications;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    Context context;
    ArrayList<Notifications> notificationsArrayList;
    private SimpleDateFormat outputFormat;

    public NotificationAdapter(Context context, ArrayList<Notifications> notificationsArrayList) {
        this.context = context;
        this.notificationsArrayList = notificationsArrayList;
        this.outputFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public NotificationAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationAdapter.ViewHolder holder, int position) {
        Notifications notifications = notificationsArrayList.get(position);
        holder.binding.notificationTitleTv.setText(notifications.getTitle());
        holder.binding.notificationBodyTv.setText(notifications.getBody());
        // Format the time
        String originalTime = notifications.getTime();
        if (originalTime != null && !originalTime.isEmpty()) {
            try {
                long timeInMillis = Long.parseLong(originalTime);
                Date date = new Date(timeInMillis);
                String formattedTime = outputFormat.format(date);
                holder.binding.notificationTimeTv.setText(formattedTime);
            } catch (NumberFormatException e) {
                Log.e("NotificationAdapter",
                        "Could not parse time string to long: " + originalTime, e);
                holder.binding.notificationTimeTv.setText(originalTime); // Fallback to original time
            } catch (Exception e) {
                // Catch any other unexpected errors during formatting
                Log.e("NotificationAdapter", "Error formatting time: " + originalTime, e);
                holder.binding.notificationTimeTv.setText(originalTime); // Fallback to original time
            }
        } else {
            holder.binding.notificationTimeTv.setText(""); // Or some placeholder like "N/A"
        }
    }

    @Override
    public int getItemCount() {
        return notificationsArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemNotificationBinding binding;
        public ViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
