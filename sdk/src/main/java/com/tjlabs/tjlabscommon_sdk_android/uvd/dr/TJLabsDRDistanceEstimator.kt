package com.tjlabs.tjlabscommon_sdk_android.uvd.dr

import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsCommonLog
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calPitchUsingAcc
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calRMS
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calRollUsingAcc
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.calVariance
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.exponentialMovingAverage
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.l2Normalize
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.transBody2Nav
import com.tjlabs.tjlabscommon_sdk_android.uvd.Attitude
import com.tjlabs.tjlabscommon_sdk_android.uvd.DrState
import com.tjlabs.tjlabscommon_sdk_android.uvd.RmsStopThresholdUpdateType
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import com.tjlabs.tjlabscommon_sdk_android.uvd.UnitDistance
import com.tjlabs.tjlabscommon_sdk_android.uvd.sensorFrequency
import java.lang.Float.min
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10


const val VELOCITY_MIN: Float = 4f
const val VELOCITY_MAX: Float = 18f
const val RF_SC_THRESHOLD_DR: Float = 0.67f
const val ACC_VAR_STOP_THRESHOLD: Float = 0.005f
const val GYRO_VAR_STOP_THRESHOLD: Float = 0.001f
const val ACC_STOP_MAX: Float = 0.20f
const val GYRO_STOP_MAX: Float = 0.05f
const val STOP_STATE_WINDOW : Int = 40

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

    private var rflow: Float = 0f
    private var rflowForVelocity: Float = 0f
    private var rflowForAutoMode: Float = 0f
    private var isSufficientRfdBuffer: Boolean = false
    private var isSufficientRfdVelocityBuffer: Boolean = false
    private var isSufficientRfdAutoModeBuffer: Boolean = false

    private var accNormBuffer: MutableList<Float> = mutableListOf()
    private var gyroNormBuffer: MutableList<Float> = mutableListOf()
    private var drStateBuffer: MutableList<DrState> = mutableListOf()
    private var ACC_STOP_THRESHOLD: Float = 0.12f
    private var GYRO_STOP_THRESHOLD: Float = 0.03f

    private var count = 0

    fun estimateDistanceInfo(dtime: Long?, sensorData: SensorData): Pair<UnitDistance, Float> {
        val acc = sensorData.acc
        val gyro = sensorData.gyro

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


        val accNorm = l2Normalize(sensorData.acc)
        val gyroNorm = l2Normalize(sensorData.gyro)
        val magNorm = l2Normalize(sensorData.magRaw)

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

        //정지 판단을 위한 버퍼 업데이트
        updateAccNormBuffer(value = accNorm - 9.8f) //중력가속도 값을 빼줌
        updateGyroNormBuffer(value = gyroNorm)

        val accRMS = calRMS(accNormBuffer)
        val gyroRMS = calRMS(gyroNormBuffer)
        val accVar = calVariance(accNormBuffer, accNormBuffer.average().toFloat())
        val gyroVar = calVariance(gyroNormBuffer, gyroNormBuffer.average().toFloat())

        if (accRMS > ACC_STOP_THRESHOLD && accVar <= ACC_VAR_STOP_THRESHOLD && gyroVar <= GYRO_VAR_STOP_THRESHOLD) {
            val preValue = ACC_STOP_THRESHOLD
            val newValue = (accRMS + preValue) * 0.5f
            setRmsStopThreshold(RmsStopThresholdUpdateType.ACC, newValue)
        }
        if (gyroRMS > GYRO_STOP_THRESHOLD && accVar <= ACC_VAR_STOP_THRESHOLD && gyroVar <= GYRO_VAR_STOP_THRESHOLD) {
            val preValue = GYRO_STOP_THRESHOLD
            val newValue = (gyroRMS + preValue) * 0.5f
            setRmsStopThreshold(RmsStopThresholdUpdateType.GYRO, newValue)
        }

        val temporalDrState: DrState =
            if (accRMS <= ACC_STOP_THRESHOLD && gyroRMS <= GYRO_STOP_THRESHOLD && accVar <= ACC_VAR_STOP_THRESHOLD && gyroVar <= GYRO_VAR_STOP_THRESHOLD) {
                DrState.STOP
            } else {
                DrState.MOVE
            }

        updateDrStateBuffer(temporalDrState)
        val drState = determineDrState(drStateBuffer)

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

        val rflowScale: Float = calRflowVelocityScale(rflowForVelocity, isSufficientRfdVelocityBuffer)

        if (!isStartRouteTrack) {
            entranceVelocityScale = 1.0f
        }

        var velocityInputScale : Float = (velocityInput*velocityScale*entranceVelocityScale)
        if (velocityInputScale < VELOCITY_MIN) {
            velocityInputScale = 0f
            if (isSufficientRfdBuffer && rflow < 0.4) {
                velocityInputScale = VELOCITY_MAX * rflowScale
            }
        } else if (velocityInputScale > VELOCITY_MAX) {
            velocityInputScale = VELOCITY_MAX
        }

        // RFlow Stop Detection
        if (isSufficientRfdBuffer && rflow >= RF_SC_THRESHOLD_DR) {
            velocityInputScale = 0f
        }


        if (velocityInputScale.toInt() == 0 && isStartRouteTrack) {
            velocityInputScale = VELOCITY_MIN
        }

        if (velocityInputScale != 0f && drState == DrState.STOP) {
            velocityInputScale = 0f
        }
        val delT = if (dtime == null) 1 / sensorFrequency.toFloat() else ((dtime) * 1e-3).toFloat()
        val velocityMps = (velocityInputScale/3.6)*turnScale
        finalUnitResult.isIndexChanged = false
        finalUnitResult.velocity = (velocityMps * 3.6f).toFloat()
        distance += (velocityMps*delT).toFloat()

        count ++
        if (count > 10) {
            TJLabsCommonLog.d("CheckVelocity", "final vel : ${finalUnitResult.velocity} // raw velocity smooth : $velocitySmoothing // scale : ${velocityScale} // ent scale : $entranceVelocityScale //  turn scale : $turnScale // rflow : $rflowScale // isSufficientRfdBuffer : $isSufficientRfdBuffer // drState : $drState")
            count = 0
        }

        if (distance >= 1) {
            index += 1
            finalUnitResult.length = distance
            finalUnitResult.index = index
            finalUnitResult.isIndexChanged = true
            distance = 0f
        }

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

    private fun updateAccNormBuffer(value: Float) {
        if (accNormBuffer.size >= STOP_STATE_WINDOW) {
            accNormBuffer.removeAt(0)
        }
        accNormBuffer.add(value)
    }

    private fun updateGyroNormBuffer(value: Float) {
        if (gyroNormBuffer.size >= STOP_STATE_WINDOW) {
            gyroNormBuffer.removeAt(0)
        }
        gyroNormBuffer.add(value)
    }


    fun setVelocityScale(scale : Float) {
        velocityScale = scale
    }

    fun setRFlow(rflow: Float, rflowForVelocity: Float, rflowForAutoMode: Float, isSufficient: Boolean, isSufficientForVelocity: Boolean, isSufficientForAutoMode: Boolean) {
        this.rflow = rflow
        this.rflowForVelocity = rflowForVelocity
        this.rflowForAutoMode = rflowForAutoMode

        isSufficientRfdBuffer = isSufficient
        isSufficientRfdVelocityBuffer = isSufficientForVelocity
        isSufficientRfdAutoModeBuffer = isSufficientForAutoMode
    }

    fun setIsStartRouteTrack(flag : Boolean) {
        isStartRouteTrack = flag
    }


    private fun setRmsStopThreshold(type: RmsStopThresholdUpdateType, value: Float) {
        if (type == RmsStopThresholdUpdateType.ACC) {
            ACC_STOP_THRESHOLD = min(value, ACC_STOP_MAX)
        } else if (type == RmsStopThresholdUpdateType.GYRO) {
            GYRO_STOP_THRESHOLD = min(value, GYRO_STOP_MAX)
        }
    }

    private fun updateDrStateBuffer(value: DrState) {
        if (drStateBuffer.size >= STOP_STATE_WINDOW) {
            drStateBuffer.removeAt(0)
        }
        drStateBuffer.add(value)
    }

    private fun determineDrState(drStateBuffer: List<DrState>): DrState {
        val windowSize = drStateBuffer.size
        if (windowSize < 10) return DrState.UNKNOWN

        val stopCount = drStateBuffer.count { it == DrState.STOP }
        val stopRatio = stopCount.toFloat() / windowSize.toFloat()

        // 버퍼 내에서 가장 긴 연속 STOP 길이 계산
        var maxConsecutiveStop = 0
        var currentConsecutive = 0
        for (state in drStateBuffer) {
            if (state == DrState.STOP) {
                currentConsecutive++
                maxConsecutiveStop = maxOf(maxConsecutiveStop, currentConsecutive)
            } else {
                currentConsecutive = 0
            }
        }

        // 최근 5개가 모두 STOP인지 확인
        val last5 = drStateBuffer.takeLast(5)
        val allLast5Stop = last5.all { it == DrState.STOP }

        return if (stopRatio >= 0.7f && maxConsecutiveStop >= 10 && allLast5Stop) {
            DrState.STOP
        } else {
            DrState.MOVE
        }
    }

}
