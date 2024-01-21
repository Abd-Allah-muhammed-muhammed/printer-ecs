package com.example.myapplication.util;


  import static com.example.myapplication.util.PrinterCommands.SELECT_BIT_IMAGE_MODE;

  import android.content.res.Resources;
  import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFPage;
import com.sun.pdfview.PDFParseException;
import com.sun.pdfview.decrypt.PDFAuthenticationFailureException;
import com.zj.btsdk.BluetoothService;

import net.sf.andpdf.nio.ByteBuffer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class PrintUtils {

    private Bitmap bArr;
    private static final String TAG = "PrintUtils";

    public PrintUtils(String filePath, OnPageLoadListener listener) {
        Log.d(TAG, "PrintUtils: " + filePath);

        // Create an instance of PdfFind
        PdfFind obj = new PdfFind(listener);

        // Execute the AsyncTask to load the PDF page in the background
        obj.execute(filePath);
    }

    public Bitmap getBitmapImages() {
        return bArr;
    }

    public interface OnPageLoadListener {
        void onPageLoad(Bitmap bitmap);
    }

    private static class PdfFind extends AsyncTask<String, Void, Bitmap> {
        private PDFFile mPdfFile;
        private PDFPage mPdfPage;
        private OnPageLoadListener mListener;

        public PdfFind(OnPageLoadListener listener) {
            mListener = listener;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            String filePath = params[0];
            try {
                System.gc();
                openFile(filePath);
                return showPage(1, 1);
            } catch (Exception e) {
                Log.e("Error", e.getMessage(), e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (mListener != null) {
                mListener.onPageLoad(result);
            }
        }

        private void openFile(String filename) {
            try {
                File file = new File(filename);
                if (file.length() == 0) {
                    Log.i("No File", "File length is 0");
                } else {
                    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                        FileChannel channel = raf.getChannel();
                        ByteBuffer bb = ByteBuffer.NEW(channel.map(
                                FileChannel.MapMode.READ_ONLY, 0, channel.size()));
                        mPdfFile = new PDFFile(bb);
                    }
                    Log.i("ParsePDF", "parse pdf called file = " + file.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.i("Error", "Can't Read File");
            }
        }

        private Bitmap showPage(int page, int zoom) {
            try {
                mPdfPage = mPdfFile.getPage(page, true);
                Log.i("File Page", "mPdfPage creates width = " + mPdfPage.getWidth() + " height = " + mPdfPage.getHeight());
                float wi = mPdfPage.getWidth();
                float hei = mPdfPage.getHeight();
                RectF clip = null;

                return mPdfPage.getImage((int) (wi * 1.25),
                        (int) (hei * 1.25), clip);
            } catch (Exception  e) {
                Log.e("Error", e.getMessage(), e);
            }
            return null;
        }
    }

    public static byte[] getBytes(Bitmap bitmap) {

        com.example.myapplication.util.PrintPic printPic = PrintPic.getInstance();
        printPic.init(bitmap);
        if (null != bitmap) {
            if (bitmap.isRecycled()) {
                bitmap = null;
            } else {
                bitmap.recycle();
                bitmap = null;
            }
        }
        byte[] bytes = printPic.printDraw();
        ArrayList<byte[]> printBytes = new ArrayList<byte[]>();
//        printBytes.add(new byte[]{0x1b, 0x40});
//        printBytes.add(new byte[]{0x0a});

        printBytes.add(SELECT_BIT_IMAGE_MODE);

        printBytes.add(bytes);
        printBytes.add(PrinterCommands.FEED_LINE);
        printBytes.add(PrinterCommands.FEED_LINE);

        PrintQueue.getQueue().add(bytes);
        return bytes;
    }
    public static   ArrayList<Bitmap> pdfToBitmap(File pdfFile) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY));

            Bitmap bitmap;
            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);

                int width =   Resources.getSystem().getDisplayMetrics().densityDpi / 72 * page.getWidth();
                int height = Resources.getSystem().getDisplayMetrics().densityDpi / 72 * page.getHeight();
                bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                bitmaps.add(bitmap);

                // close the page
                page.close();

            }

            // close the renderer
            renderer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return bitmaps;

    }

}
