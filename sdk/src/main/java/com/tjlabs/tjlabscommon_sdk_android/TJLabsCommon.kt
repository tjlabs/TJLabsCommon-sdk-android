package com.tjlabs.tjlabscommon_sdk_android

import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsCommonLog

object TJLabsCommon {
    fun setLogEnabled(enabled: Boolean) {
        TJLabsCommonLog.setEnabled(enabled)
    }

    fun isLogEnabled(): Boolean = TJLabsCommonLog.isLogEnabled()
}
