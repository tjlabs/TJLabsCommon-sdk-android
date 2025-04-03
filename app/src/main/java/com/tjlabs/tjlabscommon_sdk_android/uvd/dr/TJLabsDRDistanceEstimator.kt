package com.tjlabs.tjlabscommon_sdk_android.uvd.dr

import android.util.Log
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
    private var velocityScale: Float = 1.0f
    private var entranceVelocityScale: Float = 1.0f
    private var preTime: Long = 0L
    private var distance: Float = 0f
    private var preRoll: Float = 0f
    private var prePitch: Float = 0f
    private var isStartRouteTrack: Boolean = false
    private var biasSmoothing = 0f
    private var isPossibleUseBias = false

    fun estimateDistanceInfo(time: Long, sensorData: SensorData): Pair<UnitDistance, Float> {
//        TODO()
//        1. rflow 를 활용한 속도 추정 및 정지 판단
//        2. 가속도 bias 추정
//        3. 진출입로 속도 scaling

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


        var velocityInputScale : Float = (velocityInput*velocityScale*entranceVelocityScale).toFloat()

        if (velocityInputScale < 7) {
            velocityInputScale = 0f
        } else if (velocityInputScale > VELOCITY_MAX) {
            velocityInputScale = VELOCITY_MAX
        }


        val delT = if (preTime == 0L) 1 / sensorFrequency.toFloat() else ((time - preTime) * 1e-3).toFloat()

        if (velocityInputScale.toInt() == 0 && isStartRouteTrack) {
            velocityInputScale = VELOCITY_MIN
        }

        val velocityMps = (velocityInputScale/3.6)*turnScale

        finalUnitResult.isIndexChanged = false
        finalUnitResult.velocity = (velocityMps * 3.6f).toFloat()
        distance += (velocityMps*delT).toFloat()

        if (distance >= 1) {
            index += 1
            finalUnitResult.length = distance
            finalUnitResult.index = index
            finalUnitResult.isIndexChanged = true
            distance = 0f
        }

        preTime = time

        return Pair(finalUnitResult, magNormSmoothingVar)
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

    fun setVelocityScale(scale : Float) {
        velocityScale = scale
    }
}