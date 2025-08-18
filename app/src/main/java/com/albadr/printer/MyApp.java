package com.albadr.printer;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.albadr.printer.util.Constants;
import com.albadr.printer.util.SharedPreferencesManager;
import com.albadr.printer.util.UIUtils;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.jeremyliao.liveeventbus.LiveEventBus;

import net.posprinter.IConnectListener;
import net.posprinter.IDeviceConnection;
import net.posprinter.POSConnect;


public class MyApp extends Application {

    private static final String TAG = "MyApp";
    private IConnectListener connectListener = (code, s, s1) -> {

        Log.d(TAG, "codeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"+code);
        switch (code) {

            case POSConnect.CONNECT_SUCCESS:
                try {
                    UIUtils.toast(getString(R.string.con_success));
                    sharedPreferencesManager.savePrintAddress(s);
                    MainActivity.isConnected = true;
                    // Use post with try-catch for Android 14+ compatibility
                    postConnectionEvent(true);
                }catch (Exception e){
                    UIUtils.toast(""+e.getMessage());
                    Log.e(TAG, "Error posting connection success event: " + e.getMessage());
                }
                break;

            case POSConnect.CONNECT_FAIL:
                try {
                    UIUtils.toast(R.string.con_failed);
                    // Use post with try-catch for Android 14+ compatibility
                    postConnectionEvent(false);
                }catch (Exception e){
                    UIUtils.toast(""+e.getMessage());
                    Log.e(TAG, "Error posting connection fail event: " + e.getMessage());
                }
                break;

            case POSConnect.CONNECT_INTERRUPT:
                try {
                    UIUtils.toast(R.string.con_has_disconnect);
                    // Use post with try-catch for Android 14+ compatibility
                    postConnectionEvent(false);
                }catch (Exception e){
                    UIUtils.toast(""+e.getMessage());
                    Log.e(TAG, "Error posting connection interrupt event: " + e.getMessage());
                }
                break;

            default:
                UIUtils.toast(""+code);
                break;
            case POSConnect.SEND_FAIL:
                UIUtils.toast(R.string.send_failed);
                break;
        }
    };

    private static SharedPreferencesManager sharedPreferencesManager;
    public IDeviceConnection curConnect ;

    public void connectBt(String macAddress) {
        if (curConnect != null) {
            curConnect.close();
        }
        curConnect = POSConnect.createDevice(POSConnect.DEVICE_TYPE_BLUETOOTH);
        curConnect.connect(macAddress, connectListener);
    }

    // Helper method to safely post connection events for Android 14+ compatibility
    private void postConnectionEvent(boolean isConnected) {
        try {
            LiveEventBus.get(Constants.EVENT_CONNECT_STATUS).post(isConnected);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when posting LiveEventBus event (Android 14+ compatibility issue): " + e.getMessage());
            // For Android 14+, you might want to use alternative event mechanism here
            // or handle the connection status differently
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

        try {
            POSConnect.init(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize POSConnect: " + e.getMessage());
        }
    }

    public static SharedPreferencesManager getSharedPreferencesManager() {
        return sharedPreferencesManager;
    }

    private static MyApp app;


    public static MyApp get() {
        return app;
    }
}