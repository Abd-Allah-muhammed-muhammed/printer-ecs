package com.albadr.printer;

import static com.albadr.printer.util.Constants.PRINTER_390;
import static com.albadr.printer.util.Constants.PRINTER_500;
import static com.albadr.printer.util.Constants.mm50;
import static com.albadr.printer.util.Constants.mm80;


import android.content.Context;
import android.graphics.Bitmap;

import android.net.ConnectivityManager;
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
import androidx.annotation.Nullable;
import com.albadr.printer.util.PrintUtils;
import com.albadr.printer.util.SharedPreferencesManager;
import com.albadr.printer.util.UIUtils;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.posprinter.POSConst;
import net.posprinter.POSPrinter;
public class ThermalPrintService extends PrintService {

    private static final String LOG_TAG = "ThermalPrintService";

    private PrinterInfo mThermalPrinter;
    private Handler mHandler;


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

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    private void handleHandleQueuedPrintJob(final PrintJob printJob) {

        if (MyApp.get().curConnect == null) {
            UIUtils.toast("من فضلك اعد المحاولة مرة اخري");

            SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();
            MyApp.get().connectBt(sharedPreferencesManager.getPrintAddress());

            // Cancel the print job if no connection
            printJob.cancel();
            Log.d(TAG, "Printer connection is null, cancelling print job");
            return;
        }

//        if (!isNetworkConnected()) {
//            // If no network, proceed with printing anyway
//            Log.d(TAG, "No network connection, proceeding with printing");
//            printNow(printJob);
//            return;
//        }

//        FirebaseRemoteConfig mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
//        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
//                .setMinimumFetchIntervalInSeconds(3600)
//                .build();

//        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);
//        mFirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

        // Add timeout for Firebase Remote Config
//        Handler timeoutHandler = new Handler(Looper.getMainLooper());
//        Runnable timeoutRunnable = () -> {
//            Log.w(TAG, "Firebase Remote Config timeout, proceeding with printing");
        printNow(printJob);
//        };

//        timeoutHandler.postDelayed(timeoutRunnable, 5000); // 5 second timeout

//        mFirebaseRemoteConfig.fetchAndActivate()
//                .addOnCompleteListener( task -> {
//                    timeoutHandler.removeCallbacks(timeoutRunnable); // Cancel timeout
//
//                    if (task.isSuccessful()) {
//                        try {
//                            long version = mFirebaseRemoteConfig.getAll().get("version").asLong();
//                            int versionCode = BuildConfig.VERSION_CODE;
//
//                            Log.d(TAG, "handleHandleQueuedPrintJob: "+versionCode);
//                            Log.d(TAG, "handleHandleQueuedPrintJob: "+version);
//                            if (versionCode != version) {
//                                printJob.cancel();
//                            }else {
//                                printNow(printJob);
//                            }
//                        } catch (Exception e) {
//                            Log.e(TAG, "Error parsing version from Remote Config: " + e.getMessage());
//                            printNow(printJob); // Proceed with printing on error
//                        }
//                    } else {
//                        // Handle Firebase Remote Config failure - proceed with printing
//                        Log.e(TAG, "Firebase Remote Config fetch failed, proceeding with printing");
//                        printNow(printJob);
//                    }
//                });
    }

    private static final String TAG = "ThermalPrintService";

    private void printNow(PrintJob printJob) {
        if (printJob.isQueued()) {
            printJob.start();
        }

        Log.d(TAG, "handleHandleQueuedPrintJob: print job started");
        SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();

        final PrintJobInfo info = printJob.getInfo();

        // Sanitize the filename to remove invalid characters for Android 14+
        String sanitizedLabel = info.getLabel()
                .replaceAll("[/\\\\:*?\"<>|]", "_")  // Replace invalid filename characters
                .replaceAll("\\s+", "_")             // Replace spaces with underscores
                .trim();

        // Ensure we have a valid filename
        if (sanitizedLabel.isEmpty()) {
            sanitizedLabel = "print_job_" + System.currentTimeMillis();
        }

        // Create a more robust file path that works with Android 14+
        final File file = new File(getFilesDir(), sanitizedLabel + ".pdf");

        Log.d(TAG, "Original label: " + info.getLabel());
        Log.d(TAG, "Sanitized filename: " + sanitizedLabel + ".pdf");
        Log.d(TAG, "Full file path: " + file.getAbsolutePath());

        // Ensure the files directory exists
        if (!getFilesDir().exists()) {
            boolean created = getFilesDir().mkdirs();
            Log.d(TAG, "Files directory created: " + created);
        }

        InputStream in = null;
        FileOutputStream out = null;

        try {
            // Use ParcelFileDescriptor for better compatibility with Android 14+
            android.os.ParcelFileDescriptor pfd = printJob.getDocument().getData();
            if (pfd == null) {
                printJob.fail("Unable to access document data");
                return;
            }

            in = new FileInputStream(pfd.getFileDescriptor());
            out = new FileOutputStream(file);

            byte[] buffer = new byte[8192]; // Increased buffer size for better performance
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            // Ensure all data is written
            out.flush();
            out.getFD().sync(); // Force sync to storage

        } catch (IOException ioe) {
            Log.e(LOG_TAG, "handleHandleQueuedPrintJob:error " + file.getAbsolutePath() + ": " + ioe.getMessage());
            printJob.fail("IO Error: " + ioe.getMessage());
            return;
        } finally {
            // Properly close streams
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: " + e.getMessage());
            }
        }

        // Verify file was created successfully
        if (!file.exists() || file.length() == 0) {
            Log.e(TAG, "Failed to create PDF file or file is empty: " + file.getAbsolutePath());
            printJob.fail("Failed to create PDF file");
            return;
        }

        Log.d(TAG, "PDF file created successfully: " + file.getAbsolutePath() + ", size: " + file.length());

        try {
            // Check if connection is still valid
            if (MyApp.get().curConnect == null) {
                printJob.fail("Printer connection lost");
                return;
            }

            POSPrinter printerPos = new POSPrinter(MyApp.get().curConnect);

            ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(file);

            if (bitmaps == null || bitmaps.isEmpty()) {
                printJob.fail("Failed to convert PDF to bitmap");
                return;
            }

            boolean printingSuccessful = true;

            try {
                // Initialize printer once before printing all pages
                printerPos.initializePrinter();
                // Add a delay after initialization
                Thread.sleep(200);

                // Wake up printer in case it's in sleep mode
                printerPos.feedLine();
                Thread.sleep(100);

                for (int i = 0; i < bitmaps.size(); i++) {

                    int mWidth;
                    if (sharedPreferencesManager.getPrintSize().equals(mm50)) {
                        mWidth =  PRINTER_390;
                    }else {
                        mWidth=  PRINTER_500;
                    }

                    try {
                        // Print bitmap without reinitializing printer for each page
                        printerPos.printBitmap(bitmaps.get(i), POSConst.ALIGNMENT_CENTER, mWidth)
                                .feedLine();

                        // Add a small delay to ensure data is processed
                        Thread.sleep(100);

                        // Only cut after the last page
                        if (i == bitmaps.size() - 1) {
                            printerPos.cutHalfAndFeed(1);
                            // Final delay to ensure cut command is processed
                            Thread.sleep(200);

                            // Force print by sending additional feed lines to trigger printing
                            printerPos.feedLine(3);
                            Thread.sleep(100);
                        }

                        Log.d(TAG, "Successfully printed page " + (i + 1) + " of " + bitmaps.size());
                    } catch (Exception e) {
                        Log.e(TAG, "printPicCode error on page " + (i + 1) + ": " + e.getMessage());
                        printingSuccessful = false;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Printer initialization failed: " + e.getMessage());
                printingSuccessful = false;
            }

            Log.d("TAG", "handleHandleQueuedPrintJob: file path " + file.getPath());

            // Mark the print job as complete or failed
            if (printingSuccessful) {
                printJob.complete();
                Log.d(TAG, "Print job completed successfully");
            } else {
                printJob.fail("Printing failed");
                Log.d(TAG, "Print job failed");
            }

            Log.d(TAG, "printNow: ....");

        } catch (Exception e) {
            Log.e(LOG_TAG, "Printing error: " + e.getMessage());
            printJob.fail("Printing Error: " + e.getMessage());
        } finally {
            // Clean up the temporary file
            try {
                if (file.exists()) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "Temporary PDF file deleted: " + deleted);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete temporary file: " + e.getMessage());
            }
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

            List<PrinterInfo> printers = new ArrayList<PrinterInfo>();
            printers.add(printerInfo);
            addPrinters(printers);
            Log.d(TAG, "onStartPrinterDiscovery: "+printerInfo.getId().toString());
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


            Log.d(TAG, "onStartPrinterStateTrackinggggggggg: "+printerId.toString());
        }

        @Override
        public void onStopPrinterStateTracking(PrinterId printerId) {
            Log.d(TAG, "onStopPrinterStateTracking: ببببب"+printerId.toString());
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy: ");
        }

        private final String TAG = "ThermalPrinterDiscovery";
    }

}
