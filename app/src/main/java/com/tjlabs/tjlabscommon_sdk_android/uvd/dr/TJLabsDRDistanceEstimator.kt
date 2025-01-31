package com.tjlabs.tjlabscommon_sdk_android.uvd.dr

import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calPitchUsingAcc
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calRollUsingAcc
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calVariance
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.exponentialMovingAverage
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.l2Normalize
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.transBody2Nav
import com.tjlabs.tjlabscommon_sdk_android.uvd.Attitude
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import com.tjlabs.tjlabscommon_sdk_android.uvd.UnitDistance
import com.tjlabs.tjlabscommon_sdk_android.uvd.sensorFrequency
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10


const val VELOCITY_MIN: Float = 4f
const val VELOCITY_MAX: Float = 18f
const val RF_SC_THRESHOLD_DR: Float = 0.67f

internal class TJLabsDRDistanceEstimator {
    private var index = 0
    private var finalUnitResult = UnitDistance()
    private var navGyroZSmoothingQueue: MutableList<Float> = mutableListOf()
    private var magNormSmoothingQueue: MutableList<Float> = mutableListOf()
    private var magNormVarQueue: MutableList<Float> = mutableListOf()
    private var velocityQueue: MutableList<Float> = mutableListOf()
    private var preNavGyroZSmoothing: Float = 0f
    private var preMagNormSmoothing: Float = 0f
    private var preMagNormVarSmoothing: Float = 0f
    private var preVelocitySmoothing: Float = 0f
    private var scCompensation = 1.0f
    private var velocityScale: Float = 1.0f
    private var entranceVelocityScale: Float = 1.0f
    private var preTime: Long = 0L
    private var velocityAcc: Float = 0f
    private var distance: Float = 0f
    private var preRoll: Float = 0f
    private var prePitch: Float = 0f
    private var rflow: Float = 0f
    private var rflowForVelocity: Float = 0f
    private var isSufficientRfdBuffer: Boolean = false
    private var isStartRouteTrack: Boolean = false
    private var biasSmoothing = 0f
    private var isPossibleUseBias = false

