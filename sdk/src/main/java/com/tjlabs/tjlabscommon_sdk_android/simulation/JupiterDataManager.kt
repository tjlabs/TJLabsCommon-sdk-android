package com.tjlabs.tjlabscommon_sdk_android.simulation

import android.app.Application
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions

object JupiterDataManager {
    enum class JupiterEventCode(val code: Int) {
        START_SERVICE(1),
        STOP_SERVICE(0),
        DETECT_FIRST_RESULT(8),
        DETECT_RSSI_PEAK(300)
    }

    fun setServiceStartTime(timeMillis: Long){
        JupiterDataFunctions.setServiceStartTime(timeMillis)
    }

    fun getServiceStartTime(): String {
        return JupiterDataFunctions.getServiceStartTime()
    }

    fun clearServiceStartTime() {
        JupiterDataFunctions.clearServiceStartTime()
    }

    fun addEvent(
        application: Application,
        userId: String,
        eventCode: JupiterEventCode,
        eventInfo : String = ""
    ) {
        JupiterDataFunctions.saveEventResultAsJson(
            app = application,
            saveFlag = true,
            userId = userId,
            mobileTime = TJLabsUtilFunctions.getCurrentTimeInMilliseconds(),
            eventCode = eventCode.code,
            eventInfo = eventInfo
        )
    }
}
