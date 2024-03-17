package com.albadr.printer.util;

import android.widget.Toast;

import com.albadr.printer.MyApp;

public class UIUtils {

    public static void toast(int strRes) {
        Toast.makeText(MyApp.get(), strRes, Toast.LENGTH_SHORT).show();
    }

    public static void toast(String str) {
        Toast.makeText(MyApp.get(), str, Toast.LENGTH_SHORT).show();
    }
}
