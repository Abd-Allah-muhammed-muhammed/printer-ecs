package com.albadr.printer;

import android.app.Application;
import android.content.Context;

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

    private IConnectListener connectListener = (code, s, s1) -> {
        switch (code) {
            case POSConnect.CONNECT_SUCCESS:
                UIUtils.toast(getString(R.string.con_success));
                sharedPreferencesManager.savePrintAddress(s);
                MainActivity.isConnected = true;
                LiveEventBus.get(Constants.EVENT_CONNECT_STATUS).post(true);
                break;
            case POSConnect.CONNECT_FAIL:
                UIUtils.toast(R.string.con_failed);
                LiveEventBus.get(Constants.EVENT_CONNECT_STATUS).post(false);
                break;
            case POSConnect.CONNECT_INTERRUPT:
                UIUtils.toast(R.string.con_has_disconnect);
                LiveEventBus.get(Constants.EVENT_CONNECT_STATUS).post(false);
                break;
//            case POSConnect.SEND_FAIL:
//                UIUtils.toast(R.string.send_failed);
//                break;

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



    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        FirebaseApp.initializeApp(this);
        // Initialize the SharedPreferencesManager with the application context
        sharedPreferencesManager =   SharedPreferencesManager.getInstance(getApplicationContext());


        POSConnect.init(this);
     }

    public static SharedPreferencesManager getSharedPreferencesManager() {
        return sharedPreferencesManager;
    }



    private static MyApp app;

    public static void initialize(MyApp instance) {
        app = instance;
    }

    public static MyApp get() {
        return app;
    }
}