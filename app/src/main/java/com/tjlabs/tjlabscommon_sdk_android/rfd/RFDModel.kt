package com.tjlabs.tjlabscommon_sdk_android.rfd

data class ReceivedForce(
    val user_id: String = "",
    val mobile_time: Long = 0L,
    val ble: Map<String, Float> = mutableMapOf("temp" to -100f),
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
    NO_FILTER_SCAN, ONLY_WARD_SCAN, ONLY_SEI_SCAN, WARD_SEI_SCAN
}

object RFDErrorCode {
    //BLE Hardware
    const val BLUETOOTH_DISABLED = 100
    const val BLUETOOTH_NOT_SUPPORTED = 101
    const val AIRPLANE_MODE_ACTIVATION = 102

    //BLE Permission
    const val PERMISSION_DENIED = 200
    const val PERMISSION_STATE_CHANGED = 201

    //BLE Scan Result
    const val SCAN_TIMEOUT = 300
    const val INVALID_DEVICE_NAME = 301
    const val INVALID_RSSI = 302

    //RFD Generation Service
    const val DUPLICATE_SCAN_START = 400
}