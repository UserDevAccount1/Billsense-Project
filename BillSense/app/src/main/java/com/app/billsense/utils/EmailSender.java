package com.app.billsense.utils;

import android.content.Context;
import android.util.Log;

import com.creativityapps.gmailbackgroundlibrary.BackgroundMail;

public class EmailSender {

    public static String email = "userdeveloper554@gmail.com", password = "mgca yrjd bplf cqiy";

    public interface EmailSendListener {
        void onEmailSentSuccess();

        void onEmailSentFailure(String errorMessage);
    }

    public static void sendEmail(Context context, String toEmail, String subject, String body,
                                 EmailSendListener listener) {

        BackgroundMail.newBuilder(context)
                .withUsername(EmailSender.email)
                .withPassword(EmailSender.password)
                .withMailTo(toEmail)
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject(subject)
                .withBody(body)
                .withOnSuccessCallback(new BackgroundMail.OnSuccessCallback() {
                    @Override
                    public void onSuccess() {
                        //do some magic
                        Log.d("==mail", "success");
                        listener.onEmailSentSuccess();
                    }
                })
                .withOnFailCallback(new BackgroundMail.OnFailCallback() {
                    @Override
                    public void onFail() {
                        //do some magic
                        Log.d("==mail", "failed");
                        listener.onEmailSentFailure("Error: failed to send email. " +
                                "Please try again later.");
                    }
                })
                .send();
    }
}
