package com.albadr.printer.util;


  import static com.albadr.printer.util.Constants.mm50;
  import static com.albadr.printer.util.Constants.mm80;

  import android.graphics.Bitmap;
  import android.graphics.Canvas;
  import android.graphics.Color;
  import android.graphics.ColorSpace;
  import android.graphics.pdf.PdfRenderer;
  import android.os.ParcelFileDescriptor;
  import android.util.Log;

  import com.albadr.printer.MyApp;

  import java.io.File;
  import java.util.ArrayList;

public class PrintUtils {



     public static   ArrayList<Bitmap> pdfToBitmap(File pdfFile) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE));


            Bitmap bitmap;
            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);



                SharedPreferencesManager sharedPreferencesManager = MyApp.getSharedPreferencesManager();


                int width;

                if (sharedPreferencesManager.getPrintSize().equals(mm50)) {

                    width = 410;
                }else if (sharedPreferencesManager.getPrintSize().equals(mm80)){

                    width = 565;
                }else {

                    width = 735;
                }

                  int height;

                if (sharedPreferencesManager.getPrintSize().equals(mm50)) {

                    height = 1200;
                }else if (sharedPreferencesManager.getPrintSize().equals(mm80)){

                    height = 1655;
                }else {

                    height = 2151;
                }




                  bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                // Fill with white before rendering so transparent pixels become white
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                // Trim white space from the bottom of the bitmap
                bitmap = trimWhiteSpace(bitmap);

                bitmaps.add(bitmap);


                // close the page
                page.close();

            }

            // close the renderer
            renderer.close();
        } catch (Exception ex) {

            Log.d(TAG, "pdfToBitmap: "+ex.getMessage());
            ex.printStackTrace();
        }

        return bitmaps;

    }

    /**
     * Trims white space from the bottom of a bitmap.
     * Scans from the bottom up to find the last row that contains non-white pixels,
     * then crops the bitmap to that height plus a small padding.
     */
    private static Bitmap trimWhiteSpace(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Minimum height to avoid returning an empty bitmap
        int minHeight = 50;
        // Padding to add below the last content row (in pixels)
        int bottomPadding = 30;

        // Threshold: pixels with all RGB channels above this value are considered "white"
        int whiteThreshold = 250;

        // Scan rows from bottom to top to find the last non-white row
        int lastContentRow = minHeight;
        int[] rowPixels = new int[width];

        for (int y = height - 1; y >= minHeight; y--) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1);

            boolean hasContent = false;
            for (int x = 0; x < width; x++) {
                int pixel = rowPixels[x];
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int a = Color.alpha(pixel);

                // Check if pixel is non-white (and not fully transparent)
                if (a > 20 && (r < whiteThreshold || g < whiteThreshold || b < whiteThreshold)) {
                    hasContent = true;
                    break;
                }
            }

            if (hasContent) {
                lastContentRow = y;
                break;
            }
        }

        // Calculate the new height with padding
        int newHeight = Math.min(lastContentRow + bottomPadding, height);

        // Only trim if we can save at least 10% of the height
        if (newHeight < height * 0.9) {
            Log.d(TAG, "trimWhiteSpace: trimmed from " + height + " to " + newHeight + " pixels");
            Bitmap trimmedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, newHeight);
            // Recycle the original bitmap to free memory
            if (trimmedBitmap != bitmap) {
                bitmap.recycle();
            }
            return trimmedBitmap;
        }

        return bitmap;
    }

    private static final String TAG = "PrintUtils";

}
