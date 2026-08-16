package com.albadr.printer.util;


  import android.graphics.Bitmap;
  import android.graphics.Canvas;
  import android.graphics.Color;
  import android.graphics.pdf.PdfRenderer;
  import android.os.ParcelFileDescriptor;
  import android.util.Log;

  import com.albadr.printer.MyApp;

  import java.io.File;
  import java.util.ArrayList;

public class PrintUtils {

    /**
     * Width the page is rasterized at before cropping, in pixels.
     *
     * Comfortably above every head width so there is detail to spare after the
     * margins are cropped away, while one page stays around 16MB of bitmap.
     * Supersampling then downscaling with filtering is what keeps small Arabic
     * glyphs legible once they land on a 384 dot head.
     */
    private static final int RENDER_WIDTH_PX = 1200;

    /** Pixels with all channels above this are treated as blank paper. */
    private static final int WHITE_THRESHOLD = 250;

    /** Alpha below this means nothing was painted there. */
    private static final int ALPHA_THRESHOLD = 20;

    /**
     * A page whose detected content is narrower than this fraction of the page is
     * not cropped horizontally. Guards against a nearly blank page (a stray dot, a
     * page number) being blown up to full paper width.
     */
    private static final float MIN_CONTENT_WIDTH_RATIO = 0.25f;

    /** Blank rows kept below the last printed row, in rendered pixels. */
    private static final int BOTTOM_PADDING = 24;

    /**
     * Renders a print job's PDF into one bitmap per page, each exactly as wide as the
     * print head, with any blank margins the source left around the content removed.
     *
     * Nothing here is specific to a particular source: a document that already fills
     * the page it was given has no margins to find and comes through at 1:1.
     */
    public static ArrayList<Bitmap> pdfToBitmap(File pdfFile) {
        String printSize = MyApp.getSharedPreferencesManager().getPrintSize();
        return renderAutoFit(pdfFile, Constants.widthPxFor(printSize));
    }

    private static ArrayList<Bitmap> renderAutoFit(File pdfFile, int targetWidth) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        PdfRenderer renderer = null;
        try {
            renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE));

            // Never rasterize below the head width, however narrow the head is.
            int renderWidth = Math.max(RENDER_WIDTH_PX, targetWidth * 2);

            final int pageCount = renderer.getPageCount();

            // Two passes over the document. The horizontal crop has to be identical
            // for every page, otherwise each page would be scaled by a different
            // factor and the text size would jump between pages of one receipt. The
            // pages are re-rendered instead of cached so only one full-resolution
            // bitmap is alive at a time.
            int left = renderWidth;
            int right = -1;
            for (int i = 0; i < pageCount; i++) {
                Bitmap page = renderPage(renderer, i, renderWidth);
                int[] bounds = contentColumns(page);
                page.recycle();

                if (bounds[1] >= 0) {
                    left = Math.min(left, bounds[0]);
                    right = Math.max(right, bounds[1]);
                }
            }

            int contentWidth = right - left + 1;
            if (right < 0 || contentWidth < renderWidth * MIN_CONTENT_WIDTH_RATIO) {
                // Nothing printed, or a nearly blank page whose few marks would be
                // blown up to full paper width. Keep the page as laid out.
                Log.d(TAG, "renderAutoFit: content too narrow (" + contentWidth + "px), keeping full width");
                left = 0;
                right = renderWidth - 1;
            } else {
                Log.d(TAG, "renderAutoFit: cropping side margins to [" + left + ", " + right + "] of " + renderWidth);
            }

            for (int i = 0; i < pageCount; i++) {
                Bitmap page = renderPage(renderer, i, renderWidth);
                Bitmap output = cropAndScale(page, left, right, targetWidth);
                if (output != null) {
                    bitmaps.add(output);
                }
            }
        } catch (Exception ex) {

            Log.d(TAG, "renderAutoFit: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (renderer != null) {
                try {
                    renderer.close();
                } catch (Exception e) {
                    Log.w(TAG, "renderAutoFit: failed to close renderer: " + e.getMessage());
                }
            }
        }

        return bitmaps;
    }

    /** Rasterizes one page at {@code renderWidth}, keeping the page's aspect ratio. */
    private static Bitmap renderPage(PdfRenderer renderer, int index, int renderWidth) {
        PdfRenderer.Page page = renderer.openPage(index);
        try {
            // Passing a bitmap with a different ratio than the page makes PdfRenderer
            // scale x and y independently, which stretched the receipt vertically.
            int renderHeight = Math.max(1,
                    Math.round((float) renderWidth * page.getHeight() / page.getWidth()));

            Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);

            // Fill with white before rendering so transparent pixels become white
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } finally {
            page.close();
        }
    }

    /**
     * Finds the leftmost and rightmost printed column of one page.
     *
     * The source app lays its receipt out with its own margins — the web invoice
     * only covers about 65% of the page width — and those margins survive into the
     * PDF, where they become a blank band down both sides of the paper. They are
     * measured here so they can be dropped before printing.
     *
     * @return {left, right} inclusive column bounds, or {@code {width, -1}} when the
     *         page is blank.
     */
    private static int[] contentColumns(Bitmap page) {
        int pageWidth = page.getWidth();
        int height = page.getHeight();

        int left = pageWidth;
        int right = -1;

        int[] rowPixels = new int[pageWidth];

        for (int y = 0; y < height; y++) {
            page.getPixels(rowPixels, 0, pageWidth, 0, y, pageWidth, 1);

            for (int x = 0; x < left; x++) {
                if (isContent(rowPixels[x])) {
                    left = x;
                    break;
                }
            }
            for (int x = pageWidth - 1; x > right; x--) {
                if (isContent(rowPixels[x])) {
                    right = x;
                    break;
                }
            }

            // Nothing left to narrow down.
            if (left == 0 && right == pageWidth - 1) {
                break;
            }
        }

        return new int[]{left, right};
    }

    /**
     * Crops the page to the given columns and to its own last printed row, then
     * scales it to exactly the print head width so it fills the paper edge to edge.
     * Recycles the source bitmap.
     */
    private static Bitmap cropAndScale(Bitmap page, int left, int right, int targetWidth) {
        int pageWidth = page.getWidth();
        int pageHeight = page.getHeight();

        int bottom = lastContentRow(page);
        if (bottom < 0) {
            // Fully blank page, nothing worth feeding paper for.
            page.recycle();
            return null;
        }

        int cropWidth = Math.min(right - left + 1, pageWidth - left);
        int cropHeight = Math.min(bottom + 1 + BOTTOM_PADDING, pageHeight);

        if (cropWidth <= 0 || cropHeight <= 0) {
            page.recycle();
            return null;
        }

        Bitmap cropped = Bitmap.createBitmap(page, left, 0, cropWidth, cropHeight);
        if (cropped != page) {
            page.recycle();
        }

        int scaledHeight = Math.max(1,
                Math.round((float) cropped.getHeight() * targetWidth / cropped.getWidth()));

        if (cropped.getWidth() == targetWidth && cropped.getHeight() == scaledHeight) {
            return cropped;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(cropped, targetWidth, scaledHeight, true);
        if (scaled != cropped) {
            cropped.recycle();
        }

        Log.d(TAG, "cropAndScale: " + pageWidth + "x" + pageHeight
                + " -> " + targetWidth + "x" + scaledHeight);
        return scaled;
    }

    /** Index of the last row containing anything printed, or -1 for a blank page. */
    private static int lastContentRow(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] rowPixels = new int[width];

        for (int y = height - 1; y >= 0; y--) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1);

            for (int x = 0; x < width; x++) {
                if (isContent(rowPixels[x])) {
                    return y;
                }
            }
        }

        return -1;
    }

    private static boolean isContent(int pixel) {
        if (Color.alpha(pixel) <= ALPHA_THRESHOLD) {
            return false;
        }
        return Color.red(pixel) < WHITE_THRESHOLD
                || Color.green(pixel) < WHITE_THRESHOLD
                || Color.blue(pixel) < WHITE_THRESHOLD;
    }

    private static final String TAG = "PrintUtils";

}
