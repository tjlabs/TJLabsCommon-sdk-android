package com.tjlabs.tjlabscommon_sdk_android.rfd

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
        }

        return averageMap
    }
}