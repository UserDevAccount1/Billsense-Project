package com.app.billsense.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class PrefManager {

    private static final String PREF_USERS = "Users";

    private static SharedPreferences usersPrefs;

    private static final String KEY_IS_LOGGED_IN = "isLoggedIn_user";

    public Context context;
    public static PrefManager instance = new PrefManager();

    public static PrefManager getInstance() {
        if (usersPrefs == null && instance.context == null) {
            Log.w("PrefManager", "PrefManager.getInstance() called before initialization with context. This might lead to issues.");
            // Consider throwing an IllegalStateException here if strict initialization is required.
            // throw new IllegalStateException("PrefManager not initialized. Call new PrefManager(context) first.");
        }
        return instance;
    }

    public static void setInstance(PrefManager instance) {
        PrefManager.instance = instance;
    }

    public PrefManager() {
    }

    public PrefManager(Context context) {
        if (context == null) {
            return; // Avoid further processing if context is null
        }
        this.context = context.getApplicationContext();
        usersPrefs = this.context.getSharedPreferences(PREF_USERS, Context.MODE_PRIVATE);
    }

    // User data methods
    public void saveUserData(String id, String email, String name) {
        SharedPreferences.Editor editor = usersPrefs.edit();
        editor.putString("id", id);
        editor.putString("email", email);
        editor.putString("name", name);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }

    public String getUserId() {
        return usersPrefs.getString("id", null);
    }

    public String getUserEmail() {
        return usersPrefs.getString("email", null);
    }

    public String getUserName() {
        return usersPrefs.getString("name", null);
    }

    public boolean isLoggedIn() {
        return usersPrefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    public void clearUserData() {
        SharedPreferences.Editor editor = usersPrefs.edit();
        editor.clear();
        editor.apply();
    }
}

