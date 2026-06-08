package com.tjlabs.tjlabscommon_sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tjlabs.tjlabscommon_sdk_android.rfd.RFDGenerator
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import com.tjlabs.tjlabscommon_sdk_android.rfd.ScanMode
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataManager
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions
import com.tjlabs.tjlabscommon_sdk_android.uvd.UVDGenerator
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserVelocity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var rfdGenerator: RFDGenerator
    private lateinit var uvdGenerator: UVDGenerator
    private lateinit var spinnerScanMode: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var tvUvdLatest: TextView
    private lateinit var rvRfdResults: RecyclerView
    private lateinit var switchSaveData: SwitchCompat
    private lateinit var rgUserMode: RadioGroup
    private lateinit var rfdAdapter: RfdScanAdapter

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkPermissions()
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnStartSimul = findViewById<Button>(R.id.btnStartSimul)
        spinnerScanMode = findViewById(R.id.spinnerScanMode)
        tvStatus = findViewById(R.id.tvStatus)
        tvUvdLatest = findViewById(R.id.tvUvdLatest)
        rvRfdResults = findViewById(R.id.rvRfdResults)
        switchSaveData = findViewById(R.id.switchSaveData)
        rgUserMode = findViewById(R.id.rgUserMode)

        val modeNames = ScanMode.values().map { it.name }
        spinnerScanMode.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeNames)
        spinnerScanMode.setSelection(modeNames.indexOf(ScanMode.ONLY_WARD_SCAN.name))

        rfdAdapter = RfdScanAdapter()
        rvRfdResults.layoutManager = LinearLayoutManager(this)
        rvRfdResults.adapter = rfdAdapter

        val userId = "temp"
        val initStartTime = 1775451068715L
        rfdGenerator = RFDGenerator(application, userId)
        uvdGenerator = UVDGenerator(application, userId)

        btnStartSimul.setOnClickListener {
            rfdGenerator.generateSimulationRfdFromJson(
                userId,
                initStartTime.toString(),
                callback = object : RFDGenerator.RFDCallback {
                    override fun onRfdResult(rfd: ReceivedForce) {
                        Log.d("MainActivity", "sim rfd : $rfd")
                        updateRfdRows(rfd)
                    }

                    override fun onRfdError(code: Int, msg: String) {
                        updateStatus("Simulation RFD error: $msg")
                    }

                    override fun onRfdEmptyMillis(time: Long) = Unit
                }
            )

            val selectedUserMode = getSelectedUserMode()
            uvdGenerator.setUserMode(selectedUserMode)
            uvdGenerator.generateSimulationUvdFromJson(
                userId,
                initStartTime.toString(),
                callback = object : UVDGenerator.UVDCallback {
                    override fun onUvdResult(mode: UserMode, uvd: UserVelocity) {
                        updateUvdLatest(mode, uvd)
                    }

                    override fun onPressureResult(hPa: Float) {
                        pressure = hPa
                    }

                    override fun onVelocityResult(kmPh: Float) = Unit
                    override fun onMagNormSmoothingVarResult(value: Float) = Unit
                    override fun onUvdPauseMillis(time: Long) = Unit
                    override fun onUvdError(error: String) = Unit
                }
            )
            updateStatus("Simulation started (${selectedUserMode.name})")
        }

        btnStart.setOnClickListener {
            val saveData = switchSaveData.isChecked
            val selectedMode = ScanMode.values()[spinnerScanMode.selectedItemPosition]
            val selectedUserMode = getSelectedUserMode()

            JupiterDataManager.setServiceStartTime(TJLabsUtilFunctions.getCurrentTimeInMilliseconds())
            JupiterDataManager.addEvent(
                application,
                userId,
                JupiterDataManager.JupiterEventCode.START_SERVICE
            )

            rfdGenerator.setScanMode(selectedMode)
            rfdGenerator.generateRfd(
                -100,
                -40,
                getPressure = { pressure },
                isSaveData = saveData,
                callback = object : RFDGenerator.RFDCallback {
                    override fun onRfdResult(rfd: ReceivedForce) {
                        updateRfdRows(rfd)
                    }

                    override fun onRfdError(code: Int, msg: String) {
                        updateStatus("RFD error: $msg")
                    }

                    override fun onRfdEmptyMillis(time: Long) = Unit
                }
            )

            uvdGenerator.setUserMode(selectedUserMode)
            uvdGenerator.generateUvd(
                maxPDRStepLength = 0.7f,
                isSaveData = saveData,
                callback = object : UVDGenerator.UVDCallback {
                    override fun onUvdResult(mode: UserMode, uvd: UserVelocity) {
                        updateUvdLatest(mode, uvd)
                    }

                    override fun onPressureResult(hPa: Float) {
                        pressure = hPa
                    }

                    override fun onVelocityResult(kmPh: Float) {
                        Log.d("MainActivity", "velocity(kmPh): $kmPh")
                    }

                    override fun onMagNormSmoothingVarResult(value: Float) = Unit
                    override fun onUvdPauseMillis(time: Long) = Unit

                    override fun onUvdError(error: String) {
                        updateStatus("UVD error: $error")
                    }
                }
            )
            updateStatus("RFD/UVD started (${selectedMode.name}, ${selectedUserMode.name}, save=$saveData)")
        }

        btnStop.setOnClickListener {
            rfdGenerator.stopRfdGeneration()
            uvdGenerator.stopUvdGeneration()
            JupiterDataManager.addEvent(
                application,
                userId,
                JupiterDataManager.JupiterEventCode.STOP_SERVICE
            )
            updateStatus("Stopped")
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            tvStatus.text = "Status: $message"
        }
    }

    private fun updateUvdLatest(mode: UserMode, uvd: UserVelocity) {
        runOnUiThread {
            tvUvdLatest.text =
                "mode=${mode.name}\nindex=${uvd.index}\nlength=${uvd.length}\nheading=${uvd.heading}\nmobile_time=${uvd.mobile_time}"
        }
    }

    private fun updateRfdRows(rfd: ReceivedForce) {
        val rows = rfd.rfs.entries
            .sortedByDescending { it.value }
            .map { (name, rssi) ->
                RfdScanRow(
                    name = name,
                    rssi = rssi.toInt(),
                    scannedAtMillis = rfd.mobile_time
                )
            }

        runOnUiThread {
            rfdAdapter.submit(rows)
        }
    }

    private fun getSelectedUserMode(): UserMode {
        return when (rgUserMode.checkedRadioButtonId) {
            R.id.rbUserModePdr -> UserMode.MODE_PEDESTRIAN
            R.id.rbUserModeAuto -> UserMode.MODE_AUTO
            else -> UserMode.MODE_VEHICLE
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

private data class RfdScanRow(
    val name: String,
    val rssi: Int,
    val scannedAtMillis: Long
)

private class RfdScanAdapter : RecyclerView.Adapter<RfdScanAdapter.RfdScanViewHolder>() {
    private val items = mutableListOf<RfdScanRow>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun submit(newItems: List<RfdScanRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RfdScanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rfd_scan_result, parent, false)
        return RfdScanViewHolder(view)
    }

    override fun onBindViewHolder(holder: RfdScanViewHolder, position: Int) {
        holder.bind(items[position], dateFormatter)
    }

    override fun getItemCount(): Int = items.size

    class RfdScanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBeaconName: TextView = itemView.findViewById(R.id.tvBeaconName)
        private val tvBeaconRssi: TextView = itemView.findViewById(R.id.tvBeaconRssi)
        private val tvBeaconTime: TextView = itemView.findViewById(R.id.tvBeaconTime)

        fun bind(item: RfdScanRow, formatter: SimpleDateFormat) {
            tvBeaconName.text = item.name
            tvBeaconRssi.text = "RSSI: ${item.rssi}"
            tvBeaconTime.text = "Scanned: ${formatter.format(Date(item.scannedAtMillis))}"
        }
    }
}
