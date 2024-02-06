package com.albadr.printer;

import android.app.Application;
import android.content.Context;

import com.albadr.printer.util.SharedPreferencesManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;


public class MyApp extends Application {

    private static SharedPreferencesManager sharedPreferencesManager;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);
        // Initialize the SharedPreferencesManager with the application context
        sharedPreferencesManager =   SharedPreferencesManager.getInstance(getApplicationContext());
    }

    public static SharedPreferencesManager getSharedPreferencesManager() {
        return sharedPreferencesManager;
    }
}