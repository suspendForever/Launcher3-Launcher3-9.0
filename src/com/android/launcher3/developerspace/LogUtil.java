package com.android.launcher3.developerspace;

import android.util.Log;

public class LogUtil {
    private static final String TAG = "LhmLog";

    public static void d(String tag, String message) {
        Log.d(TAG, tag + ": " + message);
    }

    public static void e(String tag, String message) {
        Log.e(TAG, tag + ": " + message);
    }
}
