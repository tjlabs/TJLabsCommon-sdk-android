package com.tjlabs.tjlabscommon_sdk_android.rfd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import java.util.Collections
import java.util.HashSet


/**
 * TJLabsBluetoothManager
 * 블루투스 스캔 결과를 콜백 인터페이스를 통해 얻을 수 있음
 * 콜백 인터페이스는 최신 스캔 결과와 시간 내 set 을 return 함
 *
 */
class TJLabsBluetoothManager(private val context: Context) {
    // 타이머 동작 콜백 인터페이스
    interface BLETimerListener {
        fun onScanBLEResult(bleScanInfo: BLEScanInfo)
        fun onScanBLESetResult(bleScanInfoSet : MutableSet<BLEScanInfo>)
    }
    var timerListener: BLETimerListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var isRunning = false

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var scanFilters: List<ScanFilter> = emptyList()
    private val scanSettings: ScanSettings = ScanSettings.Builder()
                            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build()
    private var rssMinThreshold = -100
    private var rssMaxThreshold = 0
    private var latestBLEScanInfo = BLEScanInfo()
    private var bleScanInfoSet : MutableSet<BLEScanInfo> = Collections.synchronizedSet(HashSet())
    private val scanCallbackClass = ScanCallbackClass()
    private var bleScanInfoSetTimeLimitNanos = 1000 * 1000 * 1000

    companion object{
        const val TJLABS_WARD_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"
    }
    /**
     * 퍼미션 검사
     */
    fun checkPermissionsAndBleState(): Boolean {
        // 권한 확인
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 11 이하
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val hasPermissions = permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            println("Required permissions are not granted.")
            return false
        }

        // BLE 활성화 상태 확인
        if (bluetoothAdapter?.isEnabled != true) {
            println("Bluetooth is not enabled.")
            return false
        }

        return true
    }

    /**
     * 스캔 필터 설정
     */
    fun setScanFilters(filters: List<ScanFilter>) {
        scanFilters = filters
    }

    fun setRssMinThreshold(threshold : Int = -100) {
        rssMinThreshold = threshold
    }

    fun setRssMaxThreshold(threshold : Int = 0) {
        rssMaxThreshold = threshold
    }

    fun setBleScanInfoSetTimeLimitNanos(nanoSec : Int = 1000 * 1000 * 1000) {
        bleScanInfoSetTimeLimitNanos = nanoSec
    }
    fun getBleScanInfoSetTimeLimitNanos() : Int {
        return bleScanInfoSetTimeLimitNanos
    }

    fun getBleScanInfoSetTimeLimitMillis() : Long {
        return (bleScanInfoSetTimeLimitNanos / ( 1000 * 1000 )).toLong()
    }

    fun startScan() {
        // 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("BLUETOOTH_SCAN permission is required.")
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("ACCESS_FINE_LOCATION permission is required.")
            }
        }

        // BLE 활성화 상태 확인
        if (bluetoothAdapter?.isEnabled != true) {
            throw IllegalStateException("Bluetooth is not enabled.")
        }

        // 스캔 시작
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallbackClass)
        getBLEScanResult()
    }

    fun stopScan() {
        // BLE 활성화 상태 확인
        if (bluetoothAdapter?.isEnabled != true) {
            throw IllegalStateException("Bluetooth is not enabled.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("BLUETOOTH_SCAN permission is required.")
            } else {
                bluetoothLeScanner?.stopScan(scanCallbackClass)
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("BLUETOOTH_SCAN permission is required.")

            } else {
                bluetoothLeScanner?.stopScan(scanCallbackClass)

            }
        }

        if (!isRunning) return // 실행 중이 아니면 무시
        isRunning = false
        timerRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
        timerRunnable = null
    }

    private fun getBLEScanResult() {
        isRunning = true
        val runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                bleScanInfoSet = TJLabsBluetoothFunctions.removeBLEScanInfoSetOlderThan(bleScanInfoSet,
                    SystemClock.elapsedRealtimeNanos() - bleScanInfoSetTimeLimitNanos)
                timerListener?.onScanBLESetResult(bleScanInfoSet)
                handler.postDelayed(this, getBleScanInfoSetTimeLimitMillis())
            }
        }
        timerRunnable = runnable
        handler.postDelayed(runnable, getBleScanInfoSetTimeLimitMillis())
    }

    inner class ScanCallbackClass : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.scanRecord?.let{ scanRecord ->
                if ((rssMinThreshold < result.rssi) && (result.rssi < rssMaxThreshold)) {
                    scanRecord.deviceName?.let{deviceName ->
                        synchronized(bleScanInfoSet){
                            latestBLEScanInfo = BLEScanInfo(deviceName, result.rssi, result.timestampNanos)
                            bleScanInfoSet.add(BLEScanInfo(deviceName, result.rssi, result.timestampNanos))
                            timerListener?.onScanBLEResult(latestBLEScanInfo)
                        }
                    }
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
        }
    }
}