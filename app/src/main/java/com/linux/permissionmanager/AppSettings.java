package com.linux.permissionmanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Set;

public class AppSettings {
    public static final String KEY_IS_HOTLOAD_MODE = "is_hotload_mode";
    public static final String HOTLOAD_SHELL_PATH = "/sdcard/1.h";
    public static final String KEY_APPEARANCE_PALETTE = "appearance_palette";
    public static final String KEY_APPEARANCE_BACKGROUND_URI = "appearance_background_uri";
    public static final String KEY_APPEARANCE_BACKGROUND_ALPHA = "appearance_background_alpha";
    public static final String KEY_APPEARANCE_CHROME_TRANSPARENCY = "appearance_chrome_transparency";
    public static final String KEY_APPEARANCE_CONTROL_TRANSPARENCY = "appearance_control_transparency";
    public static final String KEY_APPEARANCE_GLASS_NAVIGATION_ENABLED = "appearance_glass_navigation_enabled";
    public static final String KEY_APPEARANCE_GLASS_NAVIGATION_TRANSPARENCY = "appearance_glass_navigation_transparency";
    public static final String KEY_MANAGER_UPDATE_CHECK_ENABLED = "manager_update_check_enabled";
    private static SharedPreferences preferences;

    public static void init(Context context) {
        if (preferences != null) return;
        preferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
    }

    public static void setBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        try {
            return preferences.getBoolean(key, defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public static void setString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public static String getString(String key, String defaultValue) {
        try {
            return preferences.getString(key, defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public static void setInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return preferences.getInt(key, defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public static void setStringSet(String key, Set<String> value) {
        preferences.edit().putStringSet(key, value).apply();
    }

    public static Set<String> getStringSet(String key, Set<String> defaultValue) {
        try {
            return preferences.getStringSet(key, defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultValue != null ? defaultValue : Collections.emptySet();
    }
}
