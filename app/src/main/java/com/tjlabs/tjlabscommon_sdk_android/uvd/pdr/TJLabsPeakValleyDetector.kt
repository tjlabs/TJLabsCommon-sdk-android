package com.tjlabs.tjlabscommon_sdk_android.uvd.pdr

import com.tjlabs.tjlabscommon_sdk_android.uvd.TimeStampFloat
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorPatternType
import java.util.LinkedList

internal class TJLabsPeakValleyDetector(private val amplitudeThreshold: Float = 0.18f,
                                        private val timeThreshold: Float = 100.0f) {

    private var lastPeakValley: PeakValleyStruct = PeakValleyStruct(SensorPatternType.PEAK, Long.MAX_VALUE, Float.NEGATIVE_INFINITY)

    data class PeakValleyStruct(var type: SensorPatternType = SensorPatternType.NONE, var timestamp: Long = 0, var pVValue: Float = 0.0f ) {
        fun updatePeakValley(localPeakValley: PeakValleyStruct) {
            if (type == SensorPatternType.PEAK && localPeakValley.type == SensorPatternType.PEAK) {
                updatePeakIfBigger(localPeakValley)
            } else if (type == SensorPatternType.VALLEY && localPeakValley.type == SensorPatternType.VALLEY) {
                updateValleyIfSmaller(localPeakValley)
            }
        }

        private fun updatePeakIfBigger(localPeak: PeakValleyStruct) {
            if (localPeak.pVValue > pVValue) {
                timestamp = localPeak.timestamp
                pVValue = localPeak.pVValue
            }
        }

        private fun updateValleyIfSmaller(localValley: PeakValleyStruct) {
            if (localValley.pVValue < pVValue) {
                timestamp = localValley.timestamp
                pVValue = localValley.pVValue
            }
        }
    }

    fun findPeakValley(smoothingNormAcc: LinkedList<TimeStampFloat>): PeakValleyStruct {
        val localPeakValley = findLocalPeakValley(smoothingNormAcc)
        val foundGlobalPeakValley = findGlobalPeakValley(localPeakValley)
        lastPeakValley.updatePeakValley(localPeakValley)
        return foundGlobalPeakValley
    }
    
    private fun findLocalPeakValley(queue: LinkedList<TimeStampFloat>): PeakValleyStruct {
        when {
            isLocalPeak(queue) -> {
                return PeakValleyStruct(
                    SensorPatternType.PEAK,
                    queue[1].timestamp,
                    queue[1].valuestamp
                )
            }
            isLocalValley(queue) -> {
                return PeakValleyStruct(
                    SensorPatternType.VALLEY,
                    queue[1].timestamp,
                    queue[1].valuestamp
                )
            }
            else -> {
                return PeakValleyStruct()
            }
        }
    }

    private fun isLocalPeak(data: LinkedList<TimeStampFloat>): Boolean {
        return (data[0].valuestamp < data[1].valuestamp) and (data[1].valuestamp >= data[2].valuestamp)
    }

    private fun isLocalValley(data: LinkedList<TimeStampFloat>): Boolean {
        return (data[0].valuestamp > data[1].valuestamp) and (data[1].valuestamp <= data[2].valuestamp)
    }

    private fun findGlobalPeakValley(localPeakValley: PeakValleyStruct): PeakValleyStruct {
        var foundPeakValley = PeakValleyStruct()
        if (lastPeakValley.type == SensorPatternType.PEAK && localPeakValley.type == SensorPatternType.VALLEY) {
            if (isGlobalPeak(lastPeakValley, localPeakValley)) {
                foundPeakValley = lastPeakValley
                lastPeakValley = localPeakValley
            }
        } else if (lastPeakValley.type == SensorPatternType.VALLEY && localPeakValley.type == SensorPatternType.PEAK) {
            if (isGlobalValley(lastPeakValley, localPeakValley)) {
                foundPeakValley = lastPeakValley
                lastPeakValley = localPeakValley
            }
        }
        return foundPeakValley
    }

    private fun isGlobalPeak(lastPeak: PeakValleyStruct, localValley: PeakValleyStruct): Boolean {
        return (lastPeak.pVValue - localValley.pVValue) > amplitudeThreshold &&
                (localValley.timestamp - lastPeak.timestamp) > timeThreshold
    }

    private fun isGlobalValley(lastValley: PeakValleyStruct, localPeak: PeakValleyStruct, ): Boolean {
        return (localPeak.pVValue - lastValley.pVValue) > amplitudeThreshold &&
                (localPeak.timestamp - lastValley.timestamp) > timeThreshold
    }


}

