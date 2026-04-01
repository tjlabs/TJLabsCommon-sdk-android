package com.tjlabs.tjlabscommon_sdk_android.uvd.pdr

import com.tjlabs.tjlabscommon_sdk_android.uvd.TimeStampFloat
import java.util.LinkedList

internal class TJLabsStepLengthEstimator
{
    private var defaultStepLength: Float = 0.6f
    private var minStepLength: Float = 0.5f
    private var maxStepLength: Float = 0.7f

    private var preStepLength = defaultStepLength
    private var alpha: Float = 0.45f
    private var differencePvStandard: Float = 0.83f
    private var midStepLength: Float = 0.5f
    private var minDifferencePv: Float = 0.2f
    private var compensationWeight: Float = 0.85f
    private var compensationBias: Float = 0.1f
    private var differencePvThreshold: Float = (midStepLength - defaultStepLength) / alpha + differencePvStandard

    fun getDefaultStepLength() : Float {
        return defaultStepLength
    }

    fun getMinStepLength() : Float {
        return minStepLength
    }

    fun getMaxStepLength() : Float {
        return maxStepLength
    }

    fun setDefaultStepLength(length : Float) {
        defaultStepLength = length
    }

    fun setMinStepLength(length : Float) {
        minStepLength = length
    }

    fun setMaxStepLength(length : Float) {
        maxStepLength = length
    }

    fun estStepLength(accPeakQueue: LinkedList<TimeStampFloat>, accValleyQueue: LinkedList<TimeStampFloat>): Float {
        if (accPeakQueue.size < 1 || accValleyQueue.size < 1) {
            return defaultStepLength
        }
        val differencePV = accPeakQueue.last.valuestamp - accValleyQueue.last.valuestamp
        var stepLength = if (differencePV > differencePvThreshold) {
            calLongStepLength(differencePV)
        } else {
            calShortStepLength(differencePV)
        }
        stepLength = compensateStepLength(stepLength)
        return limitStepLength(stepLength)
    }

    private fun calLongStepLength(differencePV: Float): Float {
        return (alpha * (differencePV - differencePvStandard) + defaultStepLength)
    }

    private fun calShortStepLength(differencePV: Float): Float {
        return ((midStepLength - minStepLength) / (differencePvThreshold - minDifferencePv)) * (differencePV - differencePvThreshold) + midStepLength
    }

    private fun compensateStepLength(curStepLength: Float): Float {
        val compensateStepLength =
            compensationWeight * (curStepLength) - (curStepLength - preStepLength) * (1 - compensationWeight) + compensationBias
        preStepLength = compensateStepLength
        return compensateStepLength
    }

    private fun limitStepLength(stepLength: Float): Float {
        return when {
            stepLength > maxStepLength -> maxStepLength
            stepLength < minStepLength -> minStepLength
            else -> stepLength
        }
    }

}


