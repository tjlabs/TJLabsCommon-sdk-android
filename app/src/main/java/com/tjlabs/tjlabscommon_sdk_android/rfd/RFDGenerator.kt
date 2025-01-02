package com.tjlabs.tjlabscommon_sdk_android.rfd

import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.getCurrentTimeInMilliseconds

class RFDGenerator {
    // Timer 관련 변수
    interface RFDGeneratorListener {
        fun onRFDResult(rfd: ReceivedForce)
    }

    var rfdListener: RFDGeneratorListener? = null


    fun generateRFD(
        intervalMillis: Long,
        id: String = "",
        bleScanInfoSet: MutableSet<BLEScanInfo>,
        pressure: Float = 0f
    ) {
        val averageBleMap = TJLabsBluetoothFunctions.averageBLEScanInfoSet(bleScanInfoSet)
        rfdListener?.onRFDResult(
            ReceivedForce(
                id,
                getCurrentTimeInMilliseconds() - (intervalMillis / 2),
                averageBleMap,
                pressure
            )
        )
    }


}