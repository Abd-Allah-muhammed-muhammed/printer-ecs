package com.albadr.printer.util;


import static com.albadr.printer.util.Constants.mm50;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesManager {

    private static final String SHARED_PREFS_NAME = "MyAppPreferences";
    private static final String KEY_PRINT_SIZE = "KEY_PRINT_SIZE";
    private static final String KEY_PRINT_NAME = "KEY_PRINT_NAME";
    private static final String NUMBER_PRINTING = "NUMBER_PRINTING";
    private static final String KEY_PRINT_ADDRESS = "KEY_PRINT_Address";
    private static final String KEY_WIDE_PAGE = "KEY_WIDE_PAGE";

    private static SharedPreferencesManager instance;

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public static synchronized SharedPreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesManager(context.getApplicationContext());
        }
        return instance;
    }
    // Example methods to store and retrieve data

    public void savePrintSize(String size) {
        editor.putString(KEY_PRINT_SIZE, size);
        editor.apply();
    }

    public String getPrintName() {
        return sharedPreferences.getString(KEY_PRINT_NAME, "");
    }

    public void savePrintName(String size) {
        editor.putString(KEY_PRINT_NAME, size);
        editor.apply();
    }

    public String getPrintAddress() {
        return sharedPreferences.getString(KEY_PRINT_ADDRESS, "");
    }

    public void savePrintAddress(String address) {
        editor.putString(KEY_PRINT_ADDRESS, address);
        editor.apply();
    }

    public String getPrintSize() {
        return sharedPreferences.getString(KEY_PRINT_SIZE, mm50);
    }

    /**
     * Compatibility mode. Off by default: the print service advertises the roll's
     * real printable width, which is what a source that lays out for the page it is
     * given expects. Turn it on for a source whose layout will not reflow narrow and
     * comes out clipped — it is then given a wider page and scaled down to fit.
     */
    public boolean isWidePageEnabled() {
        return sharedPreferences.getBoolean(KEY_WIDE_PAGE, false);
    }

    public void saveWidePageEnabled(boolean enabled) {
        editor.putBoolean(KEY_WIDE_PAGE, enabled);
        editor.apply();
    }


    public int getNumberPrinting() {
        return sharedPreferences.getInt(NUMBER_PRINTING, 500);
    }


    public void saveNumberPrinting(int number) {
        editor.putInt(NUMBER_PRINTING, number);
        editor.apply();
    }


    // Add more methods as needed for your specific use case

    // Example method to clear all preferences
    public void clearPreferences() {
        editor.clear();
        editor.apply();
    }
}