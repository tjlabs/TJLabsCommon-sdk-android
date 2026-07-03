package com.tjlabs.tjlabscommon_sdk_android.rfd

data class ReceivedForce(
    val tenant_user_name: String = "",
    val mobile_time: Long = 0L,
    val rfs: Map<String, Float> = mutableMapOf("temp" to -100f),
    val pressure: Float = 0f
)

internal data class BLEScanInfo(
    val id: String = "",
    val rssi: Int = -100,
    val timestampNanos: Long = 0L,
)


internal data class RSSIClass(
    val count: Int,
    val total: Int
) {
    fun getAverage(): Int {
        return total / count
    }

    fun getCountString(): String {
        return "$count"
    }
}

enum class ScanMode{
    NO_FILTER_SCAN, ONLY_WARD_SCAN, ONLY_SEI_SCAN, WARD_SEI_SCAN, ONLY_IBEACON_SCAN, WARD_ALL_SCAN
}

object RFDErrorCode {
    // Unified error code mapping (legacy adapter for existing RFD callback contracts)
    const val PERMISSION_DENIED = 1600
    const val BLUETOOTH_DISABLED = 1601
    const val BLUETOOTH_NOT_SUPPORTED = 1602
    const val BLE_SCAN_STOP = 1603
    const val DUPLICATE_SCAN_START = 1604

    // Legacy alias
    const val SCAN_TIMEOUT = BLE_SCAN_STOP
}
