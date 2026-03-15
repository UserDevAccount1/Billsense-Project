package com.app.billsense.activities;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.ChatAdapter;
import com.app.billsense.api.pojo.AbacusChatService;
import com.app.billsense.api.pojo.ApiResponse;
import com.app.billsense.api.pojo.ApiResponseMessage;
import com.app.billsense.databinding.ActivityChatBotBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.ChatMessage;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.InputValidator;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.ProgressDialogUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ChatBotActivity extends AppCompatActivity {
    private ActivityChatBotBinding binding;
    private String userId;
    private FBUtils fbUtils;

    private static final String TAG = "==ChatBotActivity";
    private final String DEPLOYMENT_TOKEN = "bdd8c69699f6472fb582bdda61ba8947";
    private final String DEPLOYMENT_ID = "f420900a0";

    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private int typingMessagePosition = -1;

    private final String WELCOME_MESSAGE_CONTENT = "\uD83D\uDC4B Hi there! I’m Billy — your AI assistant here at BillSense.\n" +
            "\n" +
            "\uD83D\uDE0A I’m here to help you:\n" +
            "\n" +
            "Check if your bills are genuine or suspicious\n" +
            "\n" +
            "Guide you through security features like watermarks, security threads, and tactile marks\n" +
            "\n" +
            "Explain how to use BillSense tools like Scan Bill, Compare Bill, and the Step-by-Step Detection Guide\n" +
            "\n" +
            "Share official resources, tutorials, and info on counterfeit prevention laws\n" +
            "\n" +
            "\uD83D\uDCCC Just ask me anything about Philippine currency, bill verification, or how to use the app — and I’ll do my best to help!\n" +
            "\n" +
            "Ready? Type your question anytime! \uD83E\uDDD0✅";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBotBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = PrefManager.getInstance().getUserId();
        fbUtils = new FBUtils();

        setupToolbar();
        setupRecyclerView();

        addWelcomeMessageAsFirst();

        setupSendButton();

        if (userId != null && !userId.isEmpty()) {
            getChatHistory();
        } else {
            Log.e(TAG, "User ID is null or empty, cannot fetch chat history. Welcome message is shown.");
            // No other messages to load, welcome message is already there.
            // If adapter was null during addWelcomeMessageAsFirst, re-notify (edge case)
            if (chatAdapter != null && chatAdapter.getItemCount() == 0 && !chatMessages.isEmpty()) {
                chatAdapter.notifyItemInserted(0);
            }
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = binding.toolbar; // Using binding
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // getSupportActionBar().setTitle(getString(R.string.chat)); // Already set in XML
        }
        toolbar.setNavigationOnClickListener(view -> finish());
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_chat, menu); // Inflate the menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_chat) {
            showClearChatConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showClearChatConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Chat History")
                .setMessage("Are you sure you want to permanently delete all chat messages for this conversation?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    clearChatDataFromFirebase();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.ic_delete) // Optional: show an icon in the dialog
                .show();
    }

    private void clearChatDataFromFirebase() {
        showProgressDialog(this);
        fbUtils.deleteDataFromPath(fbUtils.BILLY_CHAT_PATH, userId, new FBInterface.OnDeleteDataCallBack() {
            @Override
            public void onDeleteDataSuccess() {
                Toast.makeText(ChatBotActivity.this, "Chat history cleared.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Chat history cleared successfully for user: " + userId);
                hideProgressDialog();
            }

            @Override
            public void onDeleteDataFailure(Exception e) {
                Toast.makeText(ChatBotActivity.this, "Failed to clear chat history.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to clear chat history for user: " + userId, e);
                hideProgressDialog();
            }
        });
    }

    private void setupRecyclerView() {
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.chatsRv.setLayoutManager(layoutManager);
        binding.chatsRv.setAdapter(chatAdapter);
    }

    private void addWelcomeMessageAsFirst() {
        if (!isWelcomeMessagePresent()) { // Only add if not already present (e.g., on activity recreation)
            ChatMessage welcomeChatMessage = new ChatMessage(WELCOME_MESSAGE_CONTENT, ChatMessage.TYPE_BOT, false);
            // Ensure timestamp is sensible if it's always the first message conceptually
            // welcomeChatMessage.setTimestamp(0); // Or Long.MIN_VALUE to ensure it's always first if sorting later
            chatMessages.add(0, welcomeChatMessage);
            if (chatAdapter != null) {
                chatAdapter.notifyItemInserted(0);
            }
        }
    }

    private boolean isWelcomeMessagePresent() {
        if (!chatMessages.isEmpty()) {
            ChatMessage firstMessage = chatMessages.get(0);
            return firstMessage.getMessageType() == ChatMessage.TYPE_BOT &&
                    firstMessage.getMessage().equals(WELCOME_MESSAGE_CONTENT) &&
                    !firstMessage.isTyping();
        }
        return false;
    }

    private void setupSendButton() {
        binding.sendMsgBtn.setOnClickListener(v -> {
            String messageText = binding.inputChatMessage.getText().toString().trim();
            if (InputValidator.isValidMessage(messageText)) {
                addUserMessage(messageText);
                fetchChatResponseFromApi(messageText);
                binding.inputChatMessage.setText(""); // Clear input field
            }
        });
    }

    private void addUserMessage(String message) {
        if (userId == null) {
            Toast.makeText(this, "Error: User not identified.", Toast.LENGTH_SHORT).show();
            return;
        }
        ChatMessage chatMessage = new ChatMessage(message, ChatMessage.TYPE_USER);
        chatMessage.setSenderId(userId);
        chatMessages.add(chatMessage);
        if (chatAdapter != null) {
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        }
        scrollToBottom();
    }

    private void showTypingIndicator() {
        removePreviousTypingMessageIfExists(); // Ensure only one typing indicator

        ChatMessage typingMessage = new ChatMessage(null, ChatMessage.TYPE_BOT, true);
        chatMessages.add(typingMessage);
        typingMessagePosition = chatMessages.size() - 1;
        if (chatAdapter != null) {
            chatAdapter.notifyItemInserted(typingMessagePosition);
        }
        scrollToBottom();
    }

    private void removePreviousTypingMessageIfExists() {
        if (typingMessagePosition != -1 && typingMessagePosition < chatMessages.size()) {
            ChatMessage msg = chatMessages.get(typingMessagePosition);
            if (msg.isTyping()) { // Check if it's indeed the typing message
                chatMessages.remove(typingMessagePosition);
                if (chatAdapter != null) {
                    chatAdapter.notifyItemRemoved(typingMessagePosition);
                }
            }
        }
        typingMessagePosition = -1; // Reset position
    }

    private void updateBotMessageAfterTyping(String messageContent) {
        removePreviousTypingMessageIfExists(); // Remove "Bot is typing..."

        ChatMessage botMessage = new ChatMessage(messageContent, ChatMessage.TYPE_BOT, false);
        // botMessage.setSenderId("bot"); // Already handled in constructor
        // Timestamp is set in constructor

        chatMessages.add(botMessage); // Add the actual bot message to the end
        if (chatAdapter != null) {
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        }
        scrollToBottom();
    }

    private void updateBotMessage(String messageContent) {
        if (typingMessagePosition != -1 && typingMessagePosition < chatMessages.size()) {
            ChatMessage botMessage = chatMessages.get(typingMessagePosition);
            if (botMessage.getMessageType() == ChatMessage.TYPE_BOT && botMessage.isTyping()) {
                botMessage.setMessage(messageContent);
                botMessage.setTyping(false); // No longer typing
                chatAdapter.notifyItemChanged(typingMessagePosition);
            } else {
                // Typing indicator was possibly removed or this is an unexpected state
                // Add as a new message
                addFinalBotMessage(messageContent);
            }
        } else {
            // No typing indicator was shown, or its position is lost, add as new
            addFinalBotMessage(messageContent);
        }
        typingMessagePosition = -1; // Reset position
        scrollToBottom(); // May not be needed if item is just updated, but good for consistency
    }

    private void addFinalBotMessage(String message) {
        ChatMessage chatMessage = new ChatMessage(message, ChatMessage.TYPE_BOT, false);
        chatMessages.add(chatMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
        typingMessagePosition = -1; // Ensure it's reset
    }


    private void addBotMessage(String message) { // For initial welcome, not for API responses
        ChatMessage chatMessage = new ChatMessage(message, ChatMessage.TYPE_BOT, false); // Not typing
        chatMessages.add(chatMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            binding.chatsRv.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    private void fetchChatResponseFromApi(String userMessage) {
        showTypingIndicator();

        AbacusChatService.getChatResponseAsync(DEPLOYMENT_TOKEN, DEPLOYMENT_ID, userMessage,
                new AbacusChatService.AbacusChatCallback() {
                    @Override
                    public void onSuccess(ApiResponse apiResponse) {
                        runOnUiThread(() -> {
                            String botResponseTextStr = "Sorry, I couldn't get a response. Please try again.";
                            boolean validResponse = false;

                            if (apiResponse != null && apiResponse.getResult() != null &&
                                    apiResponse.getResult().getMessages() != null &&
                                    !apiResponse.getResult().getMessages().isEmpty()) {
                                StringBuilder botResponseTextBuilder = new StringBuilder();
                                for (ApiResponseMessage msg : apiResponse.getResult().getMessages()) {
                                    if (!msg.isUser() && msg.getText() != null && !msg.getText().isEmpty()) {
                                        botResponseTextBuilder.append(msg.getText().trim());
                                        validResponse = true;
                                        break; // Take the first AI message
                                    }
                                }
                                if (validResponse && botResponseTextBuilder.length() > 0) {
                                    botResponseTextStr = botResponseTextBuilder.toString();
                                }
                            }
                            updateBotMessageAfterTyping(botResponseTextStr); // This adds the final bot message
                            if (validResponse) { // Only save if the response was considered valid
                                saveChatMessagesToDatabase(userMessage, botResponseTextStr);
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            android.util.Log.e(TAG, "API Error: " + errorMessage);
                            updateBotMessageAfterTyping("Error: " + errorMessage + ". Please try again.");
                            Toast.makeText(ChatBotActivity.this, "API Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void saveChatMessagesToDatabase(String userMessage, String botResponseMessage) {
        if (userId != null && !userId.isEmpty()) {
            // FBUtils.saveBotChatData will save both user and bot messages with timestamps
            fbUtils.saveBotChatData(fbUtils.BILLY_CHAT_PATH, userId, userMessage, botResponseMessage);
        } else {
            android.util.Log.d(TAG, "Cannot save chat messages: User ID is null.");
        }
    }


    private void getChatHistory() {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "Cannot fetch chat history: User ID is null.");
            return;
        }
        String userChatPath = fbUtils.BILLY_CHAT_PATH + "/" + userId;

        FirebaseDatabase.getInstance().getReference(userChatPath)
                .orderByChild("timestamp")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        runOnUiThread(() -> {
                            List<ChatMessage> newFetchedMessages = new ArrayList<>();
                            for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                                ChatMessage chatMessage = messageSnapshot.getValue(ChatMessage.class);
                                if (chatMessage != null) {
                                    if (chatMessage.getId() == null) { // Set ID from Firebase key if not in object
                                        chatMessage.setId(messageSnapshot.getKey());
                                    }
                                    newFetchedMessages.add(chatMessage);
                                }
                            }

                            // --- More Robust Merging Logic ---
                            List<ChatMessage> messagesToDisplay = new ArrayList<>();

                            // 1. Always add/keep the welcome message if present
                            if (isWelcomeMessagePresent() && !chatMessages.isEmpty()) {
                                messagesToDisplay.add(chatMessages.get(0)); // Keep the existing welcome message object
                            } else if (!isWelcomeMessagePresent()) {
                                ChatMessage welcomeMsg = new ChatMessage(WELCOME_MESSAGE_CONTENT, ChatMessage.TYPE_BOT, false);
                                messagesToDisplay.add(welcomeMsg);
                            }


                            HashSet<String> addedMessageIds = new HashSet<>();
                            for (ChatMessage fetchedMsg : newFetchedMessages) {
                                if (fetchedMsg.getId() != null && !addedMessageIds.contains(fetchedMsg.getId())) {
                                    messagesToDisplay.add(fetchedMsg);
                                    addedMessageIds.add(fetchedMsg.getId());
                                }
                            }

                           chatMessages.clear();
                            chatMessages.addAll(messagesToDisplay);

                            if (chatAdapter != null) {
                                chatAdapter.notifyDataSetChanged(); // Still simplest for now
                            }

                            // Only scroll if there are more messages than just the welcome one.
                            if (chatMessages.size() > (isWelcomeMessagePresent() ? 1 : 0)) {
                                scrollToBottom();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "Failed to fetch chat history: " + databaseError.getMessage());
                            Toast.makeText(ChatBotActivity.this, "Failed to load chat history.", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }



}