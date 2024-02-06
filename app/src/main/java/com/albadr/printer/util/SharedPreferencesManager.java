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
        return sharedPreferences.getString(KEY_PRINT_NAME, "لا يوجد");
    }

    public void savePrintName(String size) {
        editor.putString(KEY_PRINT_NAME, size);
        editor.apply();
    }

    public String getPrintAddress() {
        return sharedPreferences.getString(KEY_PRINT_ADDRESS, "لا يوجد");
    }

    public void savePrintAddress(String address) {
        editor.putString(KEY_PRINT_ADDRESS, address);
        editor.apply();
    }

    public String getPrintSize() {
        return sharedPreferences.getString(KEY_PRINT_SIZE, mm50);
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