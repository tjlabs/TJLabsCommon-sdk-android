package com.tjlabs.tjlabscommon_sdk_android.utils

//import com.tjlabs.tjlabscommon_sdk_android.uvd.Attitude
//import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorAxisValue
import com.tjlabs.tjlabscommon_sdk_android.uvd.Attitude
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorAxisValue
import java.util.ArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object TJLabsUtilFunctions{
    fun frequency2Millis(frequency : Int) : Int {
        return 1000 / frequency
    }

    fun millis2nanos(millis : Long) : Long {
        return millis * 1000 * 1000
    }

    fun nanos2millis(nanos : Long) : Long {
        return nanos / (1000 * 1000)
    }

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

    fun compensateDegree(degree : Float) : Float {
        var remainderHeading = degree % 360
        if (remainderHeading < 0)
            remainderHeading += 360
        return remainderHeading
    }

    fun circularStandardDeviation(array: List<Float>): Float {
        if (array.isEmpty()) {
            return 20.0f
        }
        val meanAngle = circularMean(array)

        val circularDifferences = array.map { angleDifference(it, meanAngle) }
        val circularVariance = circularSumByDouble(circularDifferences) / circularDifferences.size
        return sqrt(circularVariance)
    }

    private fun angleDifference(a1: Float, a2: Float): Float {
        val diff = abs(a1 - a2)
        return if (diff <= 180.0) diff else 360.0f - diff
    }

    private fun circularSumByDouble(array:List<Float>) : Float{
        var sum = 0f
        for (angle in array){
            sum += angle * angle
        }
        return sum
    }

    private fun circularMean(array: List<Float>): Float {
        if (array.isEmpty()) {
            return 0.0f
        }
        var sinSum = 0.0
        var cosSum = 0.0
        for (angle in array) {
            sinSum += sin(angle * Math.PI / 180.0)
            cosSum += cos(angle * Math.PI / 180.0)
        }
        val meanSin = sinSum / array.size.toFloat()
        val meanCos = cosSum / array.size.toFloat()
        val meanAngle =(atan2(meanSin, meanCos) * 180.0 / Math.PI).toFloat()
        return if (meanAngle < 0) meanAngle + 360.0f else meanAngle
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

    internal fun getOrientation(rotationMatrix: Array<Array<Float>>): FloatArray {
        val orientation = FloatArray(3) { 0f }
        orientation[0] = atan2(rotationMatrix[0][1], rotationMatrix[1][1])
        orientation[1] = asin(-rotationMatrix[2][1])
        orientation[2] = atan2(-rotationMatrix[2][0], rotationMatrix[2][2])
        return orientation
    }

    internal fun getRotationMatrixFromVector(rotationVector: FloatArray, returnSize: Int): Array<Array<Float>> {
        val rotationMatrix = Array(4) { Array(4) { 0f } }

        val q1 = rotationVector[0]
        val q2 = rotationVector[1]
        val q3 = rotationVector[2]
        val q0 = rotationVector[3]

        val sqq1 = 2 * q1 * q1
        val sqq2 = 2 * q2 * q2
        val sqq3 = 2 * q3 * q3
        val q1q2 = 2 * q1 * q2
        val q3q0 = 2 * q3 * q0
        val q1q3 = 2 * q1 * q3
        val q2q0 = 2 * q2 * q0
        val q2q3 = 2 * q2 * q3
        val q1q0 = 2 * q1 * q0

        if (returnSize == 16) {
            rotationMatrix[0][0] = 1 - sqq2 - sqq3
            rotationMatrix[0][1] = q1q2 - q3q0
            rotationMatrix[0][2] = q1q3 + q2q0

            rotationMatrix[1][0] = q1q2 + q3q0
            rotationMatrix[1][1] = 1 - sqq1 - sqq3
            rotationMatrix[1][2] = q2q3 - q1q0

            rotationMatrix[2][0] = q1q3 - q2q0
            rotationMatrix[2][1] = q2q3 + q1q0
            rotationMatrix[2][2] = 1 - sqq1 - sqq2

            rotationMatrix[3][3] = 1f

        } else if (returnSize == 9) {
            rotationMatrix[0][0] = 1 - sqq2 - sqq3
            rotationMatrix[0][1] = q1q2 - q3q0
            rotationMatrix[0][2] = q1q3 + q2q0

            rotationMatrix[1][0] = q1q2 + q3q0
            rotationMatrix[1][1] = 1 - sqq1 - sqq3
            rotationMatrix[1][2] = q2q3 - q1q0

            rotationMatrix[2][0] = q1q3 - q2q0
            rotationMatrix[2][1] = q2q3 + q1q0
            rotationMatrix[2][2] = 1 - sqq1 - sqq2
        }

        return rotationMatrix
    }

    internal fun l2Normalize(originalVector: List<Float>): Float {
        val squaredVector = originalVector.map { it.pow(2) }
        return sqrt(squaredVector.sum())
    }

    internal fun calAngleOfRotation(timeInterval: Long, angularVelocity: Float): Float {
        return angularVelocity * timeInterval * 1e-3F
    }

    internal fun calRollUsingAcc(acc: FloatArray) : Float{
        return if (acc[0] > 0 && acc[2] < 0){
            (atan(acc[0] / sqrt(acc[1].pow(2) + acc[2].pow(2))) - PI).toFloat()
        }
        else if (acc[2] < 0 && acc[0] < 0) {
            (atan(acc[0] / sqrt(acc[1].pow(2) + acc[2].pow(2))) + PI).toFloat()
        }
        else{
            -atan(acc[0] / sqrt(acc[1].pow(2) + acc[2].pow(2)))
        }
    }

    internal fun calPitchUsingAcc(acc: FloatArray) : Float{
        return atan(acc[1] / sqrt(acc[0].pow(2) + acc[2].pow(2)))
    }

    internal fun calAttitudeUsingGameVector(gameVec: FloatArray): Attitude {
        val rotationMatrix = getRotationMatrixFromVector(gameVec, 9)
        val vecOrientation = getOrientation(rotationMatrix)
        return Attitude(vecOrientation[2], -vecOrientation[1], -vecOrientation[0])
    }

    internal fun transBody2Nav(att: Attitude, data: FloatArray): FloatArray {
        return rotationXY(-att.roll, -att.pitch, data)
    }

    private fun rotationXY(roll: Float, pitch: Float, gyro: FloatArray): FloatArray {
        val rotationMatrix = Array(3) { Array(3) { 0f } }
        val processedGyro = FloatArray(3)

        val gx = gyro[0]
        val gy = gyro[1]
        val gz = gyro[2]

        rotationMatrix[0][0] = cos(roll)
        rotationMatrix[0][1] = 0f
        rotationMatrix[0][2] = -sin(roll)

        rotationMatrix[1][0] = sin(roll) * sin(pitch)
        rotationMatrix[1][1] = 0f
        rotationMatrix[1][2] = cos(roll) * sin(pitch)

        rotationMatrix[2][0] = cos(pitch) * sin(roll)
        rotationMatrix[2][1] = -sin(pitch)
        rotationMatrix[2][2] = cos(pitch) * cos(roll)

        processedGyro[0] = (gx * rotationMatrix[0][0]) + (gy * rotationMatrix[0][1]) + (gz * rotationMatrix[0][2])
        processedGyro[1] = (gx * rotationMatrix[1][0]) + (gy * rotationMatrix[1][1]) + (gz * rotationMatrix[1][2])
        processedGyro[2] = (gx * rotationMatrix[2][0]) + (gy * rotationMatrix[2][1]) + (gz * rotationMatrix[2][2])

        return processedGyro
    }
}
