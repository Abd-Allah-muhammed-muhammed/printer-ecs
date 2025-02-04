package com.albadr.printer;

import static com.albadr.printer.util.Constants.mm100;
import static com.albadr.printer.util.Constants.mm50;
import static com.albadr.printer.util.Constants.mm80;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;


import com.albadr.printer.util.PrintBitmap;
import com.albadr.printer.util.PrintUtils;
import com.albadr.printer.util.SharedPreferencesManager;
import com.albadr.printer.util.UIUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;


import com.zj.btsdk.BluetoothService;

import net.posprinter.POSConst;
import net.posprinter.POSPrinter;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private static String[] PERMISSIONS_STORAGE = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_PRIVILEGED
    };
    SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();


    private static final String TAG_BT = "BTService";
    private AlertDialog.Builder builderSingle;
    BluetoothDevice con_dev;
    BluetoothService btService;
    ArrayAdapter<String> mPairedDevices;
    public static boolean isConnected = false;
    String filePath = null;
    Bitmap printData = null;
    private TextView imageView;
    private RadioButton radio50, radio80, radio100;
    private RadioGroup radioGroup;
    private LinearLayout li_update;
    private Button btn_update;
    private FloatingActionButton fab;

    private void checkPermissions() {
        int permission1 = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);


        int permission3 = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission3 = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES);
        }


        int permission2 = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
        }

        int permission4 = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            Log.d(TAG, "checkPermissions: ");
            permission4 = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
        }


        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    1
            );
        } else if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    1
            );
        } else if (permission3 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    1
            );
        } else if (permission4 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    1
            );
        }


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        li_update = findViewById(R.id.li_update);
        btn_update = findViewById(R.id.btn_update);

        radio50 = findViewById(R.id.radio50);
        radio80 = findViewById(R.id.radio80);
        radio100 = findViewById(R.id.radio100);
        radioGroup = findViewById(R.id.group_size);


        if (!sharedPreferencesManager.getPrintAddress().isEmpty()) {

            connectBt(sharedPreferencesManager.getPrintAddress());
            imageView.setText("Selected Printer: " + sharedPreferencesManager.getPrintName());

        }


        printSizes();
        checkPermissions();
        btService = new BluetoothService(this, mHandler);
        filePath = getIntent().getStringExtra("FILE");
        if (filePath != null) {
            Log.d(TAG, "onCreate: " + filePath);
            ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(new File(filePath));

            printData = bitmaps.get(0);


        }



        setPairedDevices();



        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        initDeviceListDialog();

        fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("TAG", "onClick: " + isConnected);
                if (isConnected) {
                    print();

                } else {

                    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (mBluetoothAdapter == null) {
                        // Device does not support Bluetooth
                    } else if (!mBluetoothAdapter.isEnabled()) {
                        // Bluetooth is not enabled :)
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                            }

                            return;
                        }
                        startActivityForResult(enableIntent, 505);
                    } else {
                        setPairedDevices();
                        initDeviceListDialog();
                        builderSingle.show();
                    }

                }

            }
        });

        firebase();

    }

    private void setPairedDevices() {

        mPairedDevices = new ArrayAdapter<String>(MainActivity.this,
                android.R.layout.simple_list_item_1, android.R.id.text1);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        if (bluetoothAdapter == null) {

            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show();

        } else {

            Set<BluetoothDevice> pairedDevices = btService.getPairedDev();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                        } else {

                            mPairedDevices.add(device.getName() + "\n" + device.getAddress());
                        }

                        return;
                    }

                    mPairedDevices.add(device.getName() + "\n" + device.getAddress());

                    Log.d(TAG, "onCreate: " + device.getName() + "\n" + device.getAddress());

                }
            } else {
                mPairedDevices.add("No printers");
            }


        }



    }

    private void connectBt(String address) {
        if (Objects.equals(address, "")) {
            UIUtils.toast(R.string.bt_select);

        } else {

            Toast.makeText(this, "جاري الاتصال...", Toast.LENGTH_SHORT).show();
            MyApp.get().connectBt(address);
        }
    }

    private void firebase() {

        FirebaseRemoteConfig mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);

        mFirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

        mFirebaseRemoteConfig.fetchAndActivate()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        long version = mFirebaseRemoteConfig.getAll().get("version").asLong();


                        int versionCode = BuildConfig.VERSION_CODE;
                        Log.d(TAG, "firebase: " + version);
                        Log.d(TAG, "firebase: " + versionCode);
                        if (versionCode != version) {
                            showUpdateDialog();
                        } else {
                            li_update.setVisibility(View.GONE);
                            fab.setVisibility(View.VISIBLE);
                        }

                    } else {
                        Toast.makeText(MainActivity.this, "Fetch failed",
                                Toast.LENGTH_SHORT).show();
                    }

                });

        mFirebaseRemoteConfig.addOnConfigUpdateListener(new ConfigUpdateListener() {
            @Override
            public void onUpdate(ConfigUpdate configUpdate) {
                mFirebaseRemoteConfig.activate().addOnCompleteListener(task -> {
                    mFirebaseRemoteConfig.activate();

                    if (configUpdate.getUpdatedKeys().contains("version")) {

                        long version = mFirebaseRemoteConfig.getAll().get("version").asLong();
                        Log.d(TAG, "firebase:onUpdate " + version);
                        int versionCode = BuildConfig.VERSION_CODE;

                        if (versionCode < version) {
                            showUpdateDialog();
                        } else {

                            li_update.setVisibility(View.GONE);
                            fab.setVisibility(View.VISIBLE);
                        }

                    }


                });

            }

            @Override
            public void onError(@NonNull FirebaseRemoteConfigException error) {
                Log.d(TAG, "onError: " + error);

            }


        });


    }

    private void showUpdateDialog() {

        li_update.setVisibility(View.VISIBLE);
        fab.setVisibility(View.GONE);
        btn_update.setOnClickListener(v -> openAppInStore());

    }


    private void openAppInStore() {
        String appPackageName = "com.albadr.printer";

        try {
            // Open the app's page on the Play Store
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException e) {
            // If Play Store app is not available, open the app's page in a browser
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    @SuppressLint("SetTextI18n")
    private void initDeviceListDialog() {
        builderSingle = new AlertDialog.Builder(MainActivity.this);
        builderSingle.setIcon(android.R.drawable.ic_btn_speak_now);
        builderSingle.setTitle("Select Printer:-");

        builderSingle.setNegativeButton(
                "cancel",
                (dialog, which) -> dialog.dismiss());


        builderSingle.setAdapter(
                mPairedDevices,
                (dialog, which) -> {
                    String info = mPairedDevices.getItem(which);
                    String address = info.substring(info.length() - 17);
                    con_dev = btService.getDevByMac(address);

                    connectBt(con_dev.getAddress());

//                  btService.connect(con_dev);
                    sharedPreferencesManager.savePrintAddress(con_dev.getAddress());
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {


                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                        } else {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 100);
                        }
                        return;
                    }
                    sharedPreferencesManager.savePrintName(con_dev.getName());
                    imageView.setText("Selected Printer: " + con_dev.getName());
                });

    }


    private void printSizes() {


        String printSize = sharedPreferencesManager.getPrintSize();

        if (printSize.equals(mm50)) {
            radio50.setChecked(true);
        }else if (printSize.equals(mm80)){
              radio80.setChecked(true);
        }else {
             radio100.setChecked(true);
        }


        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId ==  radio50.getId()) {
                sharedPreferencesManager.savePrintSize(mm50);
            }else if (checkedId ==  radio80.getId()){
                sharedPreferencesManager.savePrintSize(mm80);
            }else {
                sharedPreferencesManager.savePrintSize(mm100);

            }

        });

    }

    private void print() {

        POSPrinter printerPos = new POSPrinter(MyApp.get().curConnect);
        String str = "Welcome to  Albadr systems \n,this is print test content!\n";

        printerPos. printString(str)
                .printText(
                        "Albadr Printer!\n",
                        POSConst.ALIGNMENT_CENTER,
                        POSConst.FNT_BOLD | POSConst.FNT_UNDERLINE,
                        POSConst.TXT_1WIDTH | POSConst.TXT_2HEIGHT
                ).feedLine(5)

                .cutHalfAndFeed(1);
    }



    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d("TAG", "handleMessage: printing...1");
            switch (msg.what) {
                case BluetoothService.MESSAGE_STATE_CHANGE:
                    if (msg.arg1 == BluetoothService.STATE_CONNECTED) {

                        Log.d("TAG", "handleMessage: printing...");
                            print();
                        isConnected = true;
                            break;
                    }
                    break;
                case BluetoothService.MESSAGE_UNABLE_CONNECT:
                    Log.d(TAG_BT, "Unable to connect device");
                    break;
            }
        }

    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btService != null) {
            btService.cancelDiscovery();
        }
        btService = null;
    }



    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }



}
