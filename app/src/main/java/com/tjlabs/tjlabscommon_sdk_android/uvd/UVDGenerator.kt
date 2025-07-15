package com.tjlabs.tjlabscommon_sdk_android.uvd

import android.app.Application
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.convertToSensorData
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.saveDataFunction
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.sensorMutableList
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.sensorSimulationIndex
import com.tjlabs.tjlabscommon_sdk_android.uvd.dr.TJLabsDRDistanceEstimator
import com.tjlabs.tjlabscommon_sdk_android.uvd.pdr.TJLabsPDRDistanceEstimator


const val sensorFrequency = 40

class UVDGenerator(private val application: Application, private val userId : String = "") {
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

    private var preTime = 0L
    fun setUserMode(mode: UserMode) {
        userMode = mode
    }

    fun updateDrVelocityScale(scale : Float) {
        tjLabsDrDistanceEstimator.setVelocityScale(scale)
    }

    fun checkIsAvailableUvd(callback : UVDCallback, completion : (Boolean, String) -> Unit) {
        val (isCheckSensorSuccess, msgCheckSensor) = tjLabsSensorManager.checkSensorAvailability()
        if (isCheckSensorSuccess) {
            completion(true, msgCheckSensor)
        } else {
            completion(false, msgCheckSensor)
            callback.onUvdError(msgCheckSensor)
        }
    }

    fun generateUvd(defaultPDRStepLength: Float = tjLabsPdrDistanceEstimator.getDefaultStepLength(),
                    minPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMinStepLength(),
                    maxPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMaxStepLength(),
                    isSaveData : Boolean = false,
                    fileName : String = "",
                    callback : UVDCallback) {

        uvdGenerationTimeMillis = System.currentTimeMillis()
        tjLabsPdrDistanceEstimator.setDefaultStepLength(defaultPDRStepLength)
        tjLabsPdrDistanceEstimator.setMinStepLength(minPDRStepLength)
        tjLabsPdrDistanceEstimator.setMaxStepLength(maxPDRStepLength)

        tjLabsSensorManager.getSensorDataResultOrNull(object : TJLabsSensorManager.SensorResultListener{
            override fun onSensorChangedResult(sensorData: SensorData) {
                val curTime = System.currentTimeMillis()
                val dtime = if (preTime != 0L) {curTime - preTime} else {null}
                when (userMode) {
                    UserMode.MODE_PEDESTRIAN -> generatePedestrianUvd(curTime, dtime,sensorData, callback)
                    UserMode.MODE_VEHICLE -> generateVehicleUvd(curTime, dtime,sensorData, callback)
                    UserMode.MODE_AUTO -> TODO()
                }

                saveDataFunction(application, isSaveData, fileName, "${curTime},${sensorData.toCollectString()}" + "\n")
                preTime = curTime
            }
        })
    }

    fun generateSimulationUvd(defaultPDRStepLength: Float = tjLabsPdrDistanceEstimator.getDefaultStepLength(),
                              minPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMinStepLength(),
                              maxPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMaxStepLength(),
                              baseFileName : String,
                              callback : UVDCallback) {

        uvdGenerationTimeMillis = System.currentTimeMillis()
        tjLabsPdrDistanceEstimator.setDefaultStepLength(defaultPDRStepLength)
        tjLabsPdrDistanceEstimator.setMinStepLength(minPDRStepLength)
        tjLabsPdrDistanceEstimator.setMaxStepLength(maxPDRStepLength)

        if (JupiterSimulator.loadSensorData(application, baseFileName)) {
            tjLabsSensorManager.getSensorDataResultOrNull(object : TJLabsSensorManager.SensorResultListener{
                override fun onSensorChangedResult(sensorData: SensorData) {
                    val index = sensorSimulationIndex % sensorMutableList.size
                    val element = sensorMutableList[index]
                    val convertResult = convertToSensorData(element)
                    val simulationSensorData = convertResult.second
                    val simulationTime = convertResult.first
                    sensorSimulationIndex++

                    val curTime = System.currentTimeMillis()
                    val dtime = if (preTime != 0L) {simulationTime - preTime} else {null}
                    if (sensorSimulationIndex <= sensorMutableList.size) {
                        when (userMode) {
                            UserMode.MODE_PEDESTRIAN -> generatePedestrianUvd(curTime, dtime,simulationSensorData, callback)
                            UserMode.MODE_VEHICLE -> generateVehicleUvd(curTime, dtime,simulationSensorData, callback)
                            UserMode.MODE_AUTO -> TODO()
                        }
                    }else{
                        stopUvdGeneration()
                    }
                    preTime = simulationTime //시뮬레이션 동작 중일때는 시뮬레이션 시간기준으로 dTime 계산하기

                }
            })
        } else {
            callback.onUvdError("Load Sensor Simulation Data Error!")
        }
    }

    private fun resetVelocityAfterSeconds(velocity : Float, sec : Int = 2) : Float {
        return if (System.currentTimeMillis() - uvdGenerationTimeMillis < sec * 1000) {
            velocity
        } else {
            0f
        }
    }

    private fun generatePedestrianUvd(time : Long, dtime : Long?, sensorData: SensorData, callback: UVDCallback) {
        val pdrUnit = tjLabsPdrDistanceEstimator.estimateDistanceInfo(time, sensorData)
        val attDegree = tjLabsAttitudeEstimator.estimateAttitudeRadian(dtime, sensorData).toDegree()
        val isLookingStatus = tjLabsUnitStatusEstimator.estimateStatus(attDegree, pdrUnit.isIndexChanged)
        if (pdrUnit.isIndexChanged) {
            val index = pdrUnit.index
            val length = pdrUnit.length
            val heading = attDegree.yaw
            callback.onUvdResult(UserMode.MODE_PEDESTRIAN, UserVelocity(userId, time, index, length, heading, isLookingStatus))
            uvdGenerationTimeMillis = time
        } else {
            callback.onUvdPauseMillis(time - uvdGenerationTimeMillis)
        }
        callback.onPressureResult(sensorData.pressure[0])
        callback.onVelocityResult(resetVelocityAfterSeconds(pdrUnit.velocity))
    }

    private fun generateVehicleUvd(time : Long, dtime : Long?, sensorData: SensorData, callback: UVDCallback) {
        val (drUnit, magNormSmoothingVariance) = tjLabsDrDistanceEstimator.estimateDistanceInfo(dtime, sensorData)
        val attDegree = tjLabsAttitudeEstimator.estimateAttitudeRadian(dtime, sensorData).toDegree()
        //TODO() 자석 거치 상황인지 확인
        //TODO() calAccBias?
        if (drUnit.isIndexChanged) {
            val index = drUnit.index
            val length = drUnit.length
            val heading = attDegree.yaw
            callback.onUvdResult(UserMode.MODE_VEHICLE, UserVelocity(userId, time, index, length, heading, true))
            uvdGenerationTimeMillis = time
        } else {
            callback.onUvdPauseMillis(time - uvdGenerationTimeMillis)
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
