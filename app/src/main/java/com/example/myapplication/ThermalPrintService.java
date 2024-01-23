package com.example.myapplication;

import com.squareup.picasso.Picasso;

import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
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
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;

import com.example.myapplication.util.PrintBitmap;
import com.example.myapplication.util.PrintUtils;
import com.zj.btsdk.BluetoothService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ThermalPrintService extends PrintService {

    private static final String LOG_TAG = "ThermalPrintService";

    private PrinterInfo mThermalPrinter;
    private Handler mHandler;
    private PrintAttributes.MediaSize paperSize;

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


            Log.d("TAG", "handleHandleQueuedPrintJob: file path "+file.getPath());


//            Intent printPreview = new Intent(this, MainActivity.class);
//            printPreview.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            printPreview.putExtra("FILE", file.getPath());
//            startActivity(printPreview);

//
            ArrayAdapter<String> mPairedDevices = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

            BluetoothService btService = new BluetoothService(this, mHandler2);
            Set<BluetoothDevice> pairedDevices = btService.getPairedDev();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    mPairedDevices.add(device.getName() + "\n" + device.getAddress());

                }
            }

            String info2 = mPairedDevices.getItem(0);
            String address = info2.substring(info2.length() - 17);
           BluetoothDevice con_dev = btService.getDevByMac(address);
            btService.connect(con_dev);

           ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(file);


            for (int i = 0; i < bitmaps.size(); i++) {

                byte[] sendData = null;
                PrintBitmap pg = new PrintBitmap();
                pg.initCanvas(380);

                pg.initPaint();
                pg.paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

                 Bitmap originalBitmap = bitmaps.get(i);

                 pg.drawImage(0, 0, originalBitmap);

                 sendData = pg.printDraw();

                 btService.write(sendData);

            }
            printJob.cancel();


        } catch (IOException ioe) {
            Log.d(LOG_TAG, "handleHandleQueuedPrintJob: "+ioe.getMessage());
        }

    }

    private final Handler mHandler2 = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d("TAG", "handleMessage: printing...1");
            switch (msg.what) {
                case BluetoothService.MESSAGE_STATE_CHANGE:
                    if (msg.arg1 == BluetoothService.STATE_CONNECTED) {
                    }
                    break;
                case BluetoothService.MESSAGE_UNABLE_CONNECT:
                    break;
            }
        }

    };

    private final class PrintHandler extends Handler {
         static final int MSG_HANDLE_PRINT_JOB = 3;
        public PrintHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message message) {
            Log.d(LOG_TAG, "handleMessage: "+message.toString());
            switch (message.what) {
                case MSG_HANDLE_PRINT_JOB: {
                    PrintJob printJob = (PrintJob) message.obj;
                    handleHandleQueuedPrintJob(printJob);
                } break;
            }
        }
    }
}



class ThermalPrinterDiscoverySession extends PrinterDiscoverySession {

    private PrinterInfo printerInfo;


    ThermalPrinterDiscoverySession(PrinterInfo printerInfo ) {


//        float widthInches = 2.0f;
//        float heightInches = 5.906f;
//        int widthMils = (int) (widthInches * 1090);
//        int heightMils = (int) (heightInches * 1090);

// Use the existing constructor to create the instance
        PrintAttributes.MediaSize mediaSize58 = new PrintAttributes.MediaSize("58M", "58M", 2380, 6800);
        PrinterCapabilitiesInfo capabilities =
                new PrinterCapabilitiesInfo.Builder(printerInfo.getId())
                      .addMediaSize(  mediaSize58, true)
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

    private   final String TAG = "ThermalPrinterDiscovery";
}


