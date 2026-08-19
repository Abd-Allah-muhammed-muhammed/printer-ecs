package com.albadr.printer.util;


  import android.graphics.Bitmap;
  import android.graphics.Canvas;
  import android.graphics.Color;
  import android.graphics.Matrix;
  import android.graphics.pdf.PdfRenderer;
  import android.os.ParcelFileDescriptor;
  import android.util.Log;

  import com.albadr.printer.MyApp;

  import java.io.File;
  import java.util.ArrayList;

public class PrintUtils {

    /**
     * Width the page is rasterized at for measuring only, in pixels. Big enough to
     * locate the edges of the content precisely; the pixels themselves are discarded.
     */
    private static final int MEASURE_WIDTH_PX = 1200;

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

    /** Blank rows kept below the last printed row, in print head dots. */
    private static final int BOTTOM_PADDING_PX = 12;

    /**
     * Grey level at or below which a pixel becomes a fired dot. A thermal head has
     * no greys, so something has to make the call; the ESC/POS library would make it
     * at 128, which drops the soft edges of small Arabic glyphs and prints them
     * broken and faint. Deciding it here, and a little more generously, keeps thin
     * strokes solid. Raise it for darker output, lower it for lighter.
     */
    private static final int INK_THRESHOLD = 160;

    /**
     * Renders the PDF of a print job into one bitmap per page, each exactly as wide
     * as the print head, with any blank margins the source left around the content
     * removed.
     *
     * Nothing here is specific to a particular source: a document that already fills
     * the page it was given has no margins to find and comes through unchanged.
     */
    public static ArrayList<Bitmap> pdfToBitmap(File pdfFile) {
        String printSize = MyApp.getSharedPreferencesManager().getPrintSize();
        return render(pdfFile, Constants.widthPxFor(printSize));
    }

    private static ArrayList<Bitmap> render(File pdfFile, int targetWidth) {
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        PdfRenderer renderer = null;
        try {
            renderer = new PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE));

            final int pageCount = renderer.getPageCount();
            int measureWidth = Math.max(MEASURE_WIDTH_PX, targetWidth * 2);

            // Pass one measures where the content actually sits, as a fraction of the
            // page. The horizontal bounds are shared by every page, otherwise each
            // page would be scaled differently and the text size would jump between
            // pages of one receipt. The bottom is per page, so a short last page does
            // not drag a full page of blank roll behind it.
            float left = 1f;
            float right = 0f;
            float[] bottom = new float[pageCount];

            for (int i = 0; i < pageCount; i++) {
                Bitmap page = measurePage(renderer, i, measureWidth);
                int[] columns = contentColumns(page);
                int lastRow = lastContentRow(page);

                bottom[i] = lastRow < 0 ? -1f : (float) (lastRow + 1) / page.getHeight();

                if (columns[1] >= 0) {
                    left = Math.min(left, (float) columns[0] / measureWidth);
                    right = Math.max(right, (float) (columns[1] + 1) / measureWidth);
                }

                page.recycle();
            }

            if (right - left < MIN_CONTENT_WIDTH_RATIO) {
                // Nothing printed, or a nearly blank page whose few marks would be
                // blown up to full paper width. Keep the page as laid out.
                Log.d(TAG, "render: content too narrow, keeping full page width");
                left = 0f;
                right = 1f;
            } else {
                Log.d(TAG, "render: content spans " + Math.round(left * 100) + "% to "
                        + Math.round(right * 100) + "% of page width");
            }

            // Pass two rasterizes straight at the resolution of the head. Rendering
            // large and scaling down afterwards blurs hairline strokes into greys
            // that then threshold away, which is what made the print look faint.
            for (int i = 0; i < pageCount; i++) {
                if (bottom[i] < 0f) {
                    continue;
                }
                Bitmap output = renderPageToWidth(renderer, i, targetWidth, left, right, bottom[i]);
                if (output != null) {
                    binarize(output);
                    bitmaps.add(output);
                }
            }
        } catch (Exception ex) {

            Log.d(TAG, "render: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (renderer != null) {
                try {
                    renderer.close();
                } catch (Exception e) {
                    Log.w(TAG, "render: failed to close renderer: " + e.getMessage());
                }
            }
        }

        return bitmaps;
    }

    /** Rasterizes one page at {@code width}, keeping the aspect ratio of the page. */
    private static Bitmap measurePage(PdfRenderer renderer, int index, int width) {
        PdfRenderer.Page page = renderer.openPage(index);
        try {
            // Passing a bitmap with a different ratio than the page makes PdfRenderer
            // scale x and y independently, which stretches the receipt vertically.
            int height = Math.max(1, Math.round((float) width * page.getHeight() / page.getWidth()));

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

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
     * Renders the content region of one page so it lands exactly {@code targetWidth}
     * dots wide, cropped to the last printed row of that page.
     *
     * @param left   left edge of the content as a fraction of page width
     * @param right  right edge of the content as a fraction of page width
     * @param bottom last printed row as a fraction of page height
     */
    private static Bitmap renderPageToWidth(PdfRenderer renderer, int index, int targetWidth,
                                            float left, float right, float bottom) {
        PdfRenderer.Page page = renderer.openPage(index);
        try {
            float pageWidth = page.getWidth();
            float pageHeight = page.getHeight();

            float contentWidth = pageWidth * (right - left);
            if (contentWidth <= 0f) {
                return null;
            }

            float scale = targetWidth / contentWidth;

            int height = Math.min(
                    Math.round(pageHeight * scale),
                    Math.round(pageHeight * bottom * scale) + BOTTOM_PADDING_PX);
            if (height < 1) {
                height = 1;
            }

            Bitmap bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888);

            // Fill with white before rendering so transparent pixels become white
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(-pageWidth * left * scale, 0f);

            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            Log.d(TAG, "renderPageToWidth: page " + (index + 1) + " -> "
                    + targetWidth + "x" + height + " at " + scale + "x");
            return bitmap;
        } finally {
            page.close();
        }
    }

    /**
     * Flattens the page to pure black and white so the printer receives exactly what
     * was decided here, instead of the ESC/POS library deciding it again at its own
     * threshold. Modifies the bitmap in place.
     */
    private static void binarize(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] rowPixels = new int[width];

        for (int y = 0; y < height; y++) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1);

            for (int x = 0; x < width; x++) {
                int pixel = rowPixels[x];
                int luminance;

                if (Color.alpha(pixel) <= ALPHA_THRESHOLD) {
                    luminance = 255;
                } else {
                    luminance = (Color.red(pixel) * 299
                            + Color.green(pixel) * 587
                            + Color.blue(pixel) * 114) / 1000;
                }

                rowPixels[x] = luminance < INK_THRESHOLD ? 0xFF000000 : 0xFFFFFFFF;
            }

            bitmap.setPixels(rowPixels, 0, width, 0, y, width, 1);
        }
    }

    /**
     * Finds the leftmost and rightmost printed column of one page.
     *
     * A source lays its document out with its own margins, and those margins survive
     * into the PDF, where they become a blank band down both sides of the paper.
     * They are measured here so they can be dropped before printing.
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
