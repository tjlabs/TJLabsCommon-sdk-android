package com.tjlabs.tjlabscommon_sdk_android.rfd
import android.app.Application
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions

class RFDGenerator(private val application: Application, val userId : String = "") {
    interface RFDCallback {
        fun onRfdResult(rfd: ReceivedForce)

        fun onRfdError(code : Int, msg : String)
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
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable!!)
        }

        timerRunnable = object : Runnable {
            override fun run() {
                val (isCheckBleAvailable, msgCheckBleAvailable) = tjLabsBluetoothManager.checkBleAvailable()
                val (isCheckBlePermission, msgCheckBlePermission) = tjLabsBluetoothManager.checkPermissions()
                val (isCheckBleActivation, msgCheckBleActivation) = tjLabsBluetoothManager.checkBleActivation()

                if (!isCheckBleAvailable) {
                    callback.onRfdError(RFDErrorCode.BLUETOOTH_NOT_SUPPORTED, msgCheckBleAvailable)
                    return
                }

                if (!isCheckBlePermission) {
                    callback.onRfdError(RFDErrorCode.PERMISSION_DENIED, msgCheckBlePermission)
                    return
                }

                if (!isCheckBleActivation) {
                    callback.onRfdError(RFDErrorCode.BLUETOOTH_DISABLED, msgCheckBleActivation)
                    return
                }

                tjLabsBluetoothManager.setBleScanInfoSetTimeLimitNanos(
                    TJLabsUtilFunctions.millis2nanos(
                        bleScanWindowTimeMillis
                    )
                )
                tjLabsBluetoothManager.setMinRssiThreshold(minRssiThreshold)
                tjLabsBluetoothManager.setMaxRssiThreshold(maxRssiThreshold)

                val (isSuccess, msg) = tjLabsBluetoothManager.startScan()
                isGenerateRfd = isSuccess

                if (isGenerateRfd) {
                    tjLabsBluetoothManager.getBleScanResult(object :
                        TJLabsBluetoothManager.ScanResultListener {
                        override fun onScanBleSetResultOrNull(bleScanInfoSet: MutableSet<BLEScanInfo>) {
                            this@RFDGenerator.bleScanInfoSet = bleScanInfoSet
                        }
                    })
                } else {
                    callback.onRfdError(RFDErrorCode.PERMISSION_DENIED, msg)
                    return
                }

                if (isGenerateRfd) {
                    val currentBleScanInfoSet = this@RFDGenerator.bleScanInfoSet
                    val averageBleMap = TJLabsBluetoothFunctions.averageBleScanInfoSet(currentBleScanInfoSet)
                    callback.onRfdResult(ReceivedForce(userId, System.currentTimeMillis(), averageBleMap, getPressure())) // 결과 리턴
                    handler.postDelayed(this, rfdIntervalMillis)
                }

                //1초마다 exception 이 발생하는지 체크하기?

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