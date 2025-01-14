package com.tjlabs.tjlabscommon_sdk_android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tjlabs.tjlabscommon_sdk_android.rfd.RFDGenerator
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import com.tjlabs.tjlabscommon_sdk_android.uvd.UVDGenerator
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserVelocity

class MainActivity : AppCompatActivity() {
    private lateinit var rfdGenerator : RFDGenerator
    private lateinit var uvdGenerator: UVDGenerator

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

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)


        rfdGenerator = RFDGenerator(application, "temp")
        uvdGenerator = UVDGenerator(application, "temp")

        btnStart.setOnClickListener {
            rfdGenerator.generateRfd(1000, 1000, -100, -40, getPressure = {0f}, object : RFDGenerator.RFDCallback{
                override fun onRfdResult(rfd: ReceivedForce) {
                    Log.d("BLETimerListener", "rfd : $rfd")
                }

                override fun onRfdError(code : Int, msg : String) {
                    Log.d("BLETimerListener", "error : $msg")
                }
            })

            uvdGenerator.generateUvd(maxPDRStepLength = 0.7f, callback = object : UVDGenerator.UVDCallback{
                override fun onUvdResult(mode: UserMode, uvd: UserVelocity) {
                    Log.d("UVDVelocityResult", "mode : $mode // uvd : $uvd")
                }

                override fun onPressureResult(hPa: Float) {
                    Log.d("UVDVelocityResult", hPa.toString())
                }

                override fun onVelocityResult(kmPh: Float) {
                    Log.d("UVDVelocityResult", kmPh.toString())
                }

                override fun onUvdPauseMillis(time: Long) {
                    Log.d("UVDPauseMillis", time.toString())
                }

                override fun onUvdError(error: String) {
                }
            })

        }

        btnStop.setOnClickListener {
            rfdGenerator.stopRfdGeneration()
            uvdGenerator.stopUvdGeneration()
        }
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