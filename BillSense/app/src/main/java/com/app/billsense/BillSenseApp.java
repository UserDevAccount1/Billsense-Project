package com.app.billsense;

import android.app.Application;

import com.app.billsense.utils.PrefManager;

public class BillSenseApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PrefManager.init(this);
    }
}
