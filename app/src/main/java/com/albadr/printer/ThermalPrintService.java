package com.albadr.printer;

import static com.albadr.printer.util.Constants.mm50;
import static com.albadr.printer.util.Constants.mm80;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.print.PrintAttributes;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.albadr.printer.util.PrintBitmap;
import com.albadr.printer.util.PrintUtils;
import com.albadr.printer.util.SharedPreferencesManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.zj.btsdk.BluetoothService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public class ThermalPrintService extends PrintService {

    private static final String LOG_TAG = "ThermalPrintService";

    private PrinterInfo mThermalPrinter;
    private Handler mHandler;

    private final Handler mHandler1 = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d("TAG", "handleMessage: printing...0" + msg);
            switch (msg.what) {
                case BluetoothService.MESSAGE_STATE_CHANGE:
                    if (msg.arg1 == BluetoothService.STATE_CONNECTED)
                        Log.d("TAG", "handleMessage: STATE_CONNECTED...");
                    break;
                case BluetoothService.MESSAGE_UNABLE_CONNECT:
                    break;
            }
        }
    };



    @Override
    public void onCreate() {
        mThermalPrinter = new PrinterInfo.Builder(generatePrinterId("Printer 1"),
                "Albadr-Printer", PrinterInfo.STATUS_IDLE).build();
    }

    @Override
    protected void onConnected() {
        Log.i(LOG_TAG, "#onConnected()");
        mHandler = new PrintHandler(getMainLooper());
    }

    @Nullable
    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        Log.d(LOG_TAG, "onCreatePrinterDiscoverySession: ");
        return new ThermalPrinterDiscoverySession(mThermalPrinter);
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        Log.i(LOG_TAG, "#onRequestCancelPrintJob() printJobId: " + printJob.getId());
        if (mHandler.hasMessages(PrintHandler.MSG_HANDLE_PRINT_JOB)) {
            mHandler.removeMessages(PrintHandler.MSG_HANDLE_PRINT_JOB);
        }
        if (printJob.isQueued() || printJob.isStarted()) {
            printJob.cancel();
        }
    }


    @Override
    protected void onPrintJobQueued(PrintJob printJob) {


        Message message = mHandler.obtainMessage(PrintHandler.MSG_HANDLE_PRINT_JOB, printJob);
        mHandler.sendMessageDelayed(message, 0);
    }

    private void handleHandleQueuedPrintJob(final PrintJob printJob) {


        SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();

        int numberPrinting = sharedPreferencesManager.getNumberPrinting();

        if (numberPrinting == 0){

            return;
        }else {
            numberPrinting = numberPrinting - 1;
            sharedPreferencesManager.saveNumberPrinting(numberPrinting);
        }

        Log.d(LOG_TAG, "handleHandleQueuedPrintJob: ");
        if (printJob.isQueued()) {
            printJob.start();
        }


        final PrintJobInfo info = printJob.getInfo();

        final File file = new File(getFilesDir(), info.getLabel() + ".pdf");


        InputStream in = null;
        FileOutputStream out = null;

        try {
            in = new FileInputStream(printJob.getDocument().getData().getFileDescriptor());
            out = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();

            out.flush();
            out.close();


            BluetoothService btService = new BluetoothService(this, mHandler1);
            String address = sharedPreferencesManager.getPrintAddress();
            Log.d("TAG", "address: " + address);
            BluetoothDevice con_dev = btService.getDevByMac(address);
            btService.connect(con_dev);


            ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(file);


            for (int i = 0; i < bitmaps.size(); i++) {


                int mediaSize;

                if (sharedPreferencesManager.getPrintSize().equals(mm50)) {

                    mediaSize = 410;
                }else if (sharedPreferencesManager.getPrintSize().equals(mm80)){

                    mediaSize = 565;
                }else {

                    mediaSize = 735;
                }




                byte[] sendData = null;
                PrintBitmap pg = new PrintBitmap();
                pg.initCanvas(mediaSize);
                pg.initPaint();
                Bitmap originalBitmap = bitmaps.get(i);
                pg.drawImage(0, 0, originalBitmap);

                sendData = pg.printDraw();

                try {

                    btService.write(sendData);

                } catch (Exception e) {
                    Log.d("TAG", "Exception: " + e.getMessage());
                    e.printStackTrace();
                }

                Log.d("TAG", "btService: ");

            }

            Log.d("TAG", "handleHandleQueuedPrintJob: file path " + file.getPath());

           printJob.cancel();


        } catch (IOException ioe) {
            Log.d(LOG_TAG, "handleHandleQueuedPrintJob:error " + ioe.getMessage());
        }

    }



        private final class PrintHandler extends Handler {
            static final int MSG_HANDLE_PRINT_JOB = 3;

            public PrintHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message message) {
                Log.d(LOG_TAG, "handleMessage: " + message.toString());
                switch (message.what) {
                    case MSG_HANDLE_PRINT_JOB: {
                        PrintJob printJob = (PrintJob) message.obj;
                        handleHandleQueuedPrintJob(printJob);
                    }
                    break;
                }
            }
        }


        class ThermalPrinterDiscoverySession extends PrinterDiscoverySession {

            private PrinterInfo printerInfo;


            ThermalPrinterDiscoverySession(PrinterInfo printerInfo) {



                PrintAttributes.MediaSize mediaSize58 = new PrintAttributes.MediaSize("58M", "58M", 3200, 8800);

                PrintAttributes.MediaSize mediaSize80 = new PrintAttributes.MediaSize("80M", "80M", 4413, 12137);

                PrintAttributes.MediaSize mediaSize104 = new PrintAttributes.MediaSize("104M", "104M", 5737, 15779);

                PrintAttributes.MediaSize mediaSize ;

                SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();


                if (sharedPreferencesManager.getPrintSize().equals(mm50)) {

                    mediaSize = mediaSize58;
                }else if (sharedPreferencesManager.getPrintSize().equals(mm80)){

                    mediaSize = mediaSize80;
                }else {

                    mediaSize = mediaSize104;
                }

                PrinterCapabilitiesInfo capabilities =
                        new PrinterCapabilitiesInfo.Builder(printerInfo.getId())
                                .addMediaSize(mediaSize, true)
//                      .addMediaSize(  mediaSize58Large, false)
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .addResolution(new PrintAttributes.Resolution("R2", "200X200", 200, 200), true)
                                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME).build();

                this.printerInfo = new PrinterInfo.Builder(printerInfo)
                        .setCapabilities(capabilities)
                        .build();

            }


            @Override
            public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
                Log.d(TAG, "onStartPrinterDiscovery: ");
                List<PrinterInfo> printers = new ArrayList<PrinterInfo>();
                printers.add(printerInfo);
                addPrinters(printers);
            }

            @Override
            public void onStopPrinterDiscovery() {
                Log.d(TAG, "onStopPrinterDiscovery: ");
            }

            @Override
            public void onValidatePrinters(List<PrinterId> printerIds) {
                Log.d(TAG, "onValidatePrinters: ");
            }

            @Override
            public void onStartPrinterStateTracking(PrinterId printerId) {


                Log.d(TAG, "onStartPrinterStateTracking: ");
            }

            @Override
            public void onStopPrinterStateTracking(PrinterId printerId) {
                Log.d(TAG, "onStopPrinterStateTracking: ");
            }

            @Override
            public void onDestroy() {
                Log.d(TAG, "onDestroy: ");
            }

            private final String TAG = "ThermalPrinterDiscovery";
        }

    }





