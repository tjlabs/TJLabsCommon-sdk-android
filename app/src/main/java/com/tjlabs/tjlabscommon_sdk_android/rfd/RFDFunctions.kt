package com.tjlabs.tjlabscommon_sdk_android.rfd

object RFDFunctions {
    fun checkBleChannelNum(bleMap: Map<String, Float>?, threshold : Float = -95f): Int {
        var numChannels = 0
        bleMap?.forEach { (key, value) ->
            val bleRssi = value ?: -100.0f
            if (bleRssi > threshold) {
                numChannels++
            }
        }
        return numChannels
    }
}