package com.tjlabs.tjlabscommon_sdk_android.rfd

data class ReceivedForce(
    val user_id: String = "",
    val mobile_time: Long = 0L,
    val ble: Map<String, Float> = mutableMapOf("temp" to -100f),
    val pressure: Float = 0f
)

data class BLEScanInfo(
    val id: String = "",
    val rssi: Int = -100,
    val timestampNanos: Long = 0L,
)


data class RSSIClass(
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