    fun estimateDistanceInfo(time: Long, sensorData: SensorData): UnitDistance {
        val acc = sensorData.acc
        val gyro = sensorData.gyro
        val mag = sensorData.magRaw

        var accRoll = calRollUsingAcc(acc)
        var accPitch = calPitchUsingAcc(acc)

        if (accRoll.isNaN()){
            accRoll = preRoll
        }else{
            preRoll = accRoll
        }

        if (accPitch.isNaN()) {
            accPitch = prePitch
        } else {
            prePitch = accPitch
        }

        val accAttitude = Attitude(accRoll, accPitch, 0f)
        val accMovingDirection = transBody2Nav(accAttitude, acc)[1]
        val gyroNavZ = abs(transBody2Nav(accAttitude, gyro)[2])
        val magNorm = l2Normalize(mag)

        // ----- Gyro ----- //
        val gyroSmoothingResult = processSmoothing(
            currentValue = gyroNavZ,
            previousSmoothedValue = preNavGyroZSmoothing,
            queue = navGyroZSmoothingQueue,
            smoothingSize = sensorFrequency / 2,
            maxQueueSize = sensorFrequency
        )
        val gyroSmoothing = gyroSmoothingResult.first
        preNavGyroZSmoothing = gyroSmoothingResult.first
        navGyroZSmoothingQueue = gyroSmoothingResult.second
        // --------------- //

        // ----- Mag Norm------ //
        val magNormSmoothingResult = processSmoothing(
            currentValue = magNorm,
            previousSmoothedValue = preMagNormSmoothing,
            queue = magNormSmoothingQueue,
            smoothingSize = 5,
            maxQueueSize = sensorFrequency
        )
        preMagNormSmoothing = magNormSmoothingResult.first
        magNormSmoothingQueue = magNormSmoothingResult.second

        var magNormSmoothingVar = calVariance(magNormSmoothingQueue, magNormSmoothingQueue.average().toFloat())
        if (magNormSmoothingVar > 7) {
            magNormSmoothingVar = 7f
        }

        // ----- Mag Norm Var------ //
        val magNormVarSmoothingResult = processSmoothing(
            currentValue = magNormSmoothingVar,
            previousSmoothedValue = preMagNormVarSmoothing,
            queue = magNormVarQueue,
            smoothingSize = sensorFrequency * 2,
            maxQueueSize = sensorFrequency  * 2
        )

        val magVarSmoothing = magNormVarSmoothingResult.first
        preMagNormVarSmoothing = magNormVarSmoothingResult.first
        magNormVarQueue = magNormVarSmoothingResult.second
        // --------------- //

        val velocityRaw = log10(magVarSmoothing+1) / log10(1.1f)

        // ----- Velocity----- //
        val velocitySmoothingResult = processSmoothing(
            currentValue = velocityRaw,
            previousSmoothedValue = preVelocitySmoothing,
            queue = velocityQueue,
            smoothingSize = sensorFrequency,
            maxQueueSize = sensorFrequency
        )

        val velocitySmoothing = velocitySmoothingResult.first
        preVelocitySmoothing = velocitySmoothingResult.first
        velocityQueue = velocitySmoothingResult.second


        var turnScale = exp(- gyroSmoothing / 2)
        if (turnScale > 0.87) {
            turnScale = 1.0f
        }

        var velocityInput = velocitySmoothing
        if (velocityInput < VELOCITY_MIN) {
            velocityInput = 0f
        } else if (velocityInput > VELOCITY_MAX) {
            velocityInput = VELOCITY_MAX
        }
//        Log.d("VelocityCheck", "velocitySmoothing : $velocitySmoothing")

//        val rflowScale: Float = calRflowVelocityScale(rflowForVelocity, isSufficientRfdVelocityBuffer)

        if (!isStartRouteTrack) {
            entranceVelocityScale = 1.0f
        }

        var velocityInputScale : Float = (velocityInput*velocityScale*entranceVelocityScale).toFloat()

        if (velocityInputScale < 7) { //임시
            velocityInputScale = 0f
//            if (isSufficientRfdBuffer && rflow < 0.5 && !isStartRouteTrack) {
//                velocityInputScale = VELOCITY_MAX * rflowScale
//            }
        } else if (velocityInputScale > VELOCITY_MAX) {
            velocityInputScale = VELOCITY_MAX
        }

        // RFlow Stop Detection
//        if (isSufficientRfdBuffer && rflow >= RF_SC_THRESHOLD_DR) {
//            velocityInputScale = 0f
//        }

        val delT = if (preTime == 0L) 1 / sensorFrequency.toFloat() else ((time - preTime) * 1e-3).toFloat()

        velocityAcc += (accMovingDirection + biasSmoothing) * delT
        velocityAcc = if (velocityAcc < 0) 0f else velocityAcc

        if (velocityInputScale.toInt() == 0 && isStartRouteTrack) {
            velocityInputScale = VELOCITY_MIN
        }

        val velocityMps = (velocityInputScale/3.6)*turnScale

        val velocityCombine = (velocityMps*0.7) + (velocityAcc*0.3)
        val velocityFinal = if (isPossibleUseBias) velocityCombine else velocityMps

        finalUnitResult.isIndexChanged = false
        finalUnitResult.velocity = (velocityFinal * 3.6f).toFloat()
        distance += (velocityMps*delT).toFloat()


        if (distance >= 1) {
            index += 1
            finalUnitResult.length = distance
            finalUnitResult.index = index
            finalUnitResult.isIndexChanged = true
            distance = 0f
        }

//        controlMovingDirectionInfoBuffer(time, index, accMovingDirection, velocityMps.toFloat())

        preTime = time

        return finalUnitResult
    }

    private fun processSmoothing(
        currentValue: Float,
        previousSmoothedValue: Float,
        queue: MutableList<Float>,
        smoothingSize: Int,
        maxQueueSize: Int
    ): Pair<Float, MutableList<Float>> {
        val smoothingValue: Float = if (queue.size == 0) { currentValue
        } else if (queue.size < smoothingSize) {
            exponentialMovingAverage(previousSmoothedValue, currentValue, queue.size)
        } else {
            exponentialMovingAverage(previousSmoothedValue, currentValue, smoothingSize)
        }

        val updatedQueue = updateFloatDataQueue(smoothingValue, queue, maxQueueSize)

        return Pair(smoothingValue, updatedQueue)
    }

    private fun updateFloatDataQueue(data : Float, queue : MutableList<Float>, queueSize : Int) : MutableList<Float> {
        val queueCopy = queue.toMutableList()
        if (queueCopy.size >= queueSize) {
            queueCopy.removeAt(0)
        }

        queueCopy.add(data)
        return queueCopy

    }

    private fun calRflowVelocityScale(rflowForVelocity: Float, isSufficientForVelocity: Boolean) :Float {
        var scale: Float = 1.0f

        if (isSufficientForVelocity) {
            scale = ((-1/(1+exp(10*(-rflowForVelocity+0.66)))) + 1).toFloat()

            if (scale < 0.5) {
                scale = 0.5f
            }
        }

        return scale
    }
}