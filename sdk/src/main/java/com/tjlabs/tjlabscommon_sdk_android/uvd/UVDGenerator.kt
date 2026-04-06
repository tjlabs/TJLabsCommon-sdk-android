package com.tjlabs.tjlabscommon_sdk_android.uvd

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataFunctions
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataFunctions.convertToSensorData
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataFunctions.loadUvdJsonData
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataFunctions.saveUvdResultAsJson
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataFunctions.sensorMutableList
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataFunctions.sensorSimulationIndex
import com.tjlabs.tjlabscommon_sdk_android.uvd.dr.TJLabsDRDistanceEstimator
import com.tjlabs.tjlabscommon_sdk_android.uvd.pdr.TJLabsPDRDistanceEstimator


const val sensorFrequency = 40
const val MODE_CHANGE_TIME_CONDITION: Float = 10 * 1000f
const val MODE_CHANGE_RFLOW_TIME_OVER: Float = 0.1f
const val MODE_CHANGE_RFLOW_FORCE: Float = 0.035f // 기존 0.065
const val MODE_CHANGE_TIME_AFTER_ROUTE_TRACK: Float = 30 * 1000f

class UVDGenerator(private val application: Application, private val userId : String = "") {
    interface UVDCallback {
        fun onUvdResult(mode : UserMode, uvd: UserVelocity) {
        }

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

    private var generatorUserMode = UserMode.MODE_PEDESTRIAN // UVD Generator 동작을 위한 설정
    private var currentUserMode = UserMode.MODE_VEHICLE // AutoMode 동작 내 판단을 위한 설정
    private var preUserMode = UserMode.MODE_VEHICLE
    private var drVelocityScale = 1f

    private var lastModeChangedTime: Long = 0
    private var lastStepChangedTime: Long = 0
    private var preTime = 0L

    private var autoUnitIndexCount = 0

    private var rflow: Float = 0.0f
    private var rflowForVelocity: Float = 0.0f
    private var rflowForAutoMode: Float = 0.0f

    private var isSufficientRfdBuffer: Boolean = false
    private var isSufficientRfdVelocityBuffer: Boolean = false
    private var isSufficientRfdAutoMode: Boolean = false

    private var routeTrackFinishedTime : Long = 0

    private var isInEntranceLevel: Boolean = false
    private var isStartRouteTrack: Boolean = false

    private var isBackGround = false
    private var isSaveUvdData = false
    private val simulationHandler = Handler(Looper.getMainLooper())
    private var simulationRunnable: Runnable? = null

    fun setRFlow(rflow: Float, rflowForVelocity: Float, rflowForAutoMode: Float, isSufficient: Boolean, isSufficientForVelocity: Boolean, isSufficientForAutoMode: Boolean) {
        this.rflow = rflow
        this.rflowForVelocity = rflowForVelocity
        this.rflowForAutoMode = rflowForAutoMode

        isSufficientRfdBuffer = isSufficient
        isSufficientRfdVelocityBuffer = isSufficientForVelocity
        isSufficientRfdAutoMode = isSufficientForAutoMode

        tjLabsDrDistanceEstimator.setRFlow(
            rflow,
            rflowForVelocity,
            rflowForAutoMode,
            isSufficient,
            isSufficientForVelocity,
            isSufficientForAutoMode
        )
    }

    fun setRouteTrackFinishedTime(time : Long) {
        routeTrackFinishedTime = time
    }

    fun setIsInEntranceLevel(flag : Boolean) {
        isInEntranceLevel = flag
    }

    fun setIsStartRouteTrack(flag : Boolean) {
        isStartRouteTrack = flag
        tjLabsDrDistanceEstimator.setIsStartRouteTrack(flag)
    }

    fun setIsBackground(flag : Boolean) {
        isBackGround = flag
    }

    fun setUserMode(mode: UserMode) {
        generatorUserMode = mode
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
                    callback : UVDCallback) {

        uvdGenerationTimeMillis = System.currentTimeMillis()
        isSaveUvdData = isSaveData
        tjLabsPdrDistanceEstimator.setDefaultStepLength(defaultPDRStepLength)
        tjLabsPdrDistanceEstimator.setMinStepLength(minPDRStepLength)
        tjLabsPdrDistanceEstimator.setMaxStepLength(maxPDRStepLength)

        tjLabsSensorManager.getSensorDataResultOrNull(object : TJLabsSensorManager.SensorResultListener{
            override fun onSensorChangedResult(sensorData: SensorData) {
                val curTime = System.currentTimeMillis()
                val dtime = if (preTime != 0L) {curTime - preTime} else {null}
                when (generatorUserMode) {
                    UserMode.MODE_PEDESTRIAN -> generatePedestrianUvd(curTime, dtime,sensorData, callback)
                    UserMode.MODE_VEHICLE -> generateVehicleUvd(curTime, dtime,sensorData, callback)
                    UserMode.MODE_AUTO -> generateAutoUvd(curTime, dtime, sensorData, callback)
                }
                preTime = curTime
            }
        })
    }

    fun loadUvdData(application: Application, fileName : String) : Boolean {
        return JupiterDataFunctions.loadSensorData(application, fileName)
    }

    internal fun generateSimulationUvd(defaultPDRStepLength: Float = tjLabsPdrDistanceEstimator.getDefaultStepLength(),
                              minPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMinStepLength(),
                              maxPDRStepLength : Float = tjLabsPdrDistanceEstimator.getMaxStepLength(),
                              baseFileName : String,
                              isSaveData: Boolean = false,
                              callback : UVDCallback) {

        uvdGenerationTimeMillis = System.currentTimeMillis()
        isSaveUvdData = isSaveData
        tjLabsPdrDistanceEstimator.setDefaultStepLength(defaultPDRStepLength)
        tjLabsPdrDistanceEstimator.setMinStepLength(minPDRStepLength)
        tjLabsPdrDistanceEstimator.setMaxStepLength(maxPDRStepLength)

        if (JupiterDataFunctions.loadUvdData) {
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
                        when (generatorUserMode) {
                            UserMode.MODE_PEDESTRIAN -> generatePedestrianUvd(curTime, dtime,simulationSensorData, callback)
                            UserMode.MODE_VEHICLE -> generateVehicleUvd(curTime, dtime,simulationSensorData, callback)
                            UserMode.MODE_AUTO -> generateAutoUvd(curTime, dtime, simulationSensorData, callback)
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

    fun generateSimulationUvdFromJson(
        simulationUserId: String = userId,
        serviceStartTime: String = JupiterDataFunctions.getServiceStartTime(),
        callback: UVDCallback
    ) {
        val serviceStartTimeMillis = serviceStartTime.toLongOrNull()
        if (serviceStartTimeMillis == null) {
            callback.onUvdError("Invalid serviceStartTime. It must be epoch millis.")
            return
        }

        val records = loadUvdJsonData(application, simulationUserId, serviceStartTime)
        if (records.isEmpty()) {
            callback.onUvdError("Load UVD JSON Simulation Data Error!")
            return
        }

        simulationRunnable?.let { simulationHandler.removeCallbacks(it) }
        var recordIndex = 0
        val playbackStartElapsed = SystemClock.elapsedRealtime()

        fun scheduleNext() {
            if (recordIndex >= records.size) {
                simulationRunnable = null
                return
            }

            val nextRecord = records[recordIndex]
            val relativeTime = nextRecord.mobileTime - serviceStartTimeMillis
            val targetElapsed = playbackStartElapsed + relativeTime
            val delay = (targetElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

            val runnable = Runnable {
                if (recordIndex >= records.size) return@Runnable

                val record = records[recordIndex]
                recordIndex++
                val mode = when (record.mode.uppercase()) {
                    "PDR" -> UserMode.MODE_PEDESTRIAN
                    "DR" -> UserMode.MODE_VEHICLE
                    "AUTO" -> UserMode.MODE_AUTO
                    else -> {
                        callback.onUvdError("Invalid UVD mode in JSON: ${record.mode}")
                        scheduleNext()
                        return@Runnable
                    }
                }

                val uvdResult = UserVelocity(
                    tenant_user_name = simulationUserId,
                    mobile_time = record.mobileTime,
                    index = record.index,
                    length = record.length,
                    heading = record.heading,
                    looking = true
                )
                callback.onUvdResult(mode, uvdResult)

                scheduleNext()
            }
            simulationRunnable = runnable
            simulationHandler.postDelayed(runnable, delay)
        }

        scheduleNext()
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
        tjLabsPdrDistanceEstimator.setAutoMode(false)

        if (pdrUnit.isIndexChanged) {
            val index = pdrUnit.index
            val length = pdrUnit.length
            val heading = attDegree.yaw
            val uvdResult = UserVelocity(userId, time, index, length, heading, isLookingStatus)
            callback.onUvdResult(UserMode.MODE_PEDESTRIAN, uvdResult)
            saveUvd(UserMode.MODE_PEDESTRIAN, uvdResult)
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
            val uvdResult = UserVelocity(userId, time, index, length, heading, true)
            callback.onUvdResult(UserMode.MODE_VEHICLE, uvdResult)
            saveUvd(UserMode.MODE_VEHICLE, uvdResult)
            uvdGenerationTimeMillis = time
        } else {
            callback.onUvdPauseMillis(time - uvdGenerationTimeMillis)
        }
        callback.onPressureResult(sensorData.pressure[0])
        callback.onVelocityResult(resetVelocityAfterSeconds(drUnit.velocity))
        callback.onMagNormSmoothingVarResult(magNormSmoothingVariance)
    }

    private fun generateAutoUvd(time : Long, dtime : Long?, sensorData: SensorData, callback: UVDCallback) {
        val pdrUnit = tjLabsPdrDistanceEstimator.estimateDistanceInfo(time, sensorData)
        val attDegree = tjLabsAttitudeEstimator.estimateAttitudeRadian(dtime, sensorData).toDegree()
        val (drUnit, magNormSmoothingVariance) = tjLabsDrDistanceEstimator.estimateDistanceInfo(dtime, sensorData)
        val isLookingStatus = tjLabsUnitStatusEstimator.estimateStatus(attDegree, pdrUnit.isIndexChanged)

        val currentTime = System.currentTimeMillis()

        if (isBackGround) {
            lastModeChangedTime = currentTime
        }

        val isNormalStep = tjLabsPdrDistanceEstimator.getNormalStepCountFlag()

        if (currentTime - lastModeChangedTime >= MODE_CHANGE_TIME_CONDITION) {
            if (currentUserMode == UserMode.MODE_VEHICLE && isNormalStep) {
                currentUserMode = UserMode.MODE_PEDESTRIAN
                lastModeChangedTime = currentTime
            } else {
                val diffTime = currentTime - lastStepChangedTime
                if (isSufficientRfdAutoMode && diffTime >= MODE_CHANGE_TIME_CONDITION) {
                    if (rflowForAutoMode < MODE_CHANGE_RFLOW_TIME_OVER) {
                        currentUserMode = UserMode.MODE_VEHICLE
                        lastModeChangedTime = currentTime
                    }
                } else if (isSufficientRfdAutoMode) {
                    if (rflowForAutoMode < MODE_CHANGE_RFLOW_FORCE) {
                        currentUserMode = UserMode.MODE_VEHICLE
                        lastModeChangedTime = currentTime
                    }
                }
            }

            val diffRouteTrackTime = currentTime - routeTrackFinishedTime
            if (isInEntranceLevel || isStartRouteTrack) {
                currentUserMode = UserMode.MODE_VEHICLE
                lastModeChangedTime = currentTime
            } else if (diffRouteTrackTime > 0 && diffRouteTrackTime < MODE_CHANGE_TIME_AFTER_ROUTE_TRACK ) {
                currentUserMode = UserMode.MODE_VEHICLE
                lastModeChangedTime = currentTime
            }
        }

        if (currentUserMode == UserMode.MODE_PEDESTRIAN) {
            tjLabsPdrDistanceEstimator.setAutoMode(true)
            if (pdrUnit.isIndexChanged) {
                lastStepChangedTime = currentTime
                autoUnitIndexCount += 1

                val length = pdrUnit.length
                val heading = attDegree.yaw

                uvdGenerationTimeMillis = time

                val uvdResult = UserVelocity(userId, time, autoUnitIndexCount, length, heading, isLookingStatus)
                callback.onUvdResult(UserMode.MODE_PEDESTRIAN, uvdResult)
                saveUvd(UserMode.MODE_PEDESTRIAN, uvdResult)
            } else{
                callback.onUvdPauseMillis(time - uvdGenerationTimeMillis)
            }
        } else {
            if (drUnit.isIndexChanged) {
                lastStepChangedTime = currentTime
                autoUnitIndexCount += 1
                val length = drUnit.length
                val heading = attDegree.yaw

                uvdGenerationTimeMillis = time
                val uvdResult = UserVelocity(userId, time, autoUnitIndexCount, length, heading, true)
                callback.onUvdResult(UserMode.MODE_VEHICLE, uvdResult)
                saveUvd(UserMode.MODE_VEHICLE, uvdResult)
            } else{
                callback.onUvdPauseMillis(time - uvdGenerationTimeMillis)
            }
        }

        if (currentUserMode != preUserMode) {
            if (currentUserMode == UserMode.MODE_PEDESTRIAN) {
                tjLabsPdrDistanceEstimator.setModeDrToPdr(true)
            } else {
                tjLabsPdrDistanceEstimator.setModeDrToPdr(false)
            }
        }

        preUserMode = currentUserMode

        callback.onPressureResult(sensorData.pressure[0])
        if (currentUserMode == UserMode.MODE_PEDESTRIAN) {
            callback.onVelocityResult(resetVelocityAfterSeconds(pdrUnit.velocity))
        } else {
            callback.onVelocityResult(resetVelocityAfterSeconds(drUnit.velocity))
        }
        callback.onMagNormSmoothingVarResult(magNormSmoothingVariance)
    }

    fun stopUvdGeneration() {
        tjLabsSensorManager.stopSensorChanged()
        simulationRunnable?.let { simulationHandler.removeCallbacks(it) }
        simulationRunnable = null
        tjLabsPdrDistanceEstimator = TJLabsPDRDistanceEstimator()
        tjLabsAttitudeEstimator = TJLabsAttitudeEstimator(sensorFrequency)
        tjLabsUnitStatusEstimator = TJLabsUnitStatusEstimator()
        uvdGenerationTimeMillis = 0L
        drVelocityScale = 1f
        isSaveUvdData = false
    }

    private fun saveUvd(mode: UserMode, uvd: UserVelocity) {
        saveUvdResultAsJson(
            app = application,
            saveFlag = isSaveUvdData,
            isBackGround = isBackGround,
            userId = userId,
            mobileTime = uvd.mobile_time,
            mode = mode.value,
            index = uvd.index,
            length = uvd.length,
            heading = uvd.heading
        )
    }
}
