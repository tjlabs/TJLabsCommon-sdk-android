package com.tjlabs.tjlabscommon_sdk_android.uvd

import android.app.Application
import com.tjlabs.tjlabscommon_sdk_android.uvd.pdr.TJLabsPDRDistanceEstimator

class UVDGenerator(application: Application, private val userId : String = "") {
    interface UVDCallback {
        fun onUvdResult(mode : UserMode, uvd: UserVelocity)

        fun onVelocityResult(kmPh : Float)

        fun onUvdPauseMillis(time : Long)

        fun onUvdError(error : String)
    }

    private val tjLabsSensorManager : TJLabsSensorManager = TJLabsSensorManager(application)
    private var tjLabsPdrDistanceEstimator : TJLabsPDRDistanceEstimator = TJLabsPDRDistanceEstimator()
    private var tjLabsAttitudeEstimator : TJLabsAttitudeEstimator = TJLabsAttitudeEstimator()
    private var tjLabsUnitStatusEstimator = TJLabsUnitStatusEstimator()
    private var uvdGenerationTimeMillis = 0L
    private var userMode = UserMode.MODE_PEDESTRIAN
    private var drVelocityScale = 1f

    fun setUserMode(mode: UserMode) {
        userMode = mode
    }

    fun updateDrVelocityScale(scale : Float) {
        TODO()
    }

    fun generateUvd(defaultPDRStepLength: Float = tjLabsPdrDistanceEstimator.getDefaultStepLength(),
                    minPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMinStepLength(),
                    maxPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMaxStepLength(),
                    callback : UVDCallback) {
        val (isCheckSensorSuccess, msgCheckSensor) = tjLabsSensorManager.checkSensorAvailability()
        if (isCheckSensorSuccess) {
            uvdGenerationTimeMillis = System.currentTimeMillis()
            tjLabsPdrDistanceEstimator.setDefaultStepLength(defaultPDRStepLength)
            tjLabsPdrDistanceEstimator.setMinStepLength(minPDRStepLength)
            tjLabsPdrDistanceEstimator.setMaxStepLength(maxPDRStepLength)
            tjLabsSensorManager.getSensorDataResultOrNull(object : TJLabsSensorManager.SensorResultListener{
                override fun onSensorChangedResult(sensorData: SensorData) {
                    when (userMode) {
                        UserMode.MODE_PEDESTRIAN -> generatePedestrianUvd(sensorData, callback)
                        UserMode.MODE_VEHICLE -> TODO()
                        UserMode.MODE_AUTO -> TODO()
                    }
                }
            })
        } else {
            callback.onUvdError(msgCheckSensor)
        }
    }

    private fun generatePedestrianUvd(sensorData: SensorData, callback: UVDCallback) {
        val pdrUnit = tjLabsPdrDistanceEstimator.estimateDistanceInfo(System.currentTimeMillis(), sensorData)
        val attDegree = tjLabsAttitudeEstimator.estimateAttitudeRadian(System.currentTimeMillis(), sensorData).toDegree()
        val isLookingStatus = tjLabsUnitStatusEstimator.estimateStatus(attDegree, pdrUnit.isIndexChanged)
        if (pdrUnit.isIndexChanged) {
            val index = pdrUnit.index
            val length = pdrUnit.length
            val heading = attDegree.yaw
            callback.onUvdResult(UserMode.MODE_PEDESTRIAN, UserVelocity(userId, System.currentTimeMillis(), index, length, heading, isLookingStatus))
            uvdGenerationTimeMillis = System.currentTimeMillis()
        } else {
            callback.onUvdPauseMillis(System.currentTimeMillis() - uvdGenerationTimeMillis)
        }
        callback.onVelocityResult(zeroVelocityAfterSeconds(pdrUnit.velocity))
    }

    private fun zeroVelocityAfterSeconds(velocity : Float, sec : Int = 2) : Float {
        return if (System.currentTimeMillis() - uvdGenerationTimeMillis < sec * 1000) {
            velocity
        } else {
            0f
        }
    }

    private fun generateVehicleUvd() {
        TODO()
    }

    private fun generateAutoUvd() {
        TODO()
    }

    fun stopUvdGeneration() {
        tjLabsSensorManager.stopSensorChanged()
        tjLabsPdrDistanceEstimator = TJLabsPDRDistanceEstimator()
        tjLabsAttitudeEstimator = TJLabsAttitudeEstimator()
        tjLabsUnitStatusEstimator = TJLabsUnitStatusEstimator()
        uvdGenerationTimeMillis = 0L
        userMode = UserMode.MODE_PEDESTRIAN
        drVelocityScale = 1f
    }
}
