package com.app.billsense.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {

    private static final String PREF_USERS = "Users";

    private static SharedPreferences usersPrefs;

    private static final String KEY_IS_LOGGED_IN = "isLoggedIn_user";

    private final Context context;
    private static PrefManager instance;

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new PrefManager(context.getApplicationContext());
        }
    }

    public static PrefManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PrefManager not initialized. Call init() in Application.onCreate()");
        }
        return instance;
    }

    private PrefManager(Context context) {
        this.context = context;
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
