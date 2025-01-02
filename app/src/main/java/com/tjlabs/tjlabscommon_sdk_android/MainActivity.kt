package com.tjlabs.tjlabscommon_sdk_android

import android.Manifest
import android.bluetooth.le.ScanFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.tjlabscommon_sdk_android.rfd.BLEScanInfo
import com.tjlabs.tjlabscommon_sdk_android.rfd.TJLabsBluetoothManager

class MainActivity : AppCompatActivity() {
    lateinit var tjLabsBluetoothManager: TJLabsBluetoothManager
    private val requiredPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        }

    private val multiplePermissionsCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tjLabsBluetoothManager = TJLabsBluetoothManager(application)
        tjLabsBluetoothManager.checkPermissionsAndBleState()
        tjLabsBluetoothManager.setScanFilters(
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(TJLabsBluetoothManager.TJLABS_WARD_UUID))
                    .build()
            )
        )
        tjLabsBluetoothManager.setRssMaxThreshold(-60)
        tjLabsBluetoothManager.setRssMinThreshold(-80)

        tjLabsBluetoothManager.scanResultListener =
            object : TJLabsBluetoothManager.BleScanResultListener {
                override fun onScanBLEResult(deviceName: String?, rssi: Int, timestampNanos: Long) {
                    Log.d("BLEScanResult", "device Name : $deviceName // rssi : $rssi")
                }

                override fun onScanBLESetResult(bleScanInfoSet: MutableSet<BLEScanInfo>) {
                    Log.d("BLEScanSetResult", "bleScanInfoSet: $bleScanInfoSet")

                }
            }

        tjLabsBluetoothManager.startScan()


    }

    private fun checkPermissions() {
        val rejectedPermissionList = ArrayList<String>()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                rejectedPermissionList.add(permission)
            }
        }
        //거절된 퍼미션이 있다면...
        if (rejectedPermissionList.isNotEmpty()) {
            //권한 요청!
            val array = arrayOfNulls<String>(rejectedPermissionList.size)
            ActivityCompat.requestPermissions(
                this,
                rejectedPermissionList.toArray(array),
                multiplePermissionsCode
            )
        }
    }
}