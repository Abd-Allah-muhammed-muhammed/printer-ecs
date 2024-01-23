package com.example.myapplication.util;


  import static com.example.myapplication.util.PrinterCommands.SELECT_BIT_IMAGE_MODE;

  import android.content.res.Resources;
  import android.graphics.Bitmap;
import android.graphics.Canvas;
  import android.graphics.Color;
  import android.graphics.Paint;
  import android.graphics.RectF;
  import android.graphics.pdf.PdfDocument;
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



     public static   ArrayList<Bitmap> pdfToBitmap(File pdfFile) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY));


            Bitmap bitmap;
            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);

                int width =   380;
                int height = 1200;
                  bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

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

    private static final String TAG = "PrintUtils";

}
