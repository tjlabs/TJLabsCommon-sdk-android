package com.tjlabs.tjlabscommon_sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tjlabs.tjlabscommon_sdk_android.rfd.RFDGenerator
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import com.tjlabs.tjlabscommon_sdk_android.rfd.ScanMode
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataManager
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions
import com.tjlabs.tjlabscommon_sdk_android.uvd.UVDGenerator
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserVelocity

class MainActivity : AppCompatActivity() {
    private lateinit var rfdGenerator: RFDGenerator
    private lateinit var uvdGenerator: UVDGenerator

    private val requiredPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private val multiplePermissionsCode = 100
    private var pressure = 0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnStartSimul = findViewById<Button>(R.id.btnStartSimul)
        val userId = "temp"
        val initStartTime = 1775451068715L
        rfdGenerator = RFDGenerator(application, userId)
        uvdGenerator = UVDGenerator(application, userId)

        btnStartSimul.setOnClickListener {
            Log.d("CheckJsonData", "start")
            rfdGenerator.generateSimulationRfdFromJson(
                userId,
                initStartTime.toString(),
                callback = object : RFDGenerator.RFDCallback {
                    override fun onRfdResult(rfd: ReceivedForce) {
                        Log.d("CheckJsonData(sim)", "rfd : $rfd")
                    }

                    override fun onRfdError(code: Int, msg: String) {
                    }

                    override fun onRfdEmptyMillis(time: Long) {
                    }
                }
            )
            uvdGenerator.setUserMode(UserMode.MODE_AUTO)
            uvdGenerator.generateSimulationUvdFromJson(
                userId,
                initStartTime.toString(),
                callback = object : UVDGenerator.UVDCallback {
                    override fun onUvdResult(mode: UserMode, uvd: UserVelocity) {
                        Log.d("CheckJsonData(sim)", "mode : $mode // uvd : $uvd")
                    }

                    override fun onPressureResult(hPa: Float) {
                        pressure = hPa
                    }

                    override fun onVelocityResult(kmPh: Float) {
                    }

                    override fun onMagNormSmoothingVarResult(value: Float) {
                    }

                    override fun onUvdPauseMillis(time: Long) {
                    }

                    override fun onUvdError(error: String) {
                    }
                }
            )
        }

        btnStart.setOnClickListener {
            val saveData = true
            JupiterDataManager.setServiceStartTime(TJLabsUtilFunctions.getCurrentTimeInMilliseconds())
            JupiterDataManager.addEvent(
                application,
                userId,
                JupiterDataManager.JupiterEventCode.START_SERVICE
            )
            rfdGenerator.setScanMode(ScanMode.WARD_SEI_SCAN)
            rfdGenerator.generateRfd(
                -100,
                -40,
                getPressure = { pressure },
                isSaveData = saveData,
                object : RFDGenerator.RFDCallback {
                    override fun onRfdResult(rfd: ReceivedForce) {
                        Log.d("CheckJsonData", "rfd : $rfd")
                    }

                    override fun onRfdError(code: Int, msg: String) {
                        Log.d("BLETimerListener", "error : $msg")
                    }

                    override fun onRfdEmptyMillis(time: Long) {
                        Log.d("BLETimerListener", "time : $time")
                    }
                }
            )

            uvdGenerator.setUserMode(UserMode.MODE_PEDESTRIAN)
            uvdGenerator.generateUvd(
                maxPDRStepLength = 0.7f,
                isSaveData = saveData,
                callback = object : UVDGenerator.UVDCallback {
                    override fun onUvdResult(mode: UserMode, uvd: UserVelocity) {
                        Log.d("CheckJsonData", "mode : $mode // uvd : $uvd")
                    }

                    override fun onPressureResult(hPa: Float) {
                        pressure = hPa
                    }

                    override fun onVelocityResult(kmPh: Float) {
                        Log.d("UVDVelocityResult", "kmPh : $kmPh")
                    }

                    override fun onMagNormSmoothingVarResult(value: Float) {
                    }

                    override fun onUvdPauseMillis(time: Long) {
                    }

                    override fun onUvdError(error: String) {
                    }
                }
            )
        }

        btnStop.setOnClickListener {
            rfdGenerator.stopRfdGeneration()
            uvdGenerator.stopUvdGeneration()
            JupiterDataManager.addEvent(
                application,
                userId,
                JupiterDataManager.JupiterEventCode.STOP_SERVICE
            )
        }
    }

    private fun checkPermissions() {
        val rejectedPermissionList = ArrayList<String>()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                rejectedPermissionList.add(permission)
            }
        }

        if (rejectedPermissionList.isNotEmpty()) {
            val array = arrayOfNulls<String>(rejectedPermissionList.size)
            ActivityCompat.requestPermissions(
                this,
                rejectedPermissionList.toArray(array),
                multiplePermissionsCode
            )
        }
    }
}
