package com.albadr.printer.util;

public class Constants {


    public static  String mm50 ="50mm";
    public static  String mm80 ="80mm";
    public static  String mm100 ="100mm";
    public static final String EVENT_CONNECT_STATUS = "EVENT_CONNECT_STATUS";
    public static final int PRINTER_390 = 390;
    public static final int PRINTER_500 = 580;

    /**
     * Printable width of each roll size, in millimeters.
     * A roll is always wider than what the head can actually print:
     * 58mm roll -> 48mm printable, 80mm roll -> 72mm, 104mm roll -> 96mm.
     */
    public static final float WIDTH_MM_58 = 48f;
    public static final float WIDTH_MM_80 = 72f;
    public static final float WIDTH_MM_104 = 96f;

    /**
     * Printable width in dots at 203 dpi (8 dots/mm), rounded to a multiple of 8
     * because ESC/POS raster images are sent byte-per-8-dots.
     * These must match what EscPosPrinter computes from the mm values above,
     * otherwise the raster is clipped or mis-aligned by the printer.
     */
    public static final int WIDTH_PX_58 = 384;
    public static final int WIDTH_PX_80 = 576;
    public static final int WIDTH_PX_104 = 768;

    public static final int CHARS_PER_LINE_58 = 32;
    public static final int CHARS_PER_LINE_80 = 42;
    public static final int CHARS_PER_LINE_104 = 56;

    /**
     * Printable width in mils (1/1000 inch) — the unit PrintAttributes.MediaSize uses.
     * 48mm = 1890 mils, 72mm = 2835 mils, 96mm = 3780 mils. Page height keeps the
     * 1:2.75 ratio the print service has always advertised.
     */
    public static final int MEDIA_WIDTH_MILS_58 = 1890;
    public static final int MEDIA_WIDTH_MILS_80 = 2835;
    public static final int MEDIA_WIDTH_MILS_104 = 3780;

    public static final int MEDIA_HEIGHT_MILS_58 = 5198;
    public static final int MEDIA_HEIGHT_MILS_80 = 7796;
    public static final int MEDIA_HEIGHT_MILS_104 = 10395;

    /**
     * How much wider the advertised page gets in compatibility mode.
     *
     * A source whose layout will not reflow below some minimum overflows a narrow
     * page and the PDF arrives already clipped — nothing downstream can recover it.
     * Handing that source a page 2.33x the printable width gives its layout room,
     * and PrintUtils scales the result back down onto the head. Costs sharpness, so
     * it is off by default.
     */
    public static final float WIDE_PAGE_FACTOR = 2.33f;

    /** Advertised page width in mils for a roll size, honouring compatibility mode. */
    public static int mediaWidthMilsFor(String printSize, boolean widePage) {
        int width;
        if (mm50.equals(printSize)) {
            width = MEDIA_WIDTH_MILS_58;
        } else if (mm80.equals(printSize)) {
            width = MEDIA_WIDTH_MILS_80;
        } else {
            width = MEDIA_WIDTH_MILS_104;
        }
        return widePage ? Math.round(width * WIDE_PAGE_FACTOR) : width;
    }

    /** Advertised page height in mils for a roll size, honouring compatibility mode. */
    public static int mediaHeightMilsFor(String printSize, boolean widePage) {
        int height;
        if (mm50.equals(printSize)) {
            height = MEDIA_HEIGHT_MILS_58;
        } else if (mm80.equals(printSize)) {
            height = MEDIA_HEIGHT_MILS_80;
        } else {
            height = MEDIA_HEIGHT_MILS_104;
        }
        return widePage ? Math.round(height * WIDE_PAGE_FACTOR) : height;
    }

    /** Printable width in dots for the currently selected roll size. */
    public static int widthPxFor(String printSize) {
        if (mm50.equals(printSize)) {
            return WIDTH_PX_58;
        } else if (mm80.equals(printSize)) {
            return WIDTH_PX_80;
        }
        return WIDTH_PX_104;
    }

    /** Printable width in millimeters for the currently selected roll size. */
    public static float widthMmFor(String printSize) {
        if (mm50.equals(printSize)) {
            return WIDTH_MM_58;
        } else if (mm80.equals(printSize)) {
            return WIDTH_MM_80;
        }
        return WIDTH_MM_104;
    }

    /** Character count per line for the currently selected roll size. */
    public static int charsPerLineFor(String printSize) {
        if (mm50.equals(printSize)) {
            return CHARS_PER_LINE_58;
        } else if (mm80.equals(printSize)) {
            return CHARS_PER_LINE_80;
        }
        return CHARS_PER_LINE_104;
    }
}
