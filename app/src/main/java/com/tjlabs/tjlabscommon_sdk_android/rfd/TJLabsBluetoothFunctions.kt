package com.tjlabs.tjlabscommon_sdk_android.rfd

import android.util.Log

internal object TJLabsBluetoothFunctions {
    fun removeBleScanInfoSetOlderThan(bleScanInfoSet: MutableSet<BLEScanInfo>, elapsedRealtimeNano: Long) : MutableSet<BLEScanInfo> {
        val bleScanInfoSetCopy = bleScanInfoSet.toHashSet()
        bleScanInfoSetCopy.removeAll { it.timestampNanos < elapsedRealtimeNano }
        return bleScanInfoSetCopy
    }

    fun averageBleScanInfoSet(bleScanInfoSet: MutableSet<BLEScanInfo>): Map<String, Float> {
        var averageMap = mapOf<String, Float>()

        try {
            val bleScanInfoSetCopy: Set<BLEScanInfo>
            synchronized(bleScanInfoSet) {
                bleScanInfoSetCopy = bleScanInfoSet.toSet() // 안전하게 복사
            }

            val beaconInfoGroupedById = bleScanInfoSetCopy.groupBy { it.id }

            val rssiClassMap = beaconInfoGroupedById.map { (id, infoList) ->
                val count = infoList.size
                val total = infoList.sumOf { it.rssi }
                val average = if (count > 0) total.toFloat() / count else 0f
                Log.d(
                    "TJLabsBluetoothFunctions",
                    "BLE ID: $id | count: $count | total RSSI: $total | average RSSI: $average | rssi list : ${infoList.map { it.rssi }}"
                )

                id to RSSIClass(count = count, total = total)
            }.toMap()

            averageMap = rssiClassMap.map { it.key to (it.value.getAverage()).toFloat() }.toMap()

        } catch (e: Exception) {
            Log.e("TJLabsBluetoothFunctions", "error, average BLE Scan Info", e)
        }

        return averageMap
    }
}