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

import com.albadr.printer.util.Constants;
import com.albadr.printer.util.InternalPrinter;
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

    private static final String[] PERMISSIONS_BLUETOOTH = {
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
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
    private androidx.appcompat.widget.SwitchCompat switchSinglePage;
    private View statusDot;
    private LinearLayout li_update;
    private Button btn_update;
    private View fab;

    private BottomSheetDialog printerDialog;

    private void checkPermissions() {
        // Runtime Bluetooth permissions only exist on Android 12 (API 31) and above.
        // On older devices BLUETOOTH / BLUETOOTH_ADMIN are install-time permissions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        ArrayList<String> missing = new ArrayList<>();
        for (String permission : PERMISSIONS_BLUETOOTH) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (!missing.isEmpty()) {
            Log.d(TAG, "checkPermissions: requesting " + missing);
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 1);
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
        switchSinglePage = findViewById(R.id.switch_single_page);


        if (!sharedPreferencesManager.getPrintAddress().isEmpty()) {
            connectBt(sharedPreferencesManager.getPrintAddress());
            imageView.setText(sharedPreferencesManager.getPrintName());
            updateStatusDot(true);
        }

        // Printer card click -> show bottom sheet
        findViewById(R.id.card_printer).setOnClickListener(v -> openPrinterPicker());

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
                openPrinterPicker();
            }
        });

//        firebase();

    }

    private static final int REQUEST_CODE_BLUETOOTH_CONNECT = 100;
    private static final int REQUEST_CODE_ENABLE_BT = 505;

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

        addInternalPrinter();
    }

    /**
     * Puts the printer built into the handheld at the top of the list.
     *
     * It is wired into the device rather than paired with it, so getPairedDev() cannot
     * see it and the list came up empty on exactly the machines that always have a
     * printer in them.
     */
    private void addInternalPrinter() {
        BluetoothDevice internal = InternalPrinter.find();
        if (internal == null) {
            return;
        }

        for (int i = 0; i < pairedDeviceList.size(); i++) {
            if (InternalPrinter.isInternal(pairedDeviceList.get(i).getAddress())) {
                // Some builds do bond it. Keep that instance, just move it to the top.
                pairedDeviceList.add(0, pairedDeviceList.remove(i));
                return;
            }
        }

        pairedDeviceList.add(0, internal);
        Log.d(TAG, "addInternalPrinter: built-in printer added at " + InternalPrinter.MAC);
    }

    /** Opens the printer picker, turning Bluetooth on first if it is off. */
    private void openPrinterPicker() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            // This used to return in silence, which left the card looking dead.
            UIUtils.toast("البلوتوث غير مدعوم على هذا الجهاز");
            return;
        }

        if (!adapter.isEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH_CONNECT);
                return;
            }
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BT);
            return;
        }

        loadPairedDevices();
        showPrinterBottomSheet();
    }

    @SuppressLint("SetTextI18n")
    private void showPrinterBottomSheet() {
        printerDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_printer_list, null);
        printerDialog.setContentView(sheetView);

        RecyclerView rv = sheetView.findViewById(R.id.rv_printers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new PrinterAdapter());

        // An empty sheet says nothing about why it is empty; this does.
        boolean empty = pairedDeviceList.isEmpty();
        rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        sheetView.findViewById(R.id.tv_empty).setVisibility(empty ? View.VISIBLE : View.GONE);

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
            String address = device.getAddress();
            String name = displayName(device);
            holder.tvName.setText(name);
            // The built-in printer has an address, but it means nothing to whoever is
            // holding the device; say where it is instead.
            holder.tvAddress.setText(InternalPrinter.isInternal(address) ? "مدمجة بالجهاز" : address);
            holder.itemView.setOnClickListener(v -> {
                con_dev = device;
                connectBt(address);
                sharedPreferencesManager.savePrintAddress(address);
                sharedPreferencesManager.savePrintName(name);
                imageView.setText(name);
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

    /**
     * A name to show for a device. An unbonded one reports none of its own, and the
     * built-in printer is never bonded, so it would otherwise read as "Unknown".
     */
    @SuppressLint("MissingPermission")
    private String displayName(BluetoothDevice device) {
        if (InternalPrinter.isInternal(device.getAddress())) {
            return InternalPrinter.LABEL;
        }
        String name = device.getName();
        return (name == null || name.isEmpty()) ? "Unknown" : name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Nothing handled this before, so turning Bluetooth on at the prompt dropped the
        // user back on the main screen with no picker and no explanation.
        if (requestCode == REQUEST_CODE_ENABLE_BT && resultCode == RESULT_OK) {
            openPrinterPicker();
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

        switchSinglePage.setChecked(sharedPreferencesManager.isSinglePageEnabled());
        switchSinglePage.setOnCheckedChangeListener((v, checked) -> {
            sharedPreferencesManager.saveSinglePageEnabled(checked);
            // The print dialog reads the page size when it opens, so a dialog that is
            // already on screen keeps the old one.
            UIUtils.toast("اقفل شاشة الطباعة وافتحها تاني عشان الإعداد يشتغل");
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

            String printSize = sharedPreferencesManager.getPrintSize();
            float printerWidthMM = Constants.widthMmFor(printSize);
            int nbrCharsPerLine = Constants.charsPerLineFor(printSize);

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
