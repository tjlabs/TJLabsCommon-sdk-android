package com.tjlabs.tjlabscommon_sdk_android.rfd
import android.app.Application
import android.bluetooth.le.ScanFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions

class RFDGenerator(application: Application, val userId : String = "") {
    interface RFDCallback {
        fun onRfdResult(rfd: ReceivedForce)

        fun onRfdError(error : String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var tjLabsBluetoothManager: TJLabsBluetoothManager = TJLabsBluetoothManager(application)
    private var bleScanInfoSet = mutableSetOf<BLEScanInfo>()
    private var isGenerateRfd = false

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
            else -> listOf()
        }
        tjLabsBluetoothManager.setScanFilters(scanFilters)
    }

    fun generateRfd(
        rfdIntervalMillis : Long = 500,
        bleScanWindowTimeMillis : Long = 1000,
        minRssiThreshold : Int = -100,
        maxRssiThreshold : Int = -40,
        getPressure: () -> Float = {0f},
        callback: RFDCallback
    ) {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isGenerateRfd) {
                    val (isCheckBleSuccess, msgCheckBle) = tjLabsBluetoothManager.checkPermissionsAndBleState()
                    if (isCheckBleSuccess) {
                        tjLabsBluetoothManager.setBleScanInfoSetTimeLimitNanos(TJLabsUtilFunctions.millis2nanos(bleScanWindowTimeMillis))
                        tjLabsBluetoothManager.setMinRssiThreshold(minRssiThreshold)
                        tjLabsBluetoothManager.setMaxRssiThreshold(maxRssiThreshold)
                        val (isSuccess, msg) = tjLabsBluetoothManager.startScan()
                        isGenerateRfd = isSuccess
                        if (isSuccess) {
                            tjLabsBluetoothManager.getBleScanResult(object : TJLabsBluetoothManager.ScanResultListener {
                                override fun onScanBleSetResultOrNull(bleScanInfoSet: MutableSet<BLEScanInfo>) {
                                    this@RFDGenerator.bleScanInfoSet = bleScanInfoSet
                                }
                            })
                        } else {
                            callback.onRfdError(msg)
                        }

                    }else{
                        callback.onRfdError(msgCheckBle)
                    }
                } else {
                    val currentBleScanInfoSet = this@RFDGenerator.bleScanInfoSet
                    val averageBleMap = TJLabsBluetoothFunctions.averageBleScanInfoSet(currentBleScanInfoSet)
                    callback.onRfdResult(ReceivedForce(userId, System.currentTimeMillis(), averageBleMap, getPressure())) // 결과 리턴
                }

                if (isGenerateRfd) {
                    handler.postDelayed(this, rfdIntervalMillis)
                }
            }
        }
        handler.postDelayed(timerRunnable!!, rfdIntervalMillis)
    }


    fun stopRfdGeneration() {
        timerRunnable?.let {
            handler.removeCallbacks(it)
            timerRunnable = null
        }
        tjLabsBluetoothManager.stopScan()
        isGenerateRfd = false
        bleScanInfoSet.clear()
    }
}