package com.app.billsense.interfaces;

import com.app.billsense.model.Cases;
import com.app.billsense.model.Chats;
import com.app.billsense.model.Notifications;
import com.app.billsense.model.ScanReport;
import com.app.billsense.model.Tokens;
import com.app.billsense.model.Users;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;

public class FBInterface {

    public interface ImageUploadCallback {
        void onImageUploadSuccess(String downloadUrl);
        void onImageUploadFailure(Exception exception);
    }

    public interface FileUploadCallback {
        void onFileUploadSuccess(String downloadUrl);
        void onFileUploadFailure(Exception exception);
        void onFileUploadProgress(double progress); // New method for progress updates
    }

    public interface OnUserExistsCallBack {
        void onUserExists(boolean exists);

        void onUserExistsCheckFailed(String errorMessage);
    }

    public interface OnUserDataSaveCallBack {
        void onUserDataSaveSuccess();

        void onUserDataSaveFailure(Exception exception);
    }

    public interface OnGetUserDataCallBack {
        void onUserDataSuccess(Users user);

        void onUserDataNotExist();

        void onGetUserDataFailure(Exception exception);
    }

    public interface OnLoginCallBack {
        void onLoginSuccess(DataSnapshot userSnapshot);

        void onLoginFailed(String errorMessage);

        void onLoginUnverified(DataSnapshot userSnapshot);

        void onVerificationCodeNeeded(DataSnapshot userSnapshot);
    }

    public interface OnVerificationCallBack {
        void onVerificationSuccess();

        void onVerificationFailed(String errorMessage);
    }

    public interface OnVerificationCodeUpdateCallBack {
        void onVerificationCodeUpdated();

        void onVerificationCodeUpdateFailed(String errorMessage);
    }

    public interface OnFetchDataCallBack {
        void onFetchDataSuccess(DataSnapshot dataSnapshot);
        void onDataNotFound();
        void onFetchDataFailed(String errorMessage);
    }

    public interface OnVotingPostDataSaveCallBack {
        void onVotingPostDataSaveSuccess();
        void onVotingPostDataSaveFailure(Exception exception);
    }

    public interface OnCommentSaveCallBack {
        void onCommentSaveSuccess();
        void onCommentSaveFailure(Exception e);
    }

    public interface OnConcernDataSaveCallBack {
        void onConcernDataSaveSuccess();
        void onConcernDataSaveFailure(Exception e);
    }

    public interface OnDeleteDataCallBack {
        void onDeleteDataSuccess();
        void onDeleteDataFailure(Exception e);
    }

    public interface OnNotificationRetrievedListener {
        void onNotificationRetrieved(ArrayList<Notifications> notificationsList);
        void onNoNotificationsFound();
        void onNotificationDataRetrievalFailed(Exception exception);
    }

    public interface OnUserFcmTokensRetrievedListener {
        void onTokensRetrieved(ArrayList<Tokens> userTokens);
        void onError(String errorMessage);
    }

    public interface OnCaseDataSaveCallBack {
        void onCaseDataSaveSuccess();
        void onCaseDataSaveFailure(Exception e);
    }

    public interface OnCaseDataRetrievedListener {
        void onCaseDataRetrieved(ArrayList<Cases> casesList);
        void onNoCasesFound();
        void onCaseDataRetrievalFailed(Exception exception);
    }

    public interface OnChatDataRetrievedListener {
        void onChatDataRetrieved(ArrayList<Chats> messagesList);
        void onNoMessagesFound();
        void onChatDataRetrievalFailed(Exception exception);
    }

    public interface OnScanReportDataSaveCallBack {
        void onScanReportDataSaveSuccess();
        void onScanReportDataSaveFailure(Exception e);
    }

    public interface OnScanReportSaveCallBack{
        void onScanReportSaveSuccess();
        void onScanReportSaveFailure(Exception e);
    }

    public interface OnScanReportRetrievedListener{
        void onScanReportRetrieved(ArrayList<ScanReport> scanReportList);
        void onNoScanReportsFound();
        void onScanReportRetrievalFailed(Exception exception);
    }

}
