package com.app.billsense.activities;

import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.ChatsAdapter;
import com.app.billsense.databinding.ActivitySupportChatBinding;
import com.app.billsense.fcm.FcmNotificationSender;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Chats;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.PrefManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class SupportChatActivity extends AppCompatActivity {
    private ActivitySupportChatBinding binding;
    private String supportId, ticketNo, userId;
    private FBUtils fbUtils;
    private String senderRoom, receiverRoom;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chats> chatsArrayList = new ArrayList<>();
    private DatabaseReference chatListenerRef;
    private ValueEventListener chatValueListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySupportChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        supportId = getIntent().getStringExtra("id");
        ticketNo = getIntent().getStringExtra("ticketNo");
        userId = PrefManager.getInstance().getUserId();
        fbUtils = new FBUtils();

        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.chat));
        }

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        senderRoom = userId + ticketNo;
        receiverRoom = ticketNo + userId;

        binding.sendMsgBtn.setOnClickListener(v -> {
            String message = binding.inputChatMessage.getText().toString();
            if (!InputValidator.isValidMessage(message)){
                InputValidator.setEditTextError(binding.inputChatMessage, getString(R.string.type_a_message));
            }else {
                fbUtils.saveChatData(fbUtils.SUPPORT_PATH, supportId, userId, ticketNo,
                        senderRoom, receiverRoom,
                        new Pair<>("message", message));
                binding.inputChatMessage.setText("");
                binding.inputChatMessage.clearFocus();
                    FcmNotificationSender.get().sendNotificationToTopic(null, "New Message",
                            String.format("You have a new message regarding your ticket."),
                            userId, "1", "tickets_chat"
                    );
            }
        });

        getAllChats();


    }

    private void getAllChats() {
        binding.chatsRv.setHasFixedSize(true);
        binding.chatsRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false
        ));
        chatsAdapter = new ChatsAdapter(this, chatsArrayList, userId);
        binding.chatsRv.setAdapter(chatsAdapter);
        chatValueListener = fbUtils.getChatsData(fbUtils.SUPPORT_PATH, supportId, senderRoom,
                new FBInterface.OnChatDataRetrievedListener() {
            @Override
            public void onChatDataRetrieved(ArrayList<Chats> messagesList) {
                chatsArrayList.clear();
                chatsArrayList.addAll(messagesList);
                chatsAdapter.notifyDataSetChanged();
                if (chatsAdapter.getItemCount() > 0) {
                    binding.chatsRv.smoothScrollToPosition(chatsAdapter.getItemCount() - 1);
                }
            }

            @Override
            public void onNoMessagesFound() {
                chatsArrayList.clear();
                chatsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onChatDataRetrievalFailed(Exception exception) {
                chatsArrayList.clear();
                chatsAdapter.notifyDataSetChanged();
            }
        });

        // Store reference for cleanup in onDestroy
        chatListenerRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference(fbUtils.SUPPORT_PATH).child(supportId).child("Chats").child(senderRoom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListenerRef != null && chatValueListener != null) {
            chatListenerRef.removeEventListener(chatValueListener);
        }
    }
}