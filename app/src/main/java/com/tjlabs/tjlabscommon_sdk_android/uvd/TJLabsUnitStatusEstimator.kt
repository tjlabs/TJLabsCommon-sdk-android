package com.tjlabs.tjlabscommon_sdk_android.uvd

import java.util.*
import kotlin.math.absoluteValue

internal class TJLabsUnitStatusEstimator {
    private var lookingFlagStepQueue = LinkedList<Boolean>()
    private val lookingFlagCheckIndexSize: Int = 3

    fun estimateStatus(attDegree: Attitude, isIndexChanged : Boolean) : Boolean {
        return if (isIndexChanged) {
            val isLookingAttitude =
                (attDegree.roll.absoluteValue < 25f && attDegree.pitch >  -20f &&
                        attDegree.pitch < 80f)

            updateIsLookingAttitudeQueue(lookingFlagStepQueue, isLookingAttitude)
            checkLookingAttitude(lookingFlagStepQueue)
        } else {
            false
        }
    }

    private fun checkLookingAttitude(lookingFlagStepQueue: LinkedList<Boolean>): Boolean {
        return if (lookingFlagStepQueue.size < lookingFlagCheckIndexSize)
            true
        else {
            var bufferSum = 0
            lookingFlagStepQueue.forEach { isLooking ->
                if (isLooking)
                    bufferSum += 1
            }
            bufferSum >= lookingFlagCheckIndexSize - 1
        }
    }

    private fun updateIsLookingAttitudeQueue(
        lookingFlagStepQueue: LinkedList<Boolean>,
        lookingFlag: Boolean
    ) {
        if (lookingFlagStepQueue.size >= lookingFlagCheckIndexSize) {
            lookingFlagStepQueue.removeAt(0)
        }
        if (lookingFlag)
            lookingFlagStepQueue.add(true)
        else {
            lookingFlagStepQueue.add(false)
        }
    }
}