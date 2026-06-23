package com.brandonstudio.azauthhook

import de.robv.android.xposed.XposedBridge

const val TAG = "AzAuthHook"

fun log(msg: String) {
    try {
        XposedBridge.log(msg)
    } catch (_: Throwable) {
        // XposedBridge may not be initialized at certain stages
    }
}
