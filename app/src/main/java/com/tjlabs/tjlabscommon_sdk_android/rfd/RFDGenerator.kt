package com.tjlabs.tjlabscommon_sdk_android.rfd
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.bleMutableList
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.bleSimulationIndex
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.parseStringToMap
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterSimulator.saveDataFunction
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions
import java.util.Timer
import java.util.TimerTask

const val BLE_RESTART_INTERVAL = 5 * 60 * 1000L // 5분

class RFDGenerator(private val application: Application, val userId : String = "") {
    interface RFDCallback {
        fun onRfdResult(rfd: ReceivedForce)

        fun onRfdError(code : Int, msg : String)

        fun onRfdEmptyMillis(time : Long)
    }

    private var tjLabsBluetoothManager: TJLabsBluetoothManager = TJLabsBluetoothManager(application)
    private var bleScanInfoSet = mutableSetOf<BLEScanInfo>()
    private var rfdGenerationTimeMillis = 0L
    private var rfdTimer: Timer? = null

    init {
        setScanMode(ScanMode.ONLY_WARD_SCAN)
    }

    fun setScanMode(scanMode: ScanMode) {
        val scanFilters = when (scanMode) {
            ScanMode.NO_FILTER_SCAN -> listOf()
            ScanMode.ONLY_WARD_SCAN -> listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(TJLabsBluetoothManager.TJLABS_WARD_UUID))
                    .build()
            )
            ScanMode.ONLY_SEI_SCAN -> listOf()
            ScanMode.WARD_SEI_SCAN -> listOf()
        }
        tjLabsBluetoothManager.setScanFilters(scanFilters)
    }

    fun checkIsAvailableRfd(callback: RFDCallback, completion : (Boolean, String) -> Unit) {
        if (rfdTimer != null) {
            rfdTimer?.cancel()
            rfdTimer = null
            callback.onRfdError(RFDErrorCode.DUPLICATE_SCAN_START, "duplicate scan start error")
        }

        val (isCheckBleAvailable, msgCheckBleAvailable) = tjLabsBluetoothManager.checkBleAvailable()
        val (isCheckBlePermission, msgCheckBlePermission) = tjLabsBluetoothManager.checkPermissions()
        val (isCheckBleActivation, msgCheckBleActivation) = tjLabsBluetoothManager.checkBleActivation()

        if (!isCheckBleAvailable) {
            completion(false, msgCheckBleAvailable)
            callback.onRfdError(RFDErrorCode.BLUETOOTH_NOT_SUPPORTED, msgCheckBleAvailable)
        }

        if (!isCheckBlePermission) {
            completion(false, msgCheckBlePermission)
            callback.onRfdError(RFDErrorCode.PERMISSION_DENIED, msgCheckBlePermission)
            return
        }

        if (!isCheckBleActivation) {
            completion(false, msgCheckBleActivation)
            callback.onRfdError(RFDErrorCode.BLUETOOTH_DISABLED, msgCheckBleActivation)
            return
        }

        completion(true, "RFD check passed. All conditions met.")
    }

    fun isBleScanAvailable() : Boolean {
        val (isCheckBleAvailable, _) = tjLabsBluetoothManager.checkBleAvailable()
        val (isCheckBlePermission, _) = tjLabsBluetoothManager.checkPermissions()
        val (isCheckBleActivation, _) = tjLabsBluetoothManager.checkBleActivation()

        //하나라도 만족하지않으면 false
        return isCheckBleAvailable && isCheckBlePermission && isCheckBleActivation
    }

    fun generateRfd(
        rfdIntervalMillis : Long = 500,
        bleScanWindowTimeMillis : Long = 1000,
        minRssiThreshold : Int = -100,
        maxRssiThreshold : Int = -40,
        getPressure: () -> Float = {0f},
        isSaveData : Boolean = false,
        fileName : String = "",
        callback: RFDCallback
    ) {
        rfdGenerationTimeMillis = System.currentTimeMillis()
        val timer = Timer()
        rfdTimer = timer


        //bleManager 에서 scan 결과가 나옴.
        tjLabsBluetoothManager.getBleScanResult(object :
            TJLabsBluetoothManager.ScanResultListener {
            override fun onScanBleSetResultOrNull(bleScanInfoSet: MutableSet<BLEScanInfo>) {
                this@RFDGenerator.bleScanInfoSet = bleScanInfoSet
            }
        })

        tjLabsBluetoothManager.setBleScanInfoSetTimeLimitNanos(TJLabsUtilFunctions.millis2nanos(bleScanWindowTimeMillis))
        tjLabsBluetoothManager.setMinRssiThreshold(minRssiThreshold)
        tjLabsBluetoothManager.setMaxRssiThreshold(maxRssiThreshold)

        if (isBleScanAvailable()) {
            tjLabsBluetoothManager.startScan()
        } else {
            tjLabsBluetoothManager.stopScan()
            this@RFDGenerator.bleScanInfoSet.clear()
        }

        //bleManager 에서 scan 결과가 나온 것을 0.5초마다 평균하여 콜백함..
        timer.schedule(object : TimerTask() {
            override fun run() {
                val curTime = System.currentTimeMillis()
                val currentBleScanInfoSet = this@RFDGenerator.bleScanInfoSet
                val averageBleMap =  TJLabsBluetoothFunctions.averageBleScanInfoSet(currentBleScanInfoSet)
                callback.onRfdResult(ReceivedForce(userId, System.currentTimeMillis() - (bleScanWindowTimeMillis / 2), averageBleMap, getPressure())) // 결과 리턴

                if (averageBleMap.isEmpty()) {
                    callback.onRfdEmptyMillis(System.currentTimeMillis() - rfdGenerationTimeMillis)
                } else{
                    rfdGenerationTimeMillis = System.currentTimeMillis()
                    callback.onRfdEmptyMillis(0)
                }

                saveDataFunction(application, isSaveData, fileName, "${curTime},$averageBleMap" + "\n")
            }
        }, 0, rfdIntervalMillis)

        //BLE_RESTART_INTERVAL 마다 동작하는 타이머
        //ble manager를 재시작한다.
        timer.schedule(object : TimerTask() {
            override fun run() {
                tjLabsBluetoothManager.stopScan(restart = true)
                //이용 불가인 경우 다음 타이머 동작 시 다시 체크 후 시작됨.
                if (isBleScanAvailable()) {
                    tjLabsBluetoothManager.startScan()
                }
            }
        }, BLE_RESTART_INTERVAL, BLE_RESTART_INTERVAL)
    }

    fun loadRfdData(application: Application, fileName : String) : Boolean {
        return JupiterSimulator.loadBleData(application, fileName)
    }

    fun generateSimulationRfd(
        rfdIntervalMillis: Long = 500,
        bleScanWindowTimeMillis: Long = 1000,
        minRssiThreshold: Int = -100,
        maxRssiThreshold: Int = -40,
        getPressure: () -> Float = { 0f },
        baseFileName: String,
        callback: RFDCallback
    ) {
        rfdGenerationTimeMillis = System.currentTimeMillis()

        if (JupiterSimulator.loadRfdData) {
            bleSimulationIndex = 0 // index 초기화

            val timer = Timer()
            rfdTimer = timer

            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (bleSimulationIndex < bleMutableList.size) {
                        Log.d("CheckFileData", "rfd change")
                        val index = bleSimulationIndex % bleMutableList.size
                        val element = bleMutableList[index]
                        val averageBleMap = parseStringToMap(element)
                        bleSimulationIndex++

                        callback.onRfdResult(
                            ReceivedForce(
                                userId,
                                System.currentTimeMillis() - (bleScanWindowTimeMillis / 2),
                                averageBleMap,
                                getPressure()
                            )
                        )

                        if (averageBleMap.isEmpty()) {
                            callback.onRfdEmptyMillis(System.currentTimeMillis() - rfdGenerationTimeMillis)
                        } else {
                            rfdGenerationTimeMillis = System.currentTimeMillis()
                            callback.onRfdEmptyMillis(0)
                        }
                    } else {
                        timer.cancel()
                    }
                }
            }, 0, rfdIntervalMillis)
        } else {
            callback.onRfdError(999, "Load BLE Simulation Data Error!")
        }
    }

    fun stopRfdGeneration() {
        rfdTimer?.cancel()
        rfdTimer = null
        tjLabsBluetoothManager.stopScan()
        // TODO() stopScan 리턴 활용하기
        bleScanInfoSet.clear()
        Log.d("CheckBLERestart", "stop scan")
    }

    fun checkRfdException(callback: RFDCallback){
        //1. RFD 발생마다 exception 이 발생하는지 체크하기?
        //2. RFD State 가 변하는 것을 감지하고 에러 체크하기?
        //3,,,
        //TODO()
        val errorCode = 0
        val errorMsg = "Errorrrrr"
        callback.onRfdError(errorCode, errorMsg)
    }

    fun isBleAvailable(context: Context): Boolean {
        val hasBleFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        return hasBleFeature && bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

}