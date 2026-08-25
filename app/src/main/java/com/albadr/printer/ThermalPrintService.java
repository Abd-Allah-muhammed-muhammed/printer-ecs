package com.albadr.printer;

import static com.albadr.printer.util.Constants.PRINTER_390;
import static com.albadr.printer.util.Constants.PRINTER_500;
import static com.albadr.printer.util.Constants.mm50;
import static com.albadr.printer.util.Constants.mm80;


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
import androidx.annotation.Nullable;
import com.albadr.printer.util.Constants;
import com.albadr.printer.util.PrintUtils;
import com.albadr.printer.util.SharedPreferencesManager;
import com.albadr.printer.util.UIUtils;
import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
//import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
//import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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


    private void handleHandleQueuedPrintJob(final PrintJob printJob) {

        if (!MyApp.get().isPrinterConfigured()) {
            UIUtils.toast("من فضلك اعد المحاولة مرة اخري");

            SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();
            MyApp.get().connectBt(sharedPreferencesManager.getPrintAddress());

            // Cancel the print job if no connection
            printJob.cancel();
            Log.d(TAG, "Printer connection is null, cancelling print job");
            return;
        }

        printNow(printJob);
    }

    private static final String TAG = "ThermalPrintService";

    /** Pause after closing the printer socket, before the next job may open one. */
    private static final long SOCKET_SETTLE_MS = 400L;

    /**
     * How many times to try opening the Bluetooth socket before giving up.
     *
     * RFCOMM connects fail intermittently — the printer is still tearing down the
     * last channel, or the SDP lookup races — and surface as
     * "read failed, socket might closed or timeout". A second attempt a moment later
     * almost always succeeds, which is why restarting the job by hand used to work.
     */
    private static final int CONNECT_ATTEMPTS = 3;

    /** Base gap between connection attempts; grows with each retry. */
    private static final long CONNECT_RETRY_MS = 700L;

    /** Opens the printer, retrying a transient Bluetooth failure. */
    private EscPosPrinter connectPrinter(float printerWidthMM, int nbrCharsPerLine) {
        for (int attempt = 1; attempt <= CONNECT_ATTEMPTS; attempt++) {
            BluetoothConnection connection = MyApp.get().createBluetoothConnection();
            if (connection == null) {
                Log.e(TAG, "connectPrinter: no printer address stored");
                return null;
            }

            try {
                EscPosPrinter printer =
                        new EscPosPrinter(connection, 203, printerWidthMM, nbrCharsPerLine);
                Log.d(TAG, "connectPrinter: connected on attempt " + attempt);
                return printer;
            } catch (Exception e) {
                Log.w(TAG, "connectPrinter: attempt " + attempt + " of " + CONNECT_ATTEMPTS
                        + " failed: " + e.getMessage());

                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                    // Already down; nothing to release.
                }

                if (attempt < CONNECT_ATTEMPTS) {
                    try {
                        Thread.sleep(CONNECT_RETRY_MS * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        Log.e(TAG, "connectPrinter: giving up after " + CONNECT_ATTEMPTS + " attempts");
        return null;
    }

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

        // Declared out here so the finally block can close it down whichever way this
        // method leaves, including the early returns below.
        EscPosPrinter printer = null;

        try {
            // Check if printer is configured
            if (!MyApp.get().isPrinterConfigured()) {
                printJob.fail("Printer connection lost");
                return;
            }

            String printSize = sharedPreferencesManager.getPrintSize();
            float printerWidthMM = Constants.widthMmFor(printSize);
            int nbrCharsPerLine = Constants.charsPerLineFor(printSize);

            printer = connectPrinter(printerWidthMM, nbrCharsPerLine);
            if (printer == null) {
                printJob.fail("Unable to connect to the printer");
                return;
            }

            ArrayList<Bitmap> bitmaps = PrintUtils.pdfToBitmap(file);

            if (bitmaps == null || bitmaps.isEmpty()) {
                printJob.fail("Failed to convert PDF to bitmap");
                return;
            }

            boolean printingSuccessful = true;

            try {
                for (int i = 0; i < bitmaps.size(); i++) {
                    try {
                        Bitmap pageBitmap = bitmaps.get(i);

                        // Split bitmap into chunks of 256px height (library limit)
                        int chunkHeight = 256;
                        int bitmapHeight = pageBitmap.getHeight();
                        int bitmapWidth = pageBitmap.getWidth();

                        StringBuilder printContent = new StringBuilder();

                        for (int yOffset = 0; yOffset < bitmapHeight; yOffset += chunkHeight) {
                            int currentChunkHeight = Math.min(chunkHeight, bitmapHeight - yOffset);
                            Bitmap chunk = Bitmap.createBitmap(pageBitmap, 0, yOffset, bitmapWidth, currentChunkHeight);

                            String hexString = PrinterTextParserImg.bitmapToHexadecimalString(printer, chunk, false);
                            printContent.append("[C]<img>").append(hexString).append("</img>\n");

                            if (chunk != pageBitmap) {
                                chunk.recycle();
                            }
                        }


                        // Print with cut only on the last page. One cut, one feed:
                        // cutting twice with a 50mm feed and ten blank lines between
                        // them left a hand's length of empty roll after every receipt.
                        if (i == bitmaps.size() - 1) {
                            printer.printFormattedTextAndCut(printContent.toString(),
                                    Constants.FEED_AFTER_PRINT_MM);
                        } else {
                            printer.printFormattedText(printContent.toString());
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
            // Close the Bluetooth socket. Every job opened one and none of them were
            // ever closed, so the sockets piled up: a printer that accepts only one
            // RFCOMM connection at a time refused the third job, while a better one
            // tolerated the leak and hid the bug.
            if (printer != null) {
                try {
                    printer.disconnectPrinter();
                    Log.d(TAG, "Printer disconnected");

                    // Small printers need a moment to tear the RFCOMM channel down.
                    // Without it a job queued right behind this one can reach the
                    // printer before it is ready to accept a new connection.
                    Thread.sleep(SOCKET_SETTLE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to disconnect printer: " + e.getMessage());
                }
            }

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


            // The advertised page is the real printable area of the roll, so a source
            // that lays out for the page it is handed prints at its intended size.
            SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();

            String printSize = sharedPreferencesManager.getPrintSize();

            String label;
            if (printSize.equals(mm50)) {
                label = "58M";
            } else if (printSize.equals(mm80)) {
                label = "80M";
            } else {
                label = "104M";
            }

            PrintAttributes.MediaSize mediaSize = new PrintAttributes.MediaSize(
                    label, label,
                    Constants.mediaWidthMilsFor(printSize),
                    Constants.mediaHeightMilsFor(printSize,
                            sharedPreferencesManager.isSinglePageEnabled()));

            Log.d(TAG, "media size " + label + " -> "
                    + mediaSize.getWidthMils() + "x" + mediaSize.getHeightMils() + " mils");

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
