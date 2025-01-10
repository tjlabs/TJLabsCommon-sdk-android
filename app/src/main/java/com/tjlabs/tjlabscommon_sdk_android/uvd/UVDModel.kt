package com.tjlabs.tjlabscommon_sdk_android.uvd

import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions

data class UserVelocity(
    val user_id: String = "",
    val mobile_time: Long = 0L,
    val index: Int = 0,
    val length: Float = 0f,
    val heading: Float = 0f,
    val looking: Boolean = false
)

data class UnitDistance(
    var index: Int = 0,
    var length: Float = 0f,
    var velocity: Float = 0f,
    var isIndexChanged: Boolean = false
)

enum class UserMode{
    MODE_PEDESTRIAN, MODE_VEHICLE, MODE_AUTO
}

internal data class SensorData(
    var acc: FloatArray = FloatArray(3),
    var gyro: FloatArray = FloatArray(3),
    var magRaw: FloatArray = FloatArray(6),
    var gameVector: FloatArray = FloatArray(4),
    var rotVector: FloatArray = FloatArray(5),
    var pressure: FloatArray = FloatArray(1),
    var att: Attitude = Attitude()
) {
    override fun toString(): String {
        return "time=${System.currentTimeMillis()}, " + "\n" +
                "acc=${acc.joinToString(",")}, " + "\n" +
                "gyro=${gyro.joinToString(",")}, " + "\n" +
                "magRaw=${magRaw.joinToString(",")}, " + "\n" +
                "gameVector=${gameVector.joinToString(",")}, " + "\n" +
                "rotVector=${rotVector.joinToString(",")}, " + "\n" +
                "pressure=${pressure.joinToString(",")})"
    }
}

internal data class Attitude(
    var roll: Float = 0f,
    var pitch: Float = 0f,
    var yaw: Float = 0f
) {
    fun isNan(): Boolean {
        return roll.isNaN() || pitch.isNaN() || yaw.isNaN()
    }

    fun toDegree() : Attitude {
        return Attitude(TJLabsUtilFunctions.radian2degree(roll),
            TJLabsUtilFunctions.radian2degree(pitch),
            TJLabsUtilFunctions.radian2degree(yaw))
    }
}

internal data class SensorAxisValue(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var norm: Float = 0f
)


internal enum class SensorPatternType {
    NONE, PEAK, VALLEY
}

internal data class TimeStampFloat(
    var timestamp: Long,
    var valuestamp: Float
)
