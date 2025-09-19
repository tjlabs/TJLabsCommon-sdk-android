package com.tjlabs.tjlabscommon_sdk_android.uvd.pdr

import android.util.Log
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.exponentialMovingAverage
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.l2Normalize
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorPatternType
import com.tjlabs.tjlabscommon_sdk_android.uvd.TimeStampFloat
import com.tjlabs.tjlabscommon_sdk_android.uvd.UnitDistance
import java.util.LinkedList

private const val NORMAL_STEP_COUNT_SET : Int = 3
private const val MODE_AUTO_NORMAL_STEP_COUNT_SET = 20

internal class TJLabsPDRDistanceEstimator
{
    private val peakValleyDetector = TJLabsPeakValleyDetector()
    private val stepLengthEstimator = TJLabsStepLengthEstimator()
    private var preAccNormEMA = 0f
    private var accNormEMAQueue = LinkedList<TimeStampFloat>()
    private var finalUnitResult = UnitDistance()

    private var accPeakQueue = LinkedList<TimeStampFloat>()
    private var accValleyQueue = LinkedList<TimeStampFloat>()
    private var normalStepLossCheckQueue = LinkedList<Int>()

    private var pastIndexChangedTime = 0L

    private val avgNormAccWindow = 20
    private val accNormEmaQueueSize = 3
    private val accPvQueueSize = 3

    private var normalStepCheckCount = 0
    private var isNormalStep = false

    private var autoMode = false
    private var isModeDrToPdr = false

    fun getDefaultStepLength() : Float {
        return stepLengthEstimator.getDefaultStepLength()
    }

    fun getMinStepLength() : Float {
        return stepLengthEstimator.getMinStepLength()
    }

    fun getMaxStepLength(): Float {
        return stepLengthEstimator.getMaxStepLength()
    }

    fun getNormalStepCountFlag() : Boolean {
        return isNormalStep
    }
    fun setDefaultStepLength(length : Float) {
        stepLengthEstimator.setDefaultStepLength(length)
    }

    fun setMinStepLength(length : Float) {
        stepLengthEstimator.setMinStepLength(length)
    }

    fun setMaxStepLength(length : Float) {
        stepLengthEstimator.setMaxStepLength(length)
    }

    fun setAutoMode(flag : Boolean) {
        autoMode = flag
    }

