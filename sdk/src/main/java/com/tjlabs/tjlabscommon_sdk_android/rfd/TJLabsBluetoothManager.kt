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
import android.os.ParcelUuid
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
        const val DEFAULT_SEI_BEACON_NAME_PREFIX = "NI-011-0000"
        // Pattern matches names like TJ-00CB, TJ-0ACB, TJ-FFCB — the two chars between "TJ-"
        // and "CB" are hex (0-9, A-F, case-insensitive), not decimal digits.
        const val DEFAULT_IBEACON_NAME_KEYWORD = "TJ-[0-9A-Fa-f]{2}CB"

        private fun compileBeaconRegex(pattern: String): Regex =
            runCatching { Regex(pattern) }
                .getOrElse { Regex(Regex.escape(pattern)) }
    }
    private var scanMode: ScanMode = ScanMode.ONLY_WARD_SCAN
    private var wardServiceParcelUuid: ParcelUuid? = parseParcelUuidOrNull(TJLABS_WARD_UUID)
    private var seiBeaconNamePrefix: String = DEFAULT_SEI_BEACON_NAME_PREFIX
    private var iBeaconNameKeyword: String = DEFAULT_IBEACON_NAME_KEYWORD
    private var iBeaconNameRegex: Regex = compileBeaconRegex(DEFAULT_IBEACON_NAME_KEYWORD)
    /**
     * 퍼미션 검사
     */
    fun checkPermissions() : Pair<Boolean, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBlePermissions =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

            val hasFineLocation =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasLocationPermission = hasFineLocation || hasCoarseLocation

            if (!hasBlePermissions) {
                Pair(false, "Required BLE permission(BLUETOOTH_SCAN) is not granted.")
            } else if (!hasLocationPermission) {
                Pair(false, "Location permission(ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION) is required.")
            } else {
                Pair(true, "")
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            val hasPermissions = permissions.all {
                ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (!hasPermissions) {
                Pair(false, "Required permissions are not granted.")
            } else {
                Pair(true, "")
            }
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

    fun setScanMode(scanMode: ScanMode) {
        this.scanMode = scanMode
    }

    fun setWardScanSpec(serviceUuid: String? = TJLABS_WARD_UUID) {
        wardServiceParcelUuid = parseParcelUuidOrNull(serviceUuid)
    }

    fun setSeiScanSpec(beaconNamePrefix: String = DEFAULT_SEI_BEACON_NAME_PREFIX) {
        seiBeaconNamePrefix = beaconNamePrefix
    }

    fun setWardSeiScanSpec(
        wardServiceUuid: String? = TJLABS_WARD_UUID,
        seiBeaconNamePrefix: String = DEFAULT_SEI_BEACON_NAME_PREFIX
    ) {
        setWardScanSpec(wardServiceUuid)
        setSeiScanSpec(seiBeaconNamePrefix)
    }

    fun setIBeaconScanSpec(nameKeyword: String = DEFAULT_IBEACON_NAME_KEYWORD) {
        iBeaconNameKeyword = nameKeyword
        iBeaconNameRegex = compileBeaconRegex(nameKeyword)
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
        return try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallbackClass)
            Pair(true, "Success Start Scan")
        } catch (e: SecurityException) {
            Pair(false, "Failed to start scan due to missing permission: ${e.message}")
        } catch (e: Exception) {
            Pair(false, "Failed to start scan: ${e.message}")
        }
    }

    fun stopScan(restart : Boolean = false) : Pair<Boolean, String> {
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

        if (!restart) {
            isRunning = false
            timerRunnable?.let { runnable ->
                handler.removeCallbacks(runnable)
            }
            timerRunnable = null
        }

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
                handler.postDelayed(this, 200) // 0.2초 마다 검사
            }
        }
        timerRunnable = runnable
//        handler.postDelayed(runnable, TJLabsUtilFunctions.nanos2millis(bleScanInfoSetTimeLimitNanos))
        handler.postDelayed(runnable, 0) //시작 딜레이 없음

    }

    inner class ScanCallbackClass : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.scanRecord?.let{ scanRecord ->
                if ((minRssiThreshold < result.rssi) && (result.rssi < maxRssiThreshold)) {
                    if (!isScanModeMatched(scanRecord)) return
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

    private fun isScanModeMatched(scanRecord: android.bluetooth.le.ScanRecord): Boolean {
        return when (scanMode) {
            ScanMode.NO_FILTER_SCAN -> true
            ScanMode.ONLY_WARD_SCAN -> hasServiceUuid(scanRecord, wardServiceParcelUuid)
            ScanMode.ONLY_SEI_SCAN -> isSeiMatched(scanRecord)
            ScanMode.WARD_SEI_SCAN -> {
                hasServiceUuid(scanRecord, wardServiceParcelUuid) || isSeiMatched(scanRecord)
            }
            ScanMode.ONLY_IBEACON_SCAN -> isIBeaconMatched(scanRecord)
        }
    }

    private fun isSeiMatched(scanRecord: android.bluetooth.le.ScanRecord): Boolean {
        return scanRecord.deviceName?.startsWith(seiBeaconNamePrefix) == true
    }

    private fun isIBeaconMatched(scanRecord: android.bluetooth.le.ScanRecord): Boolean {
        val isIBeaconFrame = hasIBeaconManufacturerData(scanRecord)
        val name = scanRecord.deviceName ?: return false
        val isNameMatched = iBeaconNameRegex.containsMatchIn(name)
        return isIBeaconFrame && isNameMatched
    }

    private fun hasIBeaconManufacturerData(scanRecord: android.bluetooth.le.ScanRecord): Boolean {
        val manufacturerData = scanRecord.manufacturerSpecificData ?: return false
        // Apple company ID (0x004C) + iBeacon prefix (0x02, 0x15)
        val appleData = manufacturerData.get(0x004C) ?: return false
        if (appleData.size < 2) return false
        return appleData[0] == 0x02.toByte() && appleData[1] == 0x15.toByte()
    }

    private fun hasServiceUuid(scanRecord: android.bluetooth.le.ScanRecord, target: ParcelUuid?): Boolean {
        if (target == null) return false
        return scanRecord.serviceUuids?.contains(target) == true ||
                scanRecord.serviceData?.keys?.contains(target) == true
    }

    private fun parseParcelUuidOrNull(uuid: String?): ParcelUuid? {
        return try {
            if (uuid.isNullOrBlank()) null else ParcelUuid.fromString(uuid)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
