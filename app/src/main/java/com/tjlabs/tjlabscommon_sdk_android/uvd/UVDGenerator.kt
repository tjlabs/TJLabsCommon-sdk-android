package com.tjlabs.tjlabscommon_sdk_android.uvd

import android.app.Application
import com.tjlabs.tjlabscommon_sdk_android.uvd.dr.TJLabsDRDistanceEstimator
import com.tjlabs.tjlabscommon_sdk_android.uvd.pdr.TJLabsPDRDistanceEstimator


const val sensorFrequency = 40

class UVDGenerator(application: Application, private val userId : String = "") {
    interface UVDCallback {
        fun onUvdResult(mode : UserMode, uvd: UserVelocity)

        fun onPressureResult(hPa : Float)

        fun onVelocityResult(kmPh : Float)

        fun onMagNormSmoothingVarResult(value : Float)

        fun onUvdPauseMillis(time : Long)

        fun onUvdError(error : String)
    }
    private val tjLabsSensorManager : TJLabsSensorManager = TJLabsSensorManager(application,sensorFrequency)
    private var tjLabsAttitudeEstimator : TJLabsAttitudeEstimator = TJLabsAttitudeEstimator(sensorFrequency)
    private var tjLabsPdrDistanceEstimator : TJLabsPDRDistanceEstimator = TJLabsPDRDistanceEstimator()
    private var tjLabsDrDistanceEstimator : TJLabsDRDistanceEstimator = TJLabsDRDistanceEstimator()
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

    fun checkIsAvailableUvd(callback : UVDCallback, completion : (Boolean) -> Unit) {
        val (isCheckSensorSuccess, msgCheckSensor) = tjLabsSensorManager.checkSensorAvailability()
        if (isCheckSensorSuccess) {
            completion(true)
        } else {
            completion(false)
            callback.onUvdError(msgCheckSensor)
        }
    }

    fun generateUvd(defaultPDRStepLength: Float = tjLabsPdrDistanceEstimator.getDefaultStepLength(),
                    minPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMinStepLength(),
                    maxPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMaxStepLength(),
                    callback : UVDCallback) {

        uvdGenerationTimeMillis = System.currentTimeMillis()
        tjLabsPdrDistanceEstimator.setDefaultStepLength(defaultPDRStepLength)
        tjLabsPdrDistanceEstimator.setMinStepLength(minPDRStepLength)
        tjLabsPdrDistanceEstimator.setMaxStepLength(maxPDRStepLength)

        tjLabsSensorManager.getSensorDataResultOrNull(object : TJLabsSensorManager.SensorResultListener{
            override fun onSensorChangedResult(sensorData: SensorData) {
                when (userMode) {
                    UserMode.MODE_PEDESTRIAN -> generatePedestrianUvd(sensorData, callback)
                    UserMode.MODE_VEHICLE -> generateVehicleUvd(sensorData, callback)
                    UserMode.MODE_AUTO -> TODO()
                }
            }
        })
    }

    private fun resetVelocityAfterSeconds(velocity : Float, sec : Int = 2) : Float {
        return if (System.currentTimeMillis() - uvdGenerationTimeMillis < sec * 1000) {
            velocity
        } else {
            0f
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
        callback.onPressureResult(sensorData.pressure[0])
        callback.onVelocityResult(resetVelocityAfterSeconds(pdrUnit.velocity))
    }

    private fun generateVehicleUvd(sensorData: SensorData, callback: UVDCallback) {
        val (drUnit, magNormSmoothingVariance) = tjLabsDrDistanceEstimator.estimateDistanceInfo(System.currentTimeMillis(), sensorData)
        val attDegree = tjLabsAttitudeEstimator.estimateAttitudeRadian(System.currentTimeMillis(), sensorData).toDegree()
        //TODO() 자석 거치 상황인지 확인
        //TODO() calAccBias?
        if (drUnit.isIndexChanged) {
            val index = drUnit.index
            val length = drUnit.length
            val heading = attDegree.yaw
            callback.onUvdResult(UserMode.MODE_VEHICLE, UserVelocity(userId, System.currentTimeMillis(), index, length, heading, true))
            uvdGenerationTimeMillis = System.currentTimeMillis()
        } else {
            callback.onUvdPauseMillis(System.currentTimeMillis() - uvdGenerationTimeMillis)
        }
        callback.onPressureResult(sensorData.pressure[0])
        callback.onVelocityResult(resetVelocityAfterSeconds(drUnit.velocity))
        callback.onMagNormSmoothingVarResult(magNormSmoothingVariance)
    }

    private fun generateAutoUvd() {
        TODO()
    }

    fun stopUvdGeneration() {
        tjLabsSensorManager.stopSensorChanged()
        tjLabsPdrDistanceEstimator = TJLabsPDRDistanceEstimator()
        tjLabsAttitudeEstimator = TJLabsAttitudeEstimator(sensorFrequency)
        tjLabsUnitStatusEstimator = TJLabsUnitStatusEstimator()
        uvdGenerationTimeMillis = 0L
        drVelocityScale = 1f
    }
}
