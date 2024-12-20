package com.tjlabs.tjlabscommon_sdk_android.utils

import com.tjlabs.tjlabscommon_sdk_android.uvd.Attitude
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorAxisValue
import java.util.ArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object TJLabsUtilFunctions{
    fun getCurrentTimeInMilliseconds() : Long{
        return System.currentTimeMillis()
    }

    fun exponentialMovingAverage(preEMA: Float, curValue: Float, windowSize: Int): Float {
        return preEMA * (windowSize - 1) / windowSize + curValue / windowSize //avg_data
    }

    fun degree2radian(degree: Float): Float {
        return degree * PI.toFloat() / 180
    }

    fun radian2degree(radian: Float): Float {
        return radian * 180 / PI.toFloat()
    }

    internal fun removeLevelDirectionString(levelName : String) : String {
        var currentLevelName = levelName
        if (currentLevelName.isNotEmpty()) {
            if (currentLevelName[currentLevelName.lastIndex].toString() == "D") {
                currentLevelName = currentLevelName.replace("_D", "")
            }
        }
        return currentLevelName
    }

    internal fun movingAverage(preAvgValue: Float, curValue: Float, windowSize: Int): Float {
        val windowSizeFloat = windowSize.toFloat()
        return preAvgValue * ((windowSizeFloat - 1) / windowSizeFloat) + (curValue / windowSizeFloat)
    }

    internal fun compensateDegree(degree : Float) : Float {
        var remainderHeading = degree % 360
        if (remainderHeading < 0)
            remainderHeading += 360
        return remainderHeading
    }

    internal fun weightedAverageDegree(degreeA: Float, degreeB: Float, weightA: Float, weightB: Float): Float {
        val radianA = degree2radian(compensateDegree(degreeA))
        val radianB = degree2radian(compensateDegree(degreeB))

        // Compute the weighted components
        val x = weightA * cos(radianA) + weightB * cos(radianB)
        val y = weightA * sin(radianA) + weightB * sin(radianB)

        return compensateDegree(radian2degree(atan2(y, x)))
    }

    internal fun determineClosestDirectionOrNull(directionPair: Pair<Float, Float>): String? {
        // Normalize angles to be within 0 to 360 degrees
        val normalizedAngles = Pair(
            compensateDegree(directionPair.first),
            compensateDegree(directionPair.second)
        )
        // Define the target directions
        val directions = mapOf("hor" to listOf(0.0f, 180.0f),"ver" to listOf(90.0f, 270.0f))

        for ((directionName, referenceAngles) in directions) {
            val isBothClose = referenceAngles.any { refAngle1 ->
                calDegreeDeference(normalizedAngles.first, refAngle1) <= 40
            } && referenceAngles.any { refAngle2 ->
                calDegreeDeference(normalizedAngles.second, refAngle2) <= 40
            }
            if (isBothClose) {
                return directionName
            }
        }
        return null
    }


    internal fun calDegreeDeference(degreeA: Float, degree2: Float): Float {
        val diff = abs(degreeA - degree2)
        return diff.coerceAtMost(360 - diff)
    }

    internal fun calAttEMA(preAttEMA: Attitude, curATT: Attitude, windowSize: Int): Attitude {
        return Attitude(
            exponentialMovingAverage(preAttEMA.roll, curATT.roll, windowSize),
            exponentialMovingAverage(preAttEMA.pitch, curATT.pitch, windowSize),
            exponentialMovingAverage(preAttEMA.yaw, curATT.yaw, windowSize)
        )
    }

    internal fun calSensorAxisEMA(preArrayEMA: SensorAxisValue, curArray: SensorAxisValue, windowSize: Int): SensorAxisValue {
        return SensorAxisValue(
            exponentialMovingAverage(preArrayEMA.x, curArray.x, windowSize),
            exponentialMovingAverage(preArrayEMA.y, curArray.y, windowSize),
            exponentialMovingAverage(preArrayEMA.z, curArray.z, windowSize),
            exponentialMovingAverage(preArrayEMA.norm, curArray.norm, windowSize)
        )

    }

    internal fun l2Normalize(originalVector: List<Float>): Float {
        val squaredVector = originalVector.map { it.pow(2) }
        return sqrt(squaredVector.sum())
    }
}
