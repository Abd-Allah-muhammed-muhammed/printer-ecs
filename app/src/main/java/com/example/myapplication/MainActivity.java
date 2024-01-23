package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;


import com.example.myapplication.util.PrintBitmap;
import com.example.myapplication.util.PrintUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sun.pdfview.decode.PDFDecoder;
import com.zj.btsdk.BluetoothService;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static String[] PERMISSIONS_STORAGE = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_PRIVILEGED
    };
    private static String[] PERMISSIONS_LOCATION = {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_PRIVILEGED
    };





    private static final String TAG_BT = "BTService";
    private AlertDialog.Builder builderSingle;
    BluetoothDevice con_dev;
    BluetoothService btService;
    ArrayAdapter<String> mPairedDevices;
    boolean isConnected = false;
    String filePath = null;
    Bitmap printData = null;

    private void checkPermissions(){
        int permission1 = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(this,android.Manifest.permission.BLUETOOTH_SCAN);
        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    1
            );
        } else if (permission2 != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_LOCATION,
                    1
            );
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();
        btService = new BluetoothService(this, mHandler);
        filePath = getIntent().getStringExtra("FILE");
        if (filePath != null) {
            Log.d(TAG, "onCreate: "+filePath);
            ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(new File(filePath));
            ImageView imageView = findViewById(R.id.imageView);
            imageView.setImageBitmap(bitmaps.get(0));

        }

        mPairedDevices = new ArrayAdapter<String>(MainActivity.this,
                android.R.layout.simple_list_item_1, android.R.id.text1);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        initDeviceListDialog();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("TAG", "onClick: "+isConnected);
                if (isConnected) {
                    print();
                    return;
                }
                builderSingle.show();
            }
        });


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
                    }else
                    {


                         mPairedDevices.add(device.getName() + "\n" + device.getAddress());
                     }

                    return;
                }

                 mPairedDevices.add(device.getName() + "\n" + device.getAddress());

            }
        } else {
             mPairedDevices.add("No printers");
        }

    }

    private   final String TAG = "MainActivity";

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
                    btService.connect(con_dev);
                });

    }

    private void print() {

        Log.d("print", "print: ............");
        byte[] sendData = null;
        PrintBitmap pg = new PrintBitmap();
        pg.initCanvas(384);
        pg.initPaint();
        pg.drawImage(0, 0, printData);
        sendData = pg.printDraw();
        btService.write(sendData);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
//            startActivity(new Intent(MainActivity.this, SettingsActivity.class));

            return true;
        }
        return super.onOptionsItemSelected(item);
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
