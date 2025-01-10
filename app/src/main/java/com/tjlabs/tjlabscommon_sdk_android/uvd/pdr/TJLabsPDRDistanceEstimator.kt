package com.tjlabs.tjlabscommon_sdk_android.uvd.pdr

import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.exponentialMovingAverage
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.l2Normalize
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorPatternType
import com.tjlabs.tjlabscommon_sdk_android.uvd.TimeStampFloat
import com.tjlabs.tjlabscommon_sdk_android.uvd.UnitDistance
import java.util.LinkedList

internal class TJLabsPDRDistanceEstimator
{
    private val peakValleyDetector = TJLabsPeakValleyDetector()
    private val stepLengthEstimator = TJLabsStepLengthEstimator()
    private var preAccNormEMA = 0f
    private var accNormEMAQueue = LinkedList<TimeStampFloat>()
    private var finalUnitResult = UnitDistance()

    private var accPeakQueue = LinkedList<TimeStampFloat>()
    private var accValleyQueue = LinkedList<TimeStampFloat>()
    private var pastIndexChangedTime = 0L

    private val avgNormAccWindow = 20
    private val accNormEmaQueueSize = 3
    private val accPvQueueSize = 3

    fun getDefaultStepLength() : Float {
        return stepLengthEstimator.getDefaultStepLength()
    }

    fun getMinStepLength() : Float {
        return stepLengthEstimator.getMinStepLength()
    }

    fun getMaxStepLength(): Float {
        return stepLengthEstimator.getMaxStepLength()
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

    fun estimateDistanceInfo(time: Long, sensorData: SensorData): UnitDistance {
        val accNorm = l2Normalize(sensorData.acc.toList())
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
                finalUnitResult.index += 1
                finalUnitResult.isIndexChanged = true

                var diffTime = foundAccPV.timestamp - pastIndexChangedTime
                if (diffTime > 1000){
                    diffTime = 1000
                }
                pastIndexChangedTime = foundAccPV.timestamp
                finalUnitResult.length = stepLengthEstimator.estStepLength(accPeakQueue, accValleyQueue)

                var velocityKmph = (finalUnitResult.length / diffTime * 1000) * 3.6f

                if (velocityKmph >= 5.2){
                    velocityKmph = 5.2f
                }

                finalUnitResult.velocity = velocityKmph
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
}