    fun estimateDistanceInfo(time: Long, sensorData: SensorData): UnitDistance {
        val accNorm = l2Normalize(sensorData.acc)
        val accNormEMA = exponentialMovingAverage(preAccNormEMA, accNorm, avgNormAccWindow)

        preAccNormEMA = accNormEMA

        if (accNormEMAQueue.size < accNormEmaQueueSize) {
            accNormEMAQueue.add(TimeStampFloat(time, accNormEMA))
            return UnitDistance()
        } else {
            accNormEMAQueue.removeAt(0)
            accNormEMAQueue.add(TimeStampFloat(time, accNormEMA))
        }

        val foundAccPV = peakValleyDetector.findPeakValley(accNormEMAQueue)
        updateAccQueue(foundAccPV)

        finalUnitResult.isIndexChanged = false
        if (foundAccPV.type == SensorPatternType.PEAK) {
            normalStepCheckCount = updateNormalStepCheckCount(accPeakQueue, accValleyQueue, normalStepCheckCount)

            if (autoMode) { // Auto Mode 인 경우
                if (isModeDrToPdr) {
                    // DR -> PDR 했으면
                    // 기존 값 사용
                    isNormalStep = checkNormalStep(normalStepCheckCount, NORMAL_STEP_COUNT_SET)
                } else {
                    // PDR -> DR 했으면
                    // normal step 잘 안되게? 값을 크게함
                    isNormalStep = checkNormalStep(normalStepCheckCount, MODE_AUTO_NORMAL_STEP_COUNT_SET)
                }
            } else {
                isNormalStep = checkNormalStep(normalStepCheckCount, NORMAL_STEP_COUNT_SET)
            }

            // normal step 이 판단된다 -> step loss 가 없다.
            // normal step 판단이 안된다. -> step loss 가 있다.

            val isLossStep = !isNormalStep

            if (isNormalStep || finalUnitResult.index <= MODE_AUTO_NORMAL_STEP_COUNT_SET) {
                finalUnitResult.index += 1
                finalUnitResult.isIndexChanged = true

                var diffTime = foundAccPV.timestamp - pastIndexChangedTime
                if (diffTime > 1000) {
                    diffTime = 1000
                }
                pastIndexChangedTime = foundAccPV.timestamp

                // 현재는 step length 추정임
                finalUnitResult.length = stepLengthEstimator.estStepLength(accPeakQueue, accValleyQueue)

                // 속도는 추정한 보폭으로 계산
                var velocityKmph = (finalUnitResult.length / diffTime * 1000) * 3.6f

                // 이후 loss step 보정
                if (!autoMode) {
                    if (isLossStep && finalUnitResult.index > 3) {
                        finalUnitResult.length = 1.8f
                    }
                } else {
                    if (finalUnitResult.index >= MODE_AUTO_NORMAL_STEP_COUNT_SET ){
                        if (isLossStep){
                            if (isModeDrToPdr){
                                finalUnitResult.length = 1.8f
                            }else{
                                finalUnitResult.length = (MODE_AUTO_NORMAL_STEP_COUNT_SET) * 0.6f
                            }
                        }
                    }
                }

                if (velocityKmph >= 5.2) {
                    velocityKmph = 5.2f
                }

                finalUnitResult.velocity = velocityKmph
            }
        }

        return finalUnitResult
    }

    private fun updateAccQueue(pVStruct: TJLabsPeakValleyDetector.PeakValleyStruct) {
        if (pVStruct.type == SensorPatternType.PEAK) {
            updateAccPeakQueue(pVStruct)
        } else if (pVStruct.type == SensorPatternType.VALLEY) {
            updateAccValleyQueue(pVStruct)
        }
    }

    private fun updateAccPeakQueue(pVStruct: TJLabsPeakValleyDetector.PeakValleyStruct) {
        if (accPeakQueue.size >= accPvQueueSize) {
            accPeakQueue.removeAt(0)
        }
        accPeakQueue.add(TimeStampFloat(pVStruct.timestamp, pVStruct.pVValue))
    }

    private fun updateAccValleyQueue(pVStruct: TJLabsPeakValleyDetector.PeakValleyStruct) {
        if (accValleyQueue.size >= accPvQueueSize) {
            accValleyQueue.removeAt(0)
        }
        accValleyQueue.add(TimeStampFloat(pVStruct.timestamp, pVStruct.pVValue))
    }


    private fun updateNormalStepCheckCount(accPeakQueue: LinkedList<TimeStampFloat>,
                                   accValleyQueue: LinkedList<TimeStampFloat>,
                                   normalStepCheckCount: Int): Int {
        if (accPeakQueue.size <= 2 || accValleyQueue.size <= 2)
            return normalStepCheckCount + 1

        if (accPeakQueue.last.timestamp - accPeakQueue[accPeakQueue.size - 2].timestamp < 2000 )
            return normalStepCheckCount + 1
        return 1
    }

    private fun checkNormalStep(normalStepCount: Int, normalStepCountSet: Int = NORMAL_STEP_COUNT_SET): Boolean {
        Log.d("CheckNormalStep", "normalStepCount : $normalStepCount // normalStepCountSet : $normalStepCountSet")
        return normalStepCount >= normalStepCountSet
    }

    fun setModeDrToPdr(isModeDrToPdrInput : Boolean) {
        isModeDrToPdr = isModeDrToPdrInput
        normalStepCheckCount = 0
        normalStepLossCheckQueue = LinkedList<Int>()
    }

}


