package com.tjlabs.tjlabscommon_sdk_android.utils

import android.util.Log

object TJLabsCommonLog {
    @Volatile
    private var isEnabled: Boolean = false

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isLogEnabled(): Boolean = isEnabled

    fun d(tag: String, message: String) {
        if (isEnabled) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (isEnabled) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (isEnabled) Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }
}
