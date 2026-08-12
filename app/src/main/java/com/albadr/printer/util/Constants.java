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
