package com.tjlabs.tjlabscommon_sdk_android.rfd
import android.app.Application
import android.bluetooth.le.ScanFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions

class RFDGenerator(application: Application, val userID : String = "") {
    interface RFDCallback {
        fun onRFDResult(success : Boolean, msg : String, rfd: ReceivedForce)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var tjLabsBluetoothManager: TJLabsBluetoothManager = TJLabsBluetoothManager(application)
    private var bleScanInfoSet = mutableSetOf<BLEScanInfo>()
    private var isGenerateRFD = false

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

    fun generateRFD(
        rfdIntervalMillis : Long = 500,
        bleScanWindowTimeMillis : Long = 1000,
        minRssThreshold : Int = -100,
        maxRssThreshold : Int = -40,
        getPressure: () -> Float = {0f},
        callback: RFDCallback
    ) {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isGenerateRFD) {
                    val (isCheckBLESuccess, msgCheckBLE) = tjLabsBluetoothManager.checkPermissionsAndBleState()
                    if (isCheckBLESuccess) {
                        tjLabsBluetoothManager.setBleScanInfoSetTimeLimitNanos(TJLabsUtilFunctions.millis2nanos(bleScanWindowTimeMillis))
                        tjLabsBluetoothManager.setRssMinThreshold(minRssThreshold)
                        tjLabsBluetoothManager.setRssMaxThreshold(maxRssThreshold)
                        val (isSuccess, msg) = tjLabsBluetoothManager.startScan()
                        isGenerateRFD = isSuccess
                        tjLabsBluetoothManager.getBLEScanResult(object : TJLabsBluetoothManager.ScanResultListener {
                            override fun onScanBLESetResultOrNull(bleScanInfoSet: MutableSet<BLEScanInfo>) {
                                this@RFDGenerator.bleScanInfoSet = bleScanInfoSet
                            }
                        })
                        callback.onRFDResult(isGenerateRFD, msg, ReceivedForce()) // 결과 리턴
                    }else{
                        callback.onRFDResult(isGenerateRFD, msgCheckBLE, ReceivedForce()) // 결과 리턴
                    }
                } else {
                    val currentBleScanInfoSet = this@RFDGenerator.bleScanInfoSet
                    val averageBleMap = TJLabsBluetoothFunctions.averageBLEScanInfoSet(currentBleScanInfoSet)
                    callback.onRFDResult(isGenerateRFD, "", ReceivedForce(userID, System.currentTimeMillis(), averageBleMap, getPressure())) // 결과 리턴
                }

                // 성공했을 때만 주기적으로 콜백
                if (isGenerateRFD) {
                    handler.postDelayed(this, rfdIntervalMillis)
                }
            }
        }
        handler.postDelayed(timerRunnable!!, rfdIntervalMillis)
    }


    fun stopRFDGeneration() {
        timerRunnable?.let {
            handler.removeCallbacks(it)
            timerRunnable = null
        }
        tjLabsBluetoothManager.stopScan()
        isGenerateRFD = false
        bleScanInfoSet.clear()
    }
}