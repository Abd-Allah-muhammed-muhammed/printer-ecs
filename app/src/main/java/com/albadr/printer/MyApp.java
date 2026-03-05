package com.albadr.printer;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.albadr.printer.util.Constants;
import com.albadr.printer.util.SharedPreferencesManager;
import com.albadr.printer.util.UIUtils;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.jeremyliao.liveeventbus.LiveEventBus;


public class MyApp extends Application {

    private static final String TAG = "MyApp";

    // Store the MAC address for creating connections when needed
    private String connectedMacAddress = null;
    private boolean isConnected = false;

    /**
     * Creates a new BluetoothConnection for the stored MAC address.
     * Each print job should create its own connection.
     */
    @SuppressLint("MissingPermission")
    public BluetoothConnection createBluetoothConnection() {
        if (connectedMacAddress == null || connectedMacAddress.isEmpty()) {
            return null;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return null;
            BluetoothDevice device = adapter.getRemoteDevice(connectedMacAddress);
            return new BluetoothConnection(device);
        } catch (Exception e) {
            Log.e(TAG, "Error creating BluetoothConnection: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns whether we have a stored printer address.
     */
    public boolean isPrinterConfigured() {
        return connectedMacAddress != null && !connectedMacAddress.isEmpty();
    }

    public void connectBt(String macAddress) {
        connectedMacAddress = macAddress;
        // Test the connection by trying to connect
        new Thread(() -> {
            try {
                BluetoothConnection connection = createBluetoothConnection();
                if (connection != null) {
                    connection.connect();
                    connection.disconnect();
                    isConnected = true;
                    sharedPreferencesManager.savePrintAddress(macAddress);
                    MainActivity.isConnected = true;
                    postConnectionEvent(true);
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> UIUtils.toast(getString(R.string.con_success)));
                } else {
                    isConnected = false;
                    postConnectionEvent(false);
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> UIUtils.toast(R.string.con_failed));
                }
            } catch (Exception e) {
                Log.e(TAG, "Bluetooth connection test failed: " + e.getMessage());
                isConnected = false;
                postConnectionEvent(false);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> UIUtils.toast(R.string.con_failed));
            }
        }).start();
    }

    // Helper method to safely post connection events for Android 14+ compatibility
    private void postConnectionEvent(boolean isConnected) {
        try {
            LiveEventBus.get(Constants.EVENT_CONNECT_STATUS).post(isConnected);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when posting LiveEventBus event (Android 14+ compatibility issue): " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Exception when posting LiveEventBus event: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;

        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase: " + e.getMessage());
        }

        // Initialize the SharedPreferencesManager with the application context
        sharedPreferencesManager = SharedPreferencesManager.getInstance(getApplicationContext());

        // Restore saved MAC address
        String savedAddress = sharedPreferencesManager.getPrintAddress();
        if (savedAddress != null && !savedAddress.isEmpty()) {
            connectedMacAddress = savedAddress;
        }
    }

    private static SharedPreferencesManager sharedPreferencesManager;

    public static SharedPreferencesManager getSharedPreferencesManager() {
        return sharedPreferencesManager;
    }

    private static MyApp app;


    public static MyApp get() {
        return app;
    }
}