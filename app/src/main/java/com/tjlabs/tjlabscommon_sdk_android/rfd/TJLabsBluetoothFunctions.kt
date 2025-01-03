package com.tjlabs.tjlabscommon_sdk_android.rfd

import android.util.Log

object TJLabsBluetoothFunctions {
    internal fun removeBLEScanInfoSetOlderThan(bleScanInfoSet: MutableSet<BLEScanInfo>, elapsedRealtimeNano: Long) : MutableSet<BLEScanInfo> {
        val bleScanInfoSetCopy = bleScanInfoSet.toHashSet()
        bleScanInfoSetCopy.removeAll { it.timestampNanos < elapsedRealtimeNano }
        return bleScanInfoSetCopy
    }

    internal fun averageBLEScanInfoSet(bleScanInfoSet: MutableSet<BLEScanInfo>) : Map<String, Float> {
        var averageMap = mapOf<String,Float>()
        try {
            val bleScanInfoSetCopy = bleScanInfoSet.toHashSet()
            val beaconInfoGroupedByID = bleScanInfoSetCopy.groupBy { it.id }
            val rssiClassMap = beaconInfoGroupedByID.map {
                it.key to RSSIClass(
                    count = it.value.count(),
                    total = it.value.sumOf { beaconInfoListOfID -> beaconInfoListOfID.rssi }
                )
            }.toMap()
            val log = "_"
            rssiClassMap.map { log + "\n${it.key} // cnt : ${it.value.count} // mean : ${it.value.count / it.value.total}" }
            averageMap = rssiClassMap.map { it.key to (it.value.getAverage()).toFloat()}.toMap()
        } catch (e: Exception) {
            Log.e("TJLabsBluetoothFunctions", "error, average BLE Scan Info")
        }

        return averageMap
    }

    internal fun checkBLEChannelNum(bleMap: Map<String, Float>?, threshold : Float = -95f): Int {
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