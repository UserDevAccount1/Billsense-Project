package com.app.billsense.utils;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.ChatMessage;
import com.app.billsense.model.Chats;
import com.app.billsense.model.Notifications;
import com.app.billsense.model.ScanReport;
import com.app.billsense.model.Tokens;
import com.app.billsense.model.Users;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FBUtils {
    public String USERS_PATH = "Users";
    public String AGENTS_PATH = "Agents";
    public String VOTING_POST = "Voting Posts";
    public String VOTING_POST_COMMENTS = "Comments";
    public String TUTORIAL_PATH = "Tutorials";
    public String TRIVIA_PATH = "Trivia";
    public String FAQ_PATH = "FAQs";
    public String SUPPORT_PATH = "Support";
    public String TOKENS_PATH = "Tokens";
    public String NOTIFICATIONS_PATH = "Notifications";
    public String CASES_PATH = "Cases";
    public String DETECTIONS_PATH = "Detections";
    public String BILLS_PATH = "Bills";
    public String BILLY_CHAT_PATH = "Billy Chats";
    public String SCAN_REPORT_PATH = "Scan Report";
    public String STANDARD_SCAN_PATH = "Standard Scan";
    public String MULTI_SCAN_PATH = "Multi Scan";
    public String VIDEO_SCAN_PATH = "Video Scan";


    private final FirebaseDatabase database;

    public FBUtils() {
        database = FirebaseDatabase.getInstance();
    }

    public void checkUserExists(String path, String userEmail,
                                final FBInterface.OnUserExistsCallBack listener) {
        DatabaseReference ref = database.getReference(path);
        ref.orderByChild("email").equalTo(userEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        listener.onUserExists(dataSnapshot.exists());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        listener.onUserExistsCheckFailed(databaseError.getMessage());
                    }
                });
    }

    public void checkUserIdExists(String path, String userId,
                                final FBInterface.OnUserExistsCallBack listener) {
        DatabaseReference ref = database.getReference(path);
        ref.orderByChild("id").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        listener.onUserExists(dataSnapshot.exists());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        listener.onUserExistsCheckFailed(databaseError.getMessage());
                    }
                });
    }

    @SafeVarargs
    public final void saveUserData(String path, String userEmail,
                                   final FBInterface.OnUserDataSaveCallBack listener,
                                   Pair<String, Object>... data) {
        checkUserExists(path, userEmail, new FBInterface.OnUserExistsCallBack() {
            @Override
            public void onUserExists(boolean exists) {
                if (exists) {
                    listener.onUserDataSaveFailure(new Exception("User with this email already exists"));
                } else {
                    DatabaseReference dataRef = database.getReference(path);

                    HashMap<String, Object> userData = new HashMap<>();
                    String userId = dataRef.push().getKey();
                    if (userId == null) {
                        listener.onUserDataSaveFailure(new Exception("Failed to generate Unique Id"));
                        return;
                    }
                    userData.put("id", userId);
                    for (Pair<String, Object> pair : data) {
                        userData.put(pair.first, pair.second);
                    }
                    dataRef.child(userId).updateChildren(userData)
                            .addOnSuccessListener(aVoid -> listener.onUserDataSaveSuccess())
                            .addOnFailureListener(listener::onUserDataSaveFailure);
                }
            }

            @Override
            public void onUserExistsCheckFailed(String errorMessage) {
                listener.onUserDataSaveFailure(new Exception(errorMessage));
            }
        });

    }

    public void loginUser(String path, String email, String password,
                          final FBInterface.OnLoginCallBack onLoginListener) {
        DatabaseReference ref = database.getReference(path);
        ref.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        String id = userSnapshot.child("id").getValue(String.class);
                        FirebaseMessaging.getInstance().getToken()
                                .addOnCompleteListener(task -> {
                                    if (!task.isSuccessful()) {
                                        Log.w("==FCMTOKEN", "Fetching FCM registration token failed",
                                                task.getException());
                                        return;
                                    }
                                    String token = task.getResult();
                                    updateFCMTokenCode(path, id, token);
                                });
                        String storedPassword = userSnapshot.child("password").getValue(String.class);
                        if (password.equals(storedPassword)) {
                            // Password matches, check verification status
                            String status = userSnapshot.child("status").getValue(String.class);
                            if ("verified".equals(status)) { // Assuming "verified" represents verified status
                                // Login successful, user is verified
                                onLoginListener.onLoginSuccess(userSnapshot);
                            } else {
                                // User is not verified, check verificationCode path
                                DatabaseReference verificationCodeRef = userSnapshot.getRef()
                                        .child("verificationCode");
                                verificationCodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot verificationCodeSnapshot) {
                                        if (!verificationCodeSnapshot.exists()) {
                                            // verificationCode is null, trigger events to send verification code
                                            onLoginListener.onVerificationCodeNeeded(userSnapshot);
                                        } else {
                                            // verificationCode exists, user is unverified but has a code
                                            onLoginListener.onLoginUnverified(userSnapshot);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError databaseError) {
                                        onLoginListener.onLoginFailed(databaseError.getMessage());
                                    }
                                });
                            }
                            return;
                        }
                    }
                    // Password doesn't match
                    onLoginListener.onLoginFailed("Incorrect password");
                } else {
                    // Email not found
                    onLoginListener.onLoginFailed("Email not found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                onLoginListener.onLoginFailed(databaseError.getMessage());
            }
        });
    }

    public void checkVerificationCode(String path, String userId, String code,
                                      final FBInterface.OnVerificationCallBack listener) {
        DatabaseReference userRef = database.getReference(path).child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String storedVerificationCode = dataSnapshot
                            .child("verificationCode").getValue(String.class);

                    if (storedVerificationCode != null && storedVerificationCode.equals(code)) {
                        userRef.child("status").setValue("verified")
                                .addOnSuccessListener(aVoid -> listener.onVerificationSuccess())
                                .addOnFailureListener(e -> listener.onVerificationFailed(
                                        "Failed to update status: " + e.getMessage()));
                    } else {
                        listener.onVerificationFailed("Invalid verification code");
                    }
                } else {
                    listener.onVerificationFailed("User not found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listener.onVerificationFailed("Database error: " + databaseError.getMessage());
            }
        });
    }

    public void updateVerificationCode(String path, String userId, String verificationCode,
                                       final FBInterface.OnVerificationCodeUpdateCallBack listener) {
        DatabaseReference userRef = database.getReference(path).child(userId);
        userRef.child("verificationCode").setValue(verificationCode)
                .addOnSuccessListener(aVoid -> listener.onVerificationCodeUpdated())
                .addOnFailureListener(e -> listener.onVerificationCodeUpdateFailed(e.getMessage()));
    }

    @SafeVarargs
    public final void updateUserData(String path, String id,
                                     final FBInterface.OnUserDataSaveCallBack listener,
                                     Pair<String, Object>... data) {
        DatabaseReference dataRef = database.getReference(path);

        HashMap<String, Object> userData = new HashMap<>();
        if (id == null) {
            listener.onUserDataSaveFailure(new Exception("Failed to get User Id"));
            return;
        }
        userData.put("id", id);
        for (Pair<String, Object> pair : data) {
            userData.put(pair.first, pair.second);
        }
        dataRef.child(id).updateChildren(userData)
                .addOnSuccessListener(aVoid -> listener.onUserDataSaveSuccess())
                .addOnFailureListener(listener::onUserDataSaveFailure);

    }

    public void getUserData(String path, String userId, final FBInterface.OnGetUserDataCallBack listener) {
        DatabaseReference userRef = database.getReference(path).child(userId);

        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Users users = dataSnapshot.getValue(Users.class);
                    listener.onUserDataSuccess(users);
                } else {
                    listener.onUserDataNotExist();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                listener.onGetUserDataFailure(new Exception("Database error: " + databaseError.getMessage()));
            }
        });
    }

    public void getAllDataFromPath(String path, final FBInterface.OnFetchDataCallBack onGetDataListener) {
        DatabaseReference ref = database.getReference(path);

        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    onGetDataListener.onFetchDataSuccess(dataSnapshot);
                } else {
                    // No data found at the specified path
                    onGetDataListener.onDataNotFound();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Error occurred while reading data
                onGetDataListener.onFetchDataFailed(
                        "Database error: " + databaseError.getMessage());
            }
        });
    }

    public void getAllDataFromPathSingle(String path, final FBInterface.OnFetchDataCallBack onGetDataListener) {
        DatabaseReference ref = database.getReference(path);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    onGetDataListener.onFetchDataSuccess(dataSnapshot);
                } else {
                    // No data found at the specified path
                    onGetDataListener.onDataNotFound();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Error occurred while reading data
                onGetDataListener.onFetchDataFailed(
                        "Database error: " + databaseError.getMessage());
            }
        });
    }

    public void deleteDataFromPath(String path, String id, final FBInterface.OnDeleteDataCallBack listener) {
        DatabaseReference dataRef = database.getReference(path);
        dataRef.child(id).removeValue()
                .addOnSuccessListener(aVoid -> listener.onDeleteDataSuccess())
                .addOnFailureListener(listener::onDeleteDataFailure);
    }

    @SafeVarargs
    public final void saveVotingPostData(String path, String id, final FBInterface.OnVotingPostDataSaveCallBack listener,
                                         Pair<String, Object>... data) {
        DatabaseReference dataRef = database.getReference(path);

        // Create a unique ID for the tutorial
        String tutorialId = id;

        if (tutorialId == null) {
            tutorialId = dataRef.push().getKey();
        }

        // Prepare the data as a HashMap
        HashMap<String, Object> tutorialData = new HashMap<>();
        tutorialData.put("id", tutorialId); // Add the unique ID
        for (Pair<String, Object> pair : data) {
            tutorialData.put(pair.first, pair.second);
        }

        // Save the tutorial data to Firebase
        dataRef.child(tutorialId).updateChildren(tutorialData)
                .addOnSuccessListener(aVoid -> listener.onVotingPostDataSaveSuccess())
                .addOnFailureListener(listener::onVotingPostDataSaveFailure);
    }

    @SafeVarargs
    public final void updateVotingPostData(String path, String id,
                                           final FBInterface.OnVotingPostDataSaveCallBack listener,
                                           Pair<String, Object>... data) {
        DatabaseReference dataRef = database.getReference(path);

        // Create a unique ID for the tutorial
        if (id == null) {
            listener.onVotingPostDataSaveFailure(new Exception("Failed to generate unique ID for " + path));
            return;
        }

        // Prepare the data as a HashMap
        HashMap<String, Object> tutorialData = new HashMap<>();
        tutorialData.put("id", id); // Add the unique ID
        for (Pair<String, Object> pair : data) {
            tutorialData.put(pair.first, pair.second);
        }

        // Save the tutorial data to Firebase
        dataRef.child(id).updateChildren(tutorialData)
                .addOnSuccessListener(aVoid -> listener.onVotingPostDataSaveSuccess())
                .addOnFailureListener(listener::onVotingPostDataSaveFailure);
    }


    @SafeVarargs
    public final void savePostComment(String path, String postId, String userId,
                                      final FBInterface.OnCommentSaveCallBack listener,
                                      Pair<String, Object>... data) {
        if (postId == null || postId.isEmpty()) {
            listener.onCommentSaveFailure(new Exception("Invalid postId"));
            return;
        }
        if (userId == null || userId.isEmpty()) {
            listener.onCommentSaveFailure(new Exception("Invalid user"));
            return;
        }

        DatabaseReference commentsRef = database.getReference(path + "/" +
                postId + "/" + VOTING_POST_COMMENTS); // Adjusted path

        String commentId = commentsRef.push().getKey();

        if (commentId == null) {
            listener.onCommentSaveFailure(new Exception("Failed to generate unique comment ID"));
            return;
        }
        HashMap<String, Object> commentData = new HashMap<>();
        commentData.put("id", commentId);  //Including commentId in the data

        for (Pair<String, Object> pair : data) {
            commentData.put(pair.first, pair.second);
        }

        commentsRef.child(commentId).updateChildren(commentData)
                .addOnSuccessListener(aVoid -> listener.onCommentSaveSuccess())
                .addOnFailureListener(listener::onCommentSaveFailure);
    }

    @SafeVarargs
    public final void savePostCommentLikes(String path, String postId, String userId, String cId,
                                           final FBInterface.OnCommentSaveCallBack listener,
                                           Pair<String, Object>... data) {
        if (postId == null || postId.isEmpty()) {
            listener.onCommentSaveFailure(new Exception("Invalid postId"));
            return;
        }
        if (userId == null || userId.isEmpty()) {
            listener.onCommentSaveFailure(new Exception("Invalid user"));
            return;
        }

        DatabaseReference commentsRef = database.getReference(path + "/" +
                postId + "/" + VOTING_POST_COMMENTS); // Adjusted path

        if (cId == null) {
            listener.onCommentSaveFailure(new Exception("Failed to generate unique comment ID"));
            return;
        }
        HashMap<String, Object> commentData = new HashMap<>();
        commentData.put("id", cId);  //Including commentId in the data

        for (Pair<String, Object> pair : data) {
            commentData.put(pair.first, pair.second);
        }

        commentsRef.child(cId).updateChildren(commentData)
                .addOnSuccessListener(aVoid -> listener.onCommentSaveSuccess())
                .addOnFailureListener(listener::onCommentSaveFailure);
    }

    @SafeVarargs
    public final void saveUserConcernData(String path, final FBInterface.OnConcernDataSaveCallBack listener,
                                          Pair<String, Object>... data) {
        DatabaseReference dataRef = database.getReference(path);

        String id = dataRef.push().getKey();
        if (id == null) {
            listener.onConcernDataSaveFailure(new Exception("Failed to generate unique ID for " + path));
            return;
        }

        // Prepare the data as a HashMap
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("id", id); // Add the unique ID
        for (Pair<String, Object> pair : data) {
            hashMap.put(pair.first, pair.second);
        }

        // Save the tutorial data to Firebase
        dataRef.child(id).updateChildren(hashMap)
                .addOnSuccessListener(aVoid -> listener.onConcernDataSaveSuccess())
                .addOnFailureListener(listener::onConcernDataSaveFailure);
    }

    public void updateFCMTokenCode(String path, String userId, String token) {
        DatabaseReference userRef = database.getReference(path).child(userId);
        userRef.child("fcmToken").setValue(token);
        DatabaseReference tokenRef = database.getReference(TOKENS_PATH).child(path);
        tokenRef.child(userId).setValue(token);

    }

    public void getAllUserToken(String path, String userPath,
                                final FBInterface.OnUserFcmTokensRetrievedListener listener) {
        if (path == null || path.isEmpty()) {
            if (listener != null) {
                listener.onError("TOKENS_PATH is not defined or is empty.");
            }
            Log.e("==Token", "Cannot get all FCM tokens: TOKENS_PATH is null or empty.");
            return;
        }

        DatabaseReference tokensRef = database.getReference(path).child(userPath);

        tokensRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                ArrayList<Tokens> userTokenList = new ArrayList<>();
                if (dataSnapshot.exists()) {
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        String userId = userSnapshot.getKey(); // Get the key (userId)
                        String token = userSnapshot.getValue(String.class); // Get the value (token)

                        if (userId != null && !userId.isEmpty() && token != null && !token.isEmpty()) {
                            userTokenList.add(new Tokens(userId, token));
                        } else {
                            Log.w("==Token", "Found null/empty userId or token. UserId: "
                                    + userId + ", Token: " + token);
                        }
                    }
                } else {
                    Log.d("==Token", "No tokens found at path: " + TOKENS_PATH);
                }

                if (listener != null) {
                    listener.onTokensRetrieved(userTokenList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("==Token", "Failed to retrieve all FCM tokens: " + databaseError.getMessage());
                if (listener != null) {
                    listener.onError(databaseError.getMessage());
                }
            }
        });
    }

    @SafeVarargs
    public final void saveNotificationData(String path, Pair<String, Object>... pairs) {
        DatabaseReference offerRef = database.getReference(path);
        String uniqueId = database.getReference(path).push().getKey();
        if (uniqueId == null) {
            return;
        }

        Map<String, Object> dataMap = new HashMap<>();
        for (Pair<String, Object> pair : pairs) {
            dataMap.put(pair.first, pair.second);
        }
        dataMap.put("id", uniqueId);
        offerRef.child(uniqueId).updateChildren(dataMap);
    }

    public void getNotificationByUserId(String path, String userId,
                                        FBInterface.OnNotificationRetrievedListener listener) {
        DatabaseReference notificationRef = database.getReference(path);
        notificationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<Notifications> notificationsList = new ArrayList<>();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Notifications notifications = dataSnapshot.getValue(Notifications.class);
                    if (notifications != null) {
                        if (notifications.getReceiverId().equals(userId)) {
                            notificationsList.add(notifications);
                        }
                    }
                }
                if (notificationsList.isEmpty()) {
                    listener.onNoNotificationsFound();
                } else {
                    listener.onNotificationRetrieved(notificationsList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onNotificationDataRetrievalFailed(error.toException());
            }
        });
    }


    @SafeVarargs
    public final void saveCaseEvidenceData(String path, FBInterface.OnCaseDataSaveCallBack listener,
                                           Pair<String, Object>... pairs) {
        DatabaseReference caseRef = database.getReference(path);
        String uniqueId = caseRef.push().getKey();
        if (uniqueId == null) {
            listener.onCaseDataSaveFailure(new Exception("Failed to generate unique ID for " + path));
            return;
        }
        Map<String, Object> dataMap = new HashMap<>();
        for (Pair<String, Object> pair : pairs) {
            dataMap.put(pair.first, pair.second);
        }
        dataMap.put("id", uniqueId);
        caseRef.child(uniqueId).updateChildren(dataMap)
                .addOnSuccessListener(aVoid -> listener.onCaseDataSaveSuccess())
                .addOnFailureListener(listener::onCaseDataSaveFailure);

    }

    @SafeVarargs
    public final void saveChatData(String path, String ticketId, String customerId, String technicianId,
                                   String senderRoom, String receiverRoom, Pair<String, Object>... pairs) {
        // Generate unique ID
        String uniqueId = database.getReference(path).push().getKey();
        DatabaseReference ticketRef = database.getReference(path);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("status", "Active");
        ticketRef.child(ticketId).updateChildren(dataMap);

        // Create data map
        Map<String, Object> senderDataMap = new HashMap<>();
        for (Pair<String, Object> pair : pairs) {
            senderDataMap.put(pair.first, pair.second);
        }
        senderDataMap.put("senderId", customerId);
        senderDataMap.put("messageId", uniqueId);

        Map<String, Object> receiverDataMap = new HashMap<>();
        for (Pair<String, Object> pair : pairs) {
            receiverDataMap.put(pair.first, pair.second);
        }
        receiverDataMap.put("senderId", technicianId);
        receiverDataMap.put("messageId", uniqueId);

        // Create references
        DatabaseReference senderRef = database.getReference(path).child(ticketId).child("Chats").child(senderRoom).child(uniqueId);
        DatabaseReference receiverRef = database.getReference(path).child(ticketId).child("Chats").child(receiverRoom).child(uniqueId);

        // Save data to both references
        senderRef.updateChildren(senderDataMap);
        receiverRef.updateChildren(receiverDataMap);
    }

    public void getChatsData(String path, String ticketId, String senderRoom, final FBInterface.OnChatDataRetrievedListener listener) {
        DatabaseReference chatsRef = database.getReference(path).child(ticketId).child("Chats").child(senderRoom);
        chatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<Chats> chatsList = new ArrayList<>();
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    Chats chat = chatSnapshot.getValue(Chats.class);
                    if (chat != null) {
                        chatsList.add(chat);
                    }
                }

                if (chatsList.isEmpty()) {
                    listener.onNoMessagesFound();
                } else {
                    listener.onChatDataRetrieved(chatsList);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onChatDataRetrievalFailed(error.toException());
            }
        });
    }


    public void saveBotChatData(String path, String userId, String message, String botResponseMessage) {
        DatabaseReference userMessagesRef = database.getReference(path).child(userId);
        // User's message
        String userMessageId = userMessagesRef.push().getKey();
        if (userMessageId == null) {
            Log.e("FBUtils", "Failed to generate user message ID for chat.");
            return;
        }
        Map<String, Object> userMessageData = new HashMap<>();
        userMessageData.put("id", userMessageId);
        userMessageData.put("senderId", userId); // Actual user's ID
        userMessageData.put("message", message);
        userMessageData.put("messageType", ChatMessage.TYPE_USER); // Store messageType
        userMessageData.put("timestamp", ServerValue.TIMESTAMP); // Use server timestamp
        userMessagesRef.child(userMessageId).setValue(userMessageData); // Use setValue for new nodes

        // Bot's response message
        String botMessageId = userMessagesRef.push().getKey();
        if (botMessageId == null) {
            Log.e("FBUtils", "Failed to generate bot message ID for chat.");
            return;
        }
        Map<String, Object> botMessageData = new HashMap<>();
        botMessageData.put("id", botMessageId);
        botMessageData.put("senderId", "bot");
        botMessageData.put("message", botResponseMessage);
        botMessageData.put("messageType", ChatMessage.TYPE_BOT); // Store messageType
        botMessageData.put("timestamp", ServerValue.TIMESTAMP); // Use server timestamp
        userMessagesRef.child(botMessageId).setValue(botMessageData); // Use setValue for new nodes
    }

    public final void saveAllScanData(String path, String userId, String scanId,
                                      FBInterface.OnScanReportDataSaveCallBack listener,
                                     Object sacnDataObject) {
        if (scanId == null || scanId.isEmpty() || userId == null || userId.isEmpty()) {
            listener.onScanReportDataSaveFailure(new Exception("Invalid scanId or userId for " + path));
            return;
        }
        DatabaseReference userRef = database.getReference(path).child(userId).child(scanId);
        userRef.setValue(sacnDataObject)
                .addOnSuccessListener(aVoid -> listener.onScanReportDataSaveSuccess())
                .addOnFailureListener(listener::onScanReportDataSaveFailure);
    }

    public final void saveScanReport(String path, String userId, ScanReport scanReport) {
        DatabaseReference userRef = database.getReference(path).child(userId).child(scanReport.getId());
        userRef.setValue(scanReport);

    }

}
