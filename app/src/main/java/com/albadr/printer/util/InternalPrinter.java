package com.albadr.printer.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.util.Log;

/**
 * The printer built into a Sunmi handheld.
 *
 * Sunmi exposes it over Bluetooth at a fixed address rather than behind a vendor SDK,
 * so it prints over the same RFCOMM path as any external printer. What it never does is
 * bond: it is wired into the device, so there is nothing to pair with, and
 * {@link BluetoothAdapter#getBondedDevices()} leaves it out. A picker built only from
 * bonded devices therefore comes up empty on the one kind of device that always has a
 * printer in it, which is why it looked broken on handhelds and fine on phones.
 */
public final class InternalPrinter {

    private static final String TAG = "InternalPrinter";

    /** Address Sunmi assigns the built-in printer on every one of its handhelds. */
    public static final String MAC = "00:11:22:33:44:55";

    /** Name the Sunmi Bluetooth stack reports for it. */
    private static final String BT_NAME = "InnerPrinter";

    /** What the picker calls it, since an unbonded device reports no name of its own. */
    public static final String LABEL = "الطابعة الداخلية";

    private InternalPrinter() {
    }

    /** Whether {@code address} belongs to the built-in printer. */
    public static boolean isInternal(String address) {
        return MAC.equalsIgnoreCase(address);
    }

    /**
     * Whether {@code device} is the built-in printer.
     *
     * The address is the reliable half of this; the name covers a ROM that wires the
     * printer up at some other address, which is the case the fixed address alone
     * would miss.
     */
    @SuppressLint("MissingPermission")
    public static boolean isInternal(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        if (isInternal(device.getAddress())) {
            return true;
        }
        try {
            return BT_NAME.equalsIgnoreCase(device.getName());
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * The built-in printer as a Bluetooth device, or null when this handset has none.
     *
     * <p>The caller must hold BLUETOOTH_CONNECT on Android 12 and above.
     */
    @SuppressLint("MissingPermission")
    public static BluetoothDevice find() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return null;
        }

        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(MAC);
        } catch (IllegalArgumentException e) {
            return null;
        }

        // getRemoteDevice() hands back a device for any well-formed address whether or
        // not anything is there, so the address alone proves nothing. Two things do:
        // the vendor string, and the name the Bluetooth stack has cached for that
        // address. Either is enough on its own — a rebranded ROM fails the first, a
        // stack that has not seen the printer yet fails the second.
        if (isSunmi()) {
            return device;
        }

        String name = null;
        try {
            name = device.getName();
        } catch (SecurityException e) {
            Log.w(TAG, "find: not allowed to read the device name");
        }

        return BT_NAME.equalsIgnoreCase(name) ? device : null;
    }

    private static boolean isSunmi() {
        return "SUNMI".equalsIgnoreCase(Build.MANUFACTURER)
                || "SUNMI".equalsIgnoreCase(Build.BRAND);
    }
}
