package com.albadr.printer;

import static com.albadr.printer.util.Constants.mm100;
import static com.albadr.printer.util.Constants.mm50;
import static com.albadr.printer.util.Constants.mm80;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.albadr.printer.util.PrintUtils;
import com.albadr.printer.util.SharedPreferencesManager;
import com.albadr.printer.util.UIUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;


import com.zj.btsdk.BluetoothService;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;

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
    BluetoothDevice con_dev;
    BluetoothService btService;
    private ArrayList<BluetoothDevice> pairedDeviceList = new ArrayList<>();
    public static boolean isConnected = false;
    String filePath = null;
    Bitmap printData = null;
    private TextView imageView;
    private TextView toggle50, toggle80, toggle100;
    private View statusDot;
    private LinearLayout li_update;
    private Button btn_update;
    private View fab;

    private BottomSheetDialog printerDialog;

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
        statusDot = findViewById(R.id.status_dot);

        toggle50 = findViewById(R.id.toggle_50);
        toggle80 = findViewById(R.id.toggle_80);
        toggle100 = findViewById(R.id.toggle_100);


        if (!sharedPreferencesManager.getPrintAddress().isEmpty()) {
            connectBt(sharedPreferencesManager.getPrintAddress());
            imageView.setText(sharedPreferencesManager.getPrintName());
            updateStatusDot(true);
        }

        // Printer card click -> show bottom sheet
        findViewById(R.id.card_printer).setOnClickListener(v -> {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                return;
            } else if (!mBluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                    }
                    return;
                }
                startActivityForResult(enableIntent, 505);
            } else {
                loadPairedDevices();
                showPrinterBottomSheet();
            }
        });

        findViewById(R.id.tv_privacy_policy).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PrivacyPolicyActivity.class)));

        printSizes();
        checkPermissions();
        btService = new BluetoothService(this, mHandler);
        filePath = getIntent().getStringExtra("FILE");
        if (filePath != null) {
            Log.d(TAG, "onCreate: " + filePath);
            ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(new File(filePath));

            printData = bitmaps.get(0);
        }

        // Load paired devices after btService is initialized
        loadPairedDevices();

        fab = findViewById(R.id.fab);

        fab.setOnClickListener(view -> {
            Log.d("TAG", "onClick:1213123 " + isConnected);
            if (isConnected || MyApp.get().isPrinterConfigured()) {
                print();
            } else {
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter == null) {
                    // Device does not support Bluetooth
                } else if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                        }
                        return;
                    }
                    startActivityForResult(enableIntent, 505);
                } else {
                    loadPairedDevices();
                    showPrinterBottomSheet();
                }
            }
        });

//        firebase();

    }

    private static final int REQUEST_CODE_BLUETOOTH_CONNECT = 100;

    private void loadPairedDevices() {
        pairedDeviceList.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH_CONNECT);
            return;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = btService.getPairedDev();
        if (pairedDevices != null) {
            pairedDeviceList.addAll(pairedDevices);
        }
    }

    @SuppressLint("SetTextI18n")
    private void showPrinterBottomSheet() {
        printerDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_printer_list, null);
        printerDialog.setContentView(sheetView);

        RecyclerView rv = sheetView.findViewById(R.id.rv_printers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new PrinterAdapter());

        sheetView.findViewById(R.id.btn_disconnect).setOnClickListener(v -> {
            if (btService != null) {
                btService.cancelDiscovery();
            }
            isConnected = false;
            imageView.setText("لم يتم اختيار طابعة");
            updateStatusDot(false);
            sharedPreferencesManager.savePrintAddress("");
            sharedPreferencesManager.savePrintName("");
            printerDialog.dismiss();
        });

        sheetView.findViewById(R.id.btn_cancel).setOnClickListener(v -> printerDialog.dismiss());

        printerDialog.show();
    }

    private void updateStatusDot(boolean connected) {
        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
        dot.setColor(ContextCompat.getColor(this,
                connected ? R.color.status_connected : R.color.status_disconnected));
    }

    // RecyclerView Adapter for Printer List
    private class PrinterAdapter extends RecyclerView.Adapter<PrinterAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_printer, parent, false);
            return new VH(v);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BluetoothDevice device = pairedDeviceList.get(position);
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            String name = device.getName();
            String address = device.getAddress();
            holder.tvName.setText(name != null ? name : "Unknown");
            holder.tvAddress.setText(address);
            holder.itemView.setOnClickListener(v -> {
                con_dev = device;
                connectBt(con_dev.getAddress());
                sharedPreferencesManager.savePrintAddress(con_dev.getAddress());
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH}, 100);
                    }
                    return;
                }
                sharedPreferencesManager.savePrintName(con_dev.getName());
                imageView.setText(con_dev.getName());
                updateStatusDot(true);
                if (printerDialog != null) printerDialog.dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return pairedDeviceList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvAddress;
            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_printer_name);
                tvAddress = itemView.findViewById(R.id.tv_printer_address);
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
//                        if (versionCode != version) {
//                            showUpdateDialog();
//                        } else {
                            li_update.setVisibility(View.GONE);
                            fab.setVisibility(View.VISIBLE);
