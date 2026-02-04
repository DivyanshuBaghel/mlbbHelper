package com.example.mlbbop.managers;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "mlbb_op_prefs";
    private static final String KEY_API_KEY_1 = "gemini_api_key_1";
    private static final String KEY_API_KEY_2 = "gemini_api_key_2";
    private static final String KEY_API_KEY_3 = "gemini_api_key_3";
    private static final String KEY_ACTIVE_KEY_INDEX = "active_key_index";
    private static final String KEY_MODEL_NAME = "gemini_model_name";

    public static void saveApiKey(Context context, int index, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = getKeyForIndex(index);
        prefs.edit().putString(key, apiKey).apply();
    }

    public static String getApiKey(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = getKeyForIndex(index);
        return prefs.getString(key, "");
    }

    public static void saveActiveKeyIndex(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_ACTIVE_KEY_INDEX, index).apply();
    }

    public static int getActiveKeyIndex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_ACTIVE_KEY_INDEX, 1); // Default to index 1
    }

    public static String getActiveApiKey(Context context) {
        int index = getActiveKeyIndex(context);
        return getApiKey(context, index);
    }

    public static void saveModelName(Context context, String modelName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL_NAME, modelName).apply();
    }

    public static String getModelName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MODEL_NAME, "gemini-2.5-flash"); // Default model
    }

    private static String getKeyForIndex(int index) {
        switch (index) {
            case 2:
                return KEY_API_KEY_2;
            case 3:
                return KEY_API_KEY_3;
            default:
                return KEY_API_KEY_1;
        }
    }
}
