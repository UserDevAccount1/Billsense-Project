package com.app.billsense.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import com.app.billsense.R;

/**
 * Shared loading dialog. Hardened against the classic
 * "View not attached to window manager" crash: it never reuses a dialog across
 * activities, never shows on a finishing/destroyed activity, and swallows
 * dismiss errors.
 */
public class ProgressDialogUtil {
    private static Dialog progressDialog;

    public static void showProgressDialog(Context context) {
        // Drop any stale dialog (possibly tied to a finished activity) first.
        hideProgressDialog();

        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        try {
            Dialog d = new Dialog(context);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.setContentView(R.layout.progress_dialog);
            if (d.getWindow() != null) {
                d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            d.setCancelable(false);
            d.setCanceledOnTouchOutside(false);
            d.show();
            progressDialog = d;
        } catch (Exception e) {
            progressDialog = null;
        }
    }

    public static void hideProgressDialog() {
        Dialog d = progressDialog;
        progressDialog = null;
        if (d == null) return;
        try {
            if (d.isShowing()) d.dismiss();
        } catch (Exception ignored) {
            // Activity window already gone — nothing to dismiss.
        }
    }
}