//                        }

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

//                        if (versionCode < version) {
//                            showUpdateDialog();
//                        } else {

                            li_update.setVisibility(View.GONE);
                            fab.setVisibility(View.VISIBLE);
//                        }

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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }


    private void printSizes() {

        String printSize = sharedPreferencesManager.getPrintSize();

        if (printSize.equals(mm50)) {
            selectToggle(toggle50);
        } else if (printSize.equals(mm80)) {
            selectToggle(toggle80);
        } else {
            selectToggle(toggle100);
        }

        toggle50.setOnClickListener(v -> {
            selectToggle(toggle50);
            sharedPreferencesManager.savePrintSize(mm50);
        });
        toggle80.setOnClickListener(v -> {
            selectToggle(toggle80);
            sharedPreferencesManager.savePrintSize(mm80);
        });
        toggle100.setOnClickListener(v -> {
            selectToggle(toggle100);
            sharedPreferencesManager.savePrintSize(mm100);
        });
    }

    private void selectToggle(TextView selected) {
        // Reset all
        toggle50.setBackgroundResource(R.drawable.bg_toggle_unselected);
        toggle80.setBackgroundResource(R.drawable.bg_toggle_unselected);
        toggle100.setBackgroundResource(R.drawable.bg_toggle_unselected);
        toggle50.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        toggle80.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        toggle100.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        // Select
        selected.setBackgroundResource(R.drawable.bg_toggle_selected);
        selected.setTextColor(ContextCompat.getColor(this, R.color.orange_primary));
    }

    private void print() {

        try {
            BluetoothConnection connection = MyApp.get().createBluetoothConnection();
            if (connection == null) {
                UIUtils.toast("Error: No printer connection");
                return;
            }

            float printerWidthMM;
            int nbrCharsPerLine;
            if (sharedPreferencesManager.getPrintSize().equals(mm50)) {
                printerWidthMM = 48f;
                nbrCharsPerLine = 32;
            } else if (sharedPreferencesManager.getPrintSize().equals(mm80)) {
                printerWidthMM = 72f;
                nbrCharsPerLine = 42;
            } else {
                printerWidthMM = 96f;
                nbrCharsPerLine = 56;
            }

            EscPosPrinter printer = new EscPosPrinter(connection, 203, printerWidthMM, nbrCharsPerLine);

            printer.printFormattedTextAndCut(
                    "[L]Welcome to  Albadr systems \n" +
                    "[L],this is print test content!\n" +
                    "[C]<font size='big'><b>Albadr Printer!</b></font>\n" +
                    "\n\n\n\n\n\n\n\n\n\n",
                    50f
            );

            Log.d(TAG, "print: test print completed");
        }catch (Exception e) {
            Log.e(TAG, "print: ", e);
            UIUtils.toast("Error: " + e.getMessage());

            Log.d(TAG, "print: 4");
            return;
        }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, try setting up the paired devices again.
                loadPairedDevices();
            } else {
                // Permission denied, show an error message or disable Bluetooth-related features.
                Toast.makeText(this, "Bluetooth permission is required to list paired devices", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
