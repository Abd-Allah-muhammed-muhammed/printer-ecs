package com.albadr.printer.util;


  import static com.albadr.printer.util.Constants.mm50;
  import static com.albadr.printer.util.Constants.mm80;

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
     * Supersampling factor for the auto-fit path: the page is rasterized this much
     * wider than the print head, then downscaled with filtering. Rendering straight
     * at 384px makes small Arabic glyphs break up; 2x downscaled stays readable.
     */
    private static final int RENDER_SCALE = 2;

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

    public static ArrayList<Bitmap> pdfToBitmap(File pdfFile) {
        String printSize = MyApp.getSharedPreferencesManager().getPrintSize();

        // Only the 58mm roll uses the auto-fit path. 80mm and 104mm already print
        // correctly with the fixed sizes below, and auto-fit would reflow them.
        if (mm50.equals(printSize)) {
            return renderAutoFit(pdfFile);
        }
        return renderFixed(pdfFile, printSize);
    }

    // ---------------------------------------------------------------------
    // 80mm / 104mm: fixed render size, bottom whitespace trimmed.
    // ---------------------------------------------------------------------

    private static ArrayList<Bitmap> renderFixed(File pdfFile, String printSize) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE));

            Bitmap bitmap;
            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);

                int width;
                int height;

                if (mm80.equals(printSize)) {
                    width = 565;
                    height = 1655;
                } else {
                    width = 735;
                    height = 2151;
                }

                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                // Fill with white before rendering so transparent pixels become white
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                // Trim white space from the bottom of the bitmap
                bitmap = trimBottom(bitmap);

                bitmaps.add(bitmap);

                // close the page
                page.close();
            }

            // close the renderer
            renderer.close();
        } catch (Exception ex) {

            Log.d(TAG, "pdfToBitmap: " + ex.getMessage());
            ex.printStackTrace();
        }

        return bitmaps;
    }

    /**
     * Trims white space from the bottom of a bitmap.
     * Scans from the bottom up to find the last row that contains non-white pixels,
     * then crops the bitmap to that height plus a small padding.
     */
    private static Bitmap trimBottom(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Minimum height to avoid returning an empty bitmap
        int minHeight = 50;
        // Padding to add below the last content row (in pixels)
        int bottomPadding = 30;

        int lastContentRow = minHeight;
        int[] rowPixels = new int[width];

        for (int y = height - 1; y >= minHeight; y--) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1);

            boolean hasContent = false;
            for (int x = 0; x < width; x++) {
                if (isContent(rowPixels[x])) {
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
            Log.d(TAG, "trimBottom: trimmed from " + height + " to " + newHeight + " pixels");
            Bitmap trimmedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, newHeight);
            // Recycle the original bitmap to free memory
            if (trimmedBitmap != bitmap) {
                bitmap.recycle();
            }
            return trimmedBitmap;
        }

        return bitmap;
    }

    // ---------------------------------------------------------------------
    // 58mm: side margins cropped, content scaled to the exact head width.
    // ---------------------------------------------------------------------

    private static ArrayList<Bitmap> renderAutoFit(File pdfFile) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        PdfRenderer renderer = null;
        try {
            renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE));

            int targetWidth = Constants.WIDTH_PX_58;
            int renderWidth = targetWidth * RENDER_SCALE;

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
     * The source app lays its receipt out with its own margins (a WebView print adds
     * roughly half an inch each side), and those margins survive into the PDF. On a
     * 48mm head that leaves a wide blank band on both sides of the paper, so the
     * margins are measured here and dropped before printing.
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
