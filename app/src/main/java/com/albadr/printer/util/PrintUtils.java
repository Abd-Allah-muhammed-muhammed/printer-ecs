package com.albadr.printer.util;


  import static com.albadr.printer.util.Constants.mm50;
  import static com.albadr.printer.util.Constants.mm80;

  import android.graphics.Bitmap;
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
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY));


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

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

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

    private static final String TAG = "PrintUtils";

}
