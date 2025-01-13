package com.tjlabs.tjlabscommon_sdk_android.rfd

import android.Manifest
import android.annotation.SuppressLint
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
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions
import java.util.Collections
import java.util.HashSet


/**
 * TJLabsBluetoothManager
 * 블루투스 스캔 결과를 콜백 인터페이스를 통해 얻을 수 있음
 * 콜백 인터페이스는 최신 스캔 결과와 시간 내 set 을 return 함
 *
 */
internal class TJLabsBluetoothManager(private val context: Context) {
    // 타이머 동작 콜백 인터페이스
    interface ScanResultListener {
        fun onScanBleSetResultOrNull(bleScanInfoSet : MutableSet<BLEScanInfo>)
    }
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
    private var minRssiThreshold = -100
    private var maxRssiThreshold = 0
    private var bleScanInfoSet : MutableSet<BLEScanInfo> = Collections.synchronizedSet(HashSet())
    private val scanCallbackClass = ScanCallbackClass()
    private var bleScanInfoSetTimeLimitNanos : Long = 1000 * 1000 * 1000

    companion object{
        const val TJLABS_WARD_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"
    }
    /**
     * 퍼미션 검사
     */
    fun checkPermissions() : Pair<Boolean, String> {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val hasPermissions = permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        return if (!hasPermissions) {
            Pair(false, "Required permissions are not granted.")
        } else{
            Pair(true, "")
        }
    }

    fun checkBleActivation() : Pair<Boolean, String> {
        return if (bluetoothAdapter?.isEnabled != true) {
            Pair(false, "Bluetooth is not enabled.")
        } else {
            Pair(true, "")
        }
    }

    fun checkBleAvailable() : Pair<Boolean, String> {
        return if (bluetoothAdapter == null) {
            Pair(false, "BLUETOOTH not supported device")
        } else {
            Pair(true, "")
        }
    }
    
    /**
     * 스캔 필터 설정
     */
    fun setScanFilters(filters: List<ScanFilter>) {
        scanFilters = filters
    }

    fun setMinRssiThreshold(threshold : Int = -100) {
        minRssiThreshold = threshold
    }

    fun setMaxRssiThreshold(threshold : Int = 0) {
        maxRssiThreshold = threshold
    }

    fun setBleScanInfoSetTimeLimitNanos(nanoSec : Long = 1000 * 1000 * 1000) {
        bleScanInfoSetTimeLimitNanos = nanoSec
    }

    @SuppressLint("MissingPermission")
    fun startScan() : Pair<Boolean, String> {
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallbackClass)
        return Pair(true, "Success Start Scan")
    }

    fun stopScan() : Pair<Boolean, String> {
        // BLE 활성화 상태 확인
        if (bluetoothAdapter?.isEnabled != true) {
            return Pair(false, "Bluetooth is not enabled.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return Pair(false, "BLUETOOTH_SCAN permission is required.")
            } else {
                bluetoothLeScanner?.stopScan(scanCallbackClass)
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return Pair(false, "BLUETOOTH_SCAN permission is required.")

            } else {
                bluetoothLeScanner?.stopScan(scanCallbackClass)

            }
        }

        if (!isRunning)  return Pair(false, "Bluetooth is not enabled.")
        // 실행 중이 아니면 무시
        isRunning = false
        timerRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
        timerRunnable = null
        return Pair(true, "Success Stop Scan")
    }

    fun getBleScanResult(callback : ScanResultListener) {
        isRunning = true
        val runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                bleScanInfoSet = TJLabsBluetoothFunctions.removeBleScanInfoSetOlderThan(bleScanInfoSet,
                    SystemClock.elapsedRealtimeNanos() - bleScanInfoSetTimeLimitNanos)
                callback.onScanBleSetResultOrNull(bleScanInfoSet)
                handler.postDelayed(this, TJLabsUtilFunctions.nanos2millis(bleScanInfoSetTimeLimitNanos))
            }
        }
        timerRunnable = runnable
        handler.postDelayed(runnable, TJLabsUtilFunctions.nanos2millis(bleScanInfoSetTimeLimitNanos))
    }

    inner class ScanCallbackClass : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.scanRecord?.let{ scanRecord ->
                if ((minRssiThreshold < result.rssi) && (result.rssi < maxRssiThreshold)) {
                    scanRecord.deviceName?.let{deviceName ->
                        synchronized(bleScanInfoSet){
                            bleScanInfoSet.add(BLEScanInfo(deviceName, result.rssi, result.timestampNanos))
                        }
                    }
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
        }
    }
}