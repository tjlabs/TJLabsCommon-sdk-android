package com.tjlabs.tjlabscommon_sdk_android.rfd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions.getCurrentTimeInMilliseconds
import java.util.Collections
import java.util.HashSet

class TJLabsBluetoothManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var scanFilters: List<ScanFilter> = emptyList()
    private var scanSettings: ScanSettings = ScanSettings.Builder().build()
    private var scanCallback: ScanCallback? = null


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
    fun setFilters(filters: List<ScanFilter>, settings: ScanSettings) {
        scanFilters = filters
        scanSettings = settings
    }




//
//
//    private val beaconInfoSet : MutableSet<BeaconInfo> = Collections.synchronizedSet(HashSet())
//    private lateinit var currentScanResult : BeaconInfo
//    private var bleScanSetting : ScanSettings? = null
//    var beaconMap = mutableMapOf<String, BeaconInfo>() //스캔한 비콘들 닮을 리스트
//
//    var bleDictionary = mutableMapOf<String, MutableList<MutableList<Double>>>()
//    var BLE_VALID_TIME : Double = 1000.0
//
//    var onScanTimeCheck = 0L
//    var saveRVDDataString = ""
//    var bluetoothReady = false
//    var rssBias = 0
//    var rssScaleFactor = 1f
//
//    var rfdIndex = 0
//    var scanModeStr = ""
//    var bleLastScannedTime = getCurrentTimeInMilliseconds()
//    var bleDiscoveredTime = 0L
//
//    companion object {
//        const val RFD_SCAN_LIMIT_TIME_MILLIS = 1000
//        const val RFD_SCAN_LIMIT_TIME_NANOS = RFD_SCAN_LIMIT_TIME_MILLIS * 1000 * 1000
//    }
//
//    fun checkBleScan() : Pair<Boolean, String>{
//        bluetoothAdapter = bluetoothManager.adapter
//        if (bluetoothAdapter != null) {
//            val isEnable = bluetoothAdapter?.isEnabled
//            if (isEnable == false) {
//                return Pair(isEnable, "" + "" + " Deactivation")
//            } else {
//                bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
//                bleScanSetting =
//                    ScanSettings.Builder()
//                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
//                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
//                        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
//                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
//                        .build()
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                    if (ActivityCompat.checkSelfPermission(
//                            application,
//                            Manifest.permission.BLUETOOTH_SCAN
//                        ) != PackageManager.PERMISSION_GRANTED
//                    ) {
//                        return Pair(false, "Please Check BLUETOOTH Permission")
//
//                    } else {
//                        bluetoothReady = true
//                        return Pair(true, "BLUETOOTH Activation VERSION >= S")
//                    }
//                } else {
//                    if (ActivityCompat.checkSelfPermission(
//                            application,
//                            Manifest.permission.BLUETOOTH
//                        ) != PackageManager.PERMISSION_GRANTED
//                    ) {
//                        return Pair(false, "Please Check BLUETOOTH Permission")
//                    } else {
//                        Log.d("BLUETOOTH_SCAN", "Start Bluetooth scan VERSION < S")
//                        bluetoothReady = true
//                        return Pair(true, "BLUETOOTH Activation VERSION < S")
//                    }
//                }
//            }
//        }else{
//            return Pair(false, "BLUETOOTH Adapter is null")
//        }
//    }
//
//
//    private inner class ScanCallbackClass : ScanCallback() {
//        @SuppressLint("MissingPermission")
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            result.scanRecord?.let{ scanRecord ->
//                val bleName = scanRecord.deviceName
//                val RSSI_MIN_VALUE = -99
//                val RSSI_MAX_VALUE = -40
//
//                if ((RSSI_MIN_VALUE < result.rssi) && (result.rssi < RSSI_MAX_VALUE)) {
//                    scanRecord.deviceName?.let{deviceName ->
//                        synchronized(beaconInfoSet){
//                            removeBeaconInfoSetOlderThan(beaconInfoSet, SystemClock.elapsedRealtimeNanos() - RFD_SCAN_LIMIT_TIME_NANOS)
//
//                            onScanTimeCheck = System.currentTimeMillis()
//                            val rssValue = (result.rssi * rssScaleFactor)
//                            bleLastScannedTime = getCurrentTimeInMilliseconds()
//                            bleDiscoveredTime = getCurrentTimeInMilliseconds()
//                            beaconInfoSet.add(BeaconInfo(deviceName, rssValue.toInt(), result.timestampNanos))
//                            Log.d("ScanResult","${BeaconInfo(deviceName, rssValue.toInt(), result.timestampNanos)}")
//                            val scanMode = bleScanSetting?.scanMode
//                            scanModeStr = ""
//
//                            if (scanMode == 1)
//                            {
//                                scanModeStr = "SCAN_MODE_BALANCED"
//                            }else if(scanMode == 2)
//                            {
//                                scanModeStr = "SCAN_MODE_LOW_LATENCY"
//                            }else if(scanMode == 0)
//                            {
//                                scanModeStr = "SCAN_MODE_LOW_POWER"
//                            }else if(scanMode == -1)
//                            {
//                                scanModeStr = "SCAN_MODE_OPPORTUNISTIC"
//                            }
//                        }
//                    }
//
//                    val bleDataToAdd = mutableListOf(result.rssi.toDouble(), System.currentTimeMillis().toDouble())
//                    if (bleName != null) {
//                        if (bleDictionary.contains(bleName)) {
//                            val value = bleDictionary[bleName]!!
//                            value.add(bleDataToAdd)
//                            bleDictionary[bleName] = value
//
//                        } else {
//                            bleDictionary[bleName] = mutableListOf(bleDataToAdd)
//                        }
//                    }
//                }
//            }
//        }
//
//        override fun onBatchScanResults(results: List<ScanResult>) {
//        }
//    }
//
//
//    private fun removeBeaconInfoSetOlderThan(beaconInfoSet: MutableSet<BeaconInfo>, elapsedRealtimeNano: Long) {
//        beaconInfoSet.removeAll { it.timestampNanos < elapsedRealtimeNano }
//    }
//    private fun filterBeaconInfoSetNewerThan(beaconInfoSet: HashSet<BeaconInfo>, elapsedRealtimeNano: Long): HashSet<BeaconInfo> {
//        return beaconInfoSet.filter { it.timestampNanos > elapsedRealtimeNano }.toHashSet()
//    }
//
//    fun initBle() : Pair<Boolean, String>{
//        return Pair(true, "")
//    }
//
//    fun getIsBLEReady(): Pair<Boolean, String>{
//        return checkBleScan()
//    }

}