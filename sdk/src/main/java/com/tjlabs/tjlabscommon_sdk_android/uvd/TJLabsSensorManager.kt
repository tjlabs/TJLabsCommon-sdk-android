package com.tjlabs.tjlabscommon_sdk_android.uvd

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicInteger


internal data class SensorEventCounts(
    val acc: Int,
    val gyro: Int,
    val gameVector: Int,
    val rotVector: Int,
    val mag: Int,
    val pressure: Int
)

internal class TJLabsSensorManager(private val context : Context, private val frequency : Int): SensorEventListener {
    interface SensorResultListener {
        fun onSensorChangedResult(sensorData : SensorData)
    }

    private val sensorManager by lazy {//센서데이터 수집용
        context.getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private var sensorData = SensorData()
    private var sensorTimer: Timer? = null
    private var isRunning = false

    private val accEventCount = AtomicInteger(0)
    private val gyroEventCount = AtomicInteger(0)
    private val gameVectorEventCount = AtomicInteger(0)
    private val rotVectorEventCount = AtomicInteger(0)
    private val magEventCount = AtomicInteger(0)
    private val pressureEventCount = AtomicInteger(0)

    fun snapshotAndResetEventCounts(): SensorEventCounts = SensorEventCounts(
        acc = accEventCount.getAndSet(0),
        gyro = gyroEventCount.getAndSet(0),
        gameVector = gameVectorEventCount.getAndSet(0),
        rotVector = rotVectorEventCount.getAndSet(0),
        mag = magEventCount.getAndSet(0),
        pressure = pressureEventCount.getAndSet(0)
    )

    // 2026-11-25: 생성자에서 센서를 등록하지 않는다 — 리플레이 · 시뮬레이션처럼 수집을 시작하지 않는 인스턴스는 stopSensorChanged 가
    //   (isRunning=false 라) 해제하지 않아 인스턴스마다 리스너가 남았다 (UVDGenerator 1 개 = 1 리스너, 프로세스 한도 128 → 크래시).
    //   등록은 getSensorDataResultOrNull (수집 시작) 에서만.

    private fun initSensorManager() {
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
    }

    fun checkSensorAvailability(): Pair<Boolean, String> {
        val accCheck = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroCheck = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val magUnCaliCheck = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        val magCheck = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val pressureCheck = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val rotationVectorCheck = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gameRotationVectorCheck = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        val missingSensors = mutableListOf<String>()

        if (accCheck == null) missingSensors.add("Accelerometer")
        if (gyroCheck == null) missingSensors.add("Gyroscope")
        if (magUnCaliCheck == null) missingSensors.add("Uncalibrated Magnetometer")
        if (magCheck == null) missingSensors.add("Magnetometer")
        if (rotationVectorCheck == null) missingSensors.add("Rotation Vector")
        if (gameRotationVectorCheck == null) missingSensors.add("Game Rotation Vector")

        val pressureStatus = if (pressureCheck != null) "Pressure sensor is available." else "Pressure sensor is NOT available."

        val isAllAvailable = missingSensors.isEmpty()

        val resultMessage = if (isAllAvailable) {
            "All required sensors are available. [$pressureStatus]"
        } else {
            "Some sensors are missing: ${missingSensors.joinToString(", ")} [$pressureStatus]"
        }

        return Pair(isAllAvailable, resultMessage)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, sensorData.acc, 0, sensorData.acc.size)
                    accEventCount.incrementAndGet()
                }

                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, sensorData.gyro, 0, sensorData.gyro.size)
                    gyroEventCount.incrementAndGet()
                }

                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                    System.arraycopy(event.values, 0, sensorData.magRaw, 0, sensorData.magRaw.size)
                    magEventCount.incrementAndGet()
                }

                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    System.arraycopy(event.values, 0, sensorData.gameVector, 0, sensorData.gameVector.size)
                    gameVectorEventCount.incrementAndGet()
                }

                Sensor.TYPE_ROTATION_VECTOR -> {
                    System.arraycopy(event.values, 0, sensorData.rotVector, 0, sensorData.rotVector.size)
                    rotVectorEventCount.incrementAndGet()
                }

                Sensor.TYPE_PRESSURE -> {
                    System.arraycopy(event.values, 0, sensorData.pressure, 0, sensorData.pressure.size)
                    pressureEventCount.incrementAndGet()
                }
            }
        }
    }

    fun getSensorDataResultOrNull(callback: SensorResultListener) {
        // 이미 수집 중이면 이전 타이머를 멈춘다 (다시 시작해도 타이머가 겹치지 않게). 같은 리스너 · 센서 재등록은 무시된다.
        sensorTimer?.cancel()
        sensorTimer = null
        isRunning = true
        initSensorManager()

        val intervalMillis = TJLabsUtilFunctions.frequency2Millis(frequency).toLong()
        val timer = Timer()
        sensorTimer = timer

        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!isRunning) {
                    timer.cancel()
                    return
                }
                callback.onSensorChangedResult(sensorData)
            }
        }, 0, intervalMillis)
    }


    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {

    }

    fun stopSensorChanged() : Pair<Boolean, String> {
        // 2026-11-25: 시작 여부와 상관없이 항상 해제 (예전 버전이 생성자에서 등록한 리스너도 남지 않게).
        sensorManager.unregisterListener(this)
        if (!isRunning)  return Pair(false, "SensorManager is not started.")

        sensorData = SensorData()
        // 실행 중이 아니면 무시
        isRunning = false
        sensorTimer?.cancel()
        sensorTimer = null
        return Pair(true, "Success Stop Sensor Changed")
    }

}