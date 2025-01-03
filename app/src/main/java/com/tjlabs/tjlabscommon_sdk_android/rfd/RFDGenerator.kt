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

    enum class MODE{
        NO_FILTER_SCAN, ONLY_WARD_SCAN, ONLY_SEI_SCAN, WARD_SEI_SCAN
    }

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var tjLabsBluetoothManager: TJLabsBluetoothManager = TJLabsBluetoothManager(application)
    private var bleScanInfoSet = mutableSetOf<BLEScanInfo>()
    private var isGenerateRFD = false

    fun setMode(mode: MODE) {
        val scanFilters = when (mode) {
            MODE.NO_FILTER_SCAN -> listOf()
            MODE.ONLY_WARD_SCAN -> listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(TJLabsBluetoothManager.TJLABS_WARD_UUID))
                    .build()
            )
            MODE.ONLY_SEI_SCAN -> listOf()
            MODE.WARD_SEI_SCAN -> listOf()
            else -> listOf()
        }
        tjLabsBluetoothManager.setScanFilters(scanFilters)
    }


    fun generateRFD(
        rfdIntervalMillis : Long = 1000,
        bleTrimIntervalMillis : Long = 1000,
        minBleThreshold : Int = -100,
        maxBleThreshold : Int = 0,
        pressure: Float = 0f,
        callback: RFDCallback
    ) {
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isGenerateRFD) {
                    val (isCheckBLESuccess, msgCheckBLE) = tjLabsBluetoothManager.checkPermissionsAndBleState()
                    if (isCheckBLESuccess) {
                        tjLabsBluetoothManager.setBleScanInfoSetTimeLimitNanos(TJLabsUtilFunctions.millis2nanos(bleTrimIntervalMillis))
                        tjLabsBluetoothManager.setRssMinThreshold(minBleThreshold)
                        tjLabsBluetoothManager.setRssMaxThreshold(maxBleThreshold)
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
                    callback.onRFDResult(isGenerateRFD, "", ReceivedForce(userID, System.currentTimeMillis(), averageBleMap, pressure)) // 결과 리턴
                }

                // 성공했을 때만 주기적으로 콜백
                if (isGenerateRFD) {
                    handler.postDelayed(this, rfdIntervalMillis)
                }
            }
        }
        handler.postDelayed(timerRunnable!!, rfdIntervalMillis)
    }


    fun stop() {
        timerRunnable?.let {
            handler.removeCallbacks(it)
            timerRunnable = null
        }
        tjLabsBluetoothManager.stopScan()
        isGenerateRFD = false
        bleScanInfoSet.clear()
    }
}