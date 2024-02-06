package com.albadr.printer.util;

import android.bluetooth.BluetoothAdapter;

import java.util.ArrayList;

public class PrintQueue {
    /**
     * instance
     */
    private static PrintQueue mInstance;
    /**
     * context
     */
    /**
     * print queue
     */
    private ArrayList<byte[]> mQueue;
    /**
     * bluetooth adapter
     */
    private BluetoothAdapter mAdapter;
    /**
     * bluetooth service
     */


    private PrintQueue() {
    }

    public static PrintQueue getQueue() {
        if (null == mInstance) {
            mInstance = new PrintQueue();
        }
        return mInstance;
    }

    /**
     * add print bytes to queue. and call print
     *
     * @param bytes bytes
     */
    public synchronized void add(byte[] bytes) {
        if (null == mQueue) {
            mQueue = new ArrayList<byte[]>();
        }
        if (null != bytes) {
            mQueue.add(bytes);
        }
//        print();
    }

    /**
     * add print bytes to queue. and call print
     *
     * @param bytesList bytesList
     */
    public synchronized void add(ArrayList<byte[]> bytesList) {
        if (null == mQueue) {
            mQueue = new ArrayList<byte[]>();
        }
        if (null != bytesList) {
            mQueue.addAll(bytesList);
        }
//        print();
    }


    /**
     * print queue
     */
//    public synchronized void print() {
//        try {
//            if (null == mQueue || mQueue.size() <= 0) {
//                return;
//            }
//            if (null == mAdapter) {
//                mAdapter = BluetoothAdapter.getDefaultAdapter();
//            }
//            if (null == mBtService) {
//                mBtService = new BtService(mContext);
//            }
//            if (mBtService.getState() != BtService.STATE_CONNECTED) {
//                if (!TextUtils.isEmpty(AppInfo.btAddress)) {
//                    BluetoothDevice device = mAdapter.getRemoteDevice(AppInfo.btAddress);
//                    mBtService.connect(device);
//                    return;
//                }
//            }
//            while (mQueue.size() > 0) {
//                mBtService.write(mQueue.get(0));
//                mQueue.remove(0);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    /**
     * disconnect remote device
     */
//    public void disconnect() {
//        try {
//            if (null != mBtService) {
//                mBtService.stop();
//                mBtService = null;
//            }
//            if (null != mAdapter) {
//                mAdapter = null;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}

