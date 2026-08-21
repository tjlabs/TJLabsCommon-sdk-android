package com.tjlabs.tjlabscommon_sample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tjlabs.tjlabscommon_sample.auth.AuthService
import com.tjlabs.tjlabscommon_sample.network.ApiServices
import com.tjlabs.tjlabscommon_sample.network.SectorOption
import com.tjlabs.tjlabscommon_sample.network.SectorParser
import com.tjlabs.tjlabscommon_sample.network.TenantApi
import com.tjlabs.tjlabscommon_sample.preview.PreviewActivity
import com.tjlabs.tjlabscommon_sample.preview.SessionMeta
import com.tjlabs.tjlabscommon_sample.preview.SessionMetaStore
import com.tjlabs.tjlabscommon_sample.wards.ScannedWardTracker
import com.tjlabs.tjlabscommon_sample.wards.ScannedWardsActivity
import com.tjlabs.tjlabscommon_sdk_android.rfd.PermissionDiagnostic
import com.tjlabs.tjlabscommon_sdk_android.rfd.RFDGenerator
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import com.tjlabs.tjlabscommon_sdk_android.rfd.ScanMode
import com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataManager
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsUtilFunctions
import com.tjlabs.tjlabscommon_sdk_android.uvd.UVDGenerator
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserVelocity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AUTH_LOG_TAG = "Auth"

class MainActivity : AppCompatActivity() {

    private var rfdGenerator: RFDGenerator? = null
    private var uvdGenerator: UVDGenerator? = null

    private lateinit var btnAuth: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnStartSimul: Button
    private lateinit var btnFiles: Button
    private lateinit var btnWards: Button
    private lateinit var btnDiagnose: Button
    private lateinit var spinnerScanMode: Spinner
    private lateinit var spinnerSector: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var tvUvdLatest: TextView
    private lateinit var rvRfdResults: RecyclerView
    private lateinit var switchSaveData: SwitchCompat
    private lateinit var rgUserMode: RadioGroup
    private lateinit var rfdAdapter: RfdScanAdapter

    private val tenantApi: TenantApi by lazy {
        ApiServices.createTenantApi(BuildConfig.USER_BASE_URL)
    }

    private var sectorOptions: List<SectorOption> = emptyList()
    private var selectedSector: SectorOption? = null

    private var tenantUserName: String = ""
    private var serviceStartTime: String = ""
    private var isAuthed = false
    private var isRunning = false
    private var pressure = 0f

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkPermissions()
        setContentView(R.layout.activity_main)

        btnAuth = findViewById(R.id.btnAuth)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnStartSimul = findViewById(R.id.btnStartSimul)
        btnFiles = findViewById(R.id.btnFiles)
        btnWards = findViewById(R.id.btnWards)
        btnDiagnose = findViewById(R.id.btnDiagnose)
        spinnerScanMode = findViewById(R.id.spinnerScanMode)
        spinnerSector = findViewById(R.id.spinnerSector)
        tvStatus = findViewById(R.id.tvStatus)
        tvUvdLatest = findViewById(R.id.tvUvdLatest)
        rvRfdResults = findViewById(R.id.rvRfdResults)
        switchSaveData = findViewById(R.id.switchSaveData)
        rgUserMode = findViewById(R.id.rgUserMode)

        val modeNames = ScanMode.values().map { it.name }
        spinnerScanMode.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeNames)
        spinnerScanMode.setSelection(modeNames.indexOf(ScanMode.WARD_ALL_SCAN.name))

        spinnerSector.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("(auth first)")
        )

        rfdAdapter = RfdScanAdapter()
        rvRfdResults.layoutManager = LinearLayoutManager(this)
        rvRfdResults.adapter = rfdAdapter

        btnAuth.setOnClickListener { performAuth() }
        btnStart.setOnClickListener { startServices() }
        btnStop.setOnClickListener { stopServices() }
        btnStartSimul.setOnClickListener { startSimulation() }
        btnFiles.setOnClickListener { openFiles() }
        btnWards.setOnClickListener { openWards() }
        btnDiagnose.setOnClickListener { showBleScanDiagnose() }

        applyRunningState(false)
        updateStatus("Idle. Auth 후 Start 가능")
    }

    private fun openFiles() {
        val intent = Intent(this, PreviewActivity::class.java)
        selectedSector?.id?.let { intent.putExtra(PreviewActivity.EXTRA_SECTOR_ID, it) }
        startActivity(intent)
    }

    private fun openWards() {
        val intent = Intent(this, ScannedWardsActivity::class.java)
        selectedSector?.let {
            intent.putExtra(ScannedWardsActivity.EXTRA_SECTOR_ID, it.id)
            intent.putExtra(ScannedWardsActivity.EXTRA_SECTOR_DISPLAY, it.display)
        }
        startActivity(intent)
    }

    private fun showBleScanDiagnose() {
        val report = PermissionDiagnostic.diagnoseBleScanReadiness(this)
        val statusIcon = if (report.isReady) "✅ READY" else "❌ NOT READY"
        val details = buildString {
            appendLine("Status: $statusIcon")
            appendLine()
            appendLine("BLUETOOTH_SCAN granted: ${report.hasBluetoothScanPermission}")
            appendLine("Location granted (FINE or COARSE): ${report.hasLocationPermission}")
            appendLine("Manifest 'neverForLocation' flag: ${report.manifestHasNeverForLocationFlag}")
            if (report.problems.isNotEmpty()) {
                appendLine()
                appendLine("── Problems ──")
                report.problems.forEachIndexed { idx, p -> appendLine("${idx + 1}. $p") }
            }
            report.remediation?.let {
                appendLine()
                appendLine("── Remediation ──")
                appendLine(it)
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("BLE Scan Readiness")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
        updateStatus("Diagnose: $statusIcon")
    }

    private fun applyRunningState(running: Boolean) {
        isRunning = running
        btnStart.isEnabled = !running
        btnStartSimul.isEnabled = !running
        btnStop.isEnabled = running
    }

    private fun performAuth() {
        val clientKey = BuildConfig.AUTH_CLIENT_KEY
        val accessKey = BuildConfig.AUTH_ACCESS_KEY
        val secretAccessKey = BuildConfig.AUTH_SECRET_ACCESS_KEY

        Log.d(AUTH_LOG_TAG, "performAuth() BuildConfig inputs:")
        Log.d(AUTH_LOG_TAG, "  AUTH_CLIENT_KEY        = ${maskSecret(clientKey)} (len=${clientKey.length})")
        Log.d(AUTH_LOG_TAG, "  AUTH_ACCESS_KEY        = ${maskSecret(accessKey)} (len=${accessKey.length})")
        Log.d(AUTH_LOG_TAG, "  AUTH_SECRET_ACCESS_KEY = ${maskSecret(secretAccessKey)} (len=${secretAccessKey.length})")
        Log.d(AUTH_LOG_TAG, "  USER_BASE_URL          = ${BuildConfig.USER_BASE_URL}")
        Log.d(AUTH_LOG_TAG, "  REC_BASE_URL           = ${BuildConfig.REC_BASE_URL}")

        if (clientKey.isBlank() || accessKey.isBlank() || secretAccessKey.isBlank()) {
            Log.w(AUTH_LOG_TAG, "performAuth() aborted: missing local.properties keys")
            updateStatus("local.properties 에 AUTH_CLIENT_KEY/AUTH_ACCESS_KEY/AUTH_SECRET_ACCESS_KEY 필요")
            return
        }

        updateStatus("Auth 진행 중...")
        AuthService.signIn(
            application = application,
            clientSecret = clientKey,
            accessKey = accessKey,
            secretAccessKey = secretAccessKey
        ) { result ->
            runOnUiThread {
                Log.d(AUTH_LOG_TAG, "performAuth() completion: success=${result.success} code=${result.code} tenant='${result.tenantName}' user='${result.tenantUserName}'")
                if (!result.success) {
                    isAuthed = false
                    updateStatus("Auth 실패 code=${result.code}")
                    return@runOnUiThread
                }
                isAuthed = true
                tenantUserName = result.tenantUserName
                updateStatus("Auth 성공 tenant=${result.tenantName} user=${result.tenantUserName}")
                loadSectors()
            }
        }
    }

    private fun loadSectors() {
        Log.d(AUTH_LOG_TAG, "loadSectors() requesting bearer token...")
        val bearerCallback: (String?) -> Unit = { bearer ->
            if (bearer.isNullOrBlank()) {
                Log.w(AUTH_LOG_TAG, "loadSectors() aborted: bearer token is null/blank")
                runOnUiThread { updateStatus("토큰 없음 - 섹터 조회 실패") }
            } else {
                Log.d(AUTH_LOG_TAG, "loadSectors() fetching sectors version=${ApiServices.TENANT_VERSION} from ${BuildConfig.USER_BASE_URL}")
                lifecycleScope.launch(Dispatchers.IO) {
                    val response = runCatching {
                        tenantApi.getMySectors(ApiServices.TENANT_VERSION, bearer)
                    }.getOrElse {
                        Log.e(AUTH_LOG_TAG, "loadSectors() exception: ${it.message}", it)
                        withContext(Dispatchers.Main) { updateStatus("섹터 조회 오류: ${it.message}") }
                        return@launch
                    }
                    Log.d(AUTH_LOG_TAG, "loadSectors() HTTP ${response.code()} successful=${response.isSuccessful}")
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            updateStatus("섹터 조회 실패 HTTP ${response.code()}")
                        }
                        return@launch
                    }
                    val options = SectorParser.parse(response.body())
                    Log.d(AUTH_LOG_TAG, "loadSectors() parsed ${options.size} sector(s): ${options.joinToString { "${it.id}:${it.display}" }}")
                    withContext(Dispatchers.Main) { applySectors(options) }
                }
            }
        }
        AuthService.bearerToken(bearerCallback)
    }

    private fun maskSecret(value: String): String {
        if (value.isEmpty()) return "<empty>"
        if (value.length <= 8) return "*".repeat(value.length)
        return value.take(4) + "*".repeat(value.length - 8) + value.takeLast(4)
    }

    private fun applySectors(options: List<SectorOption>) {
        sectorOptions = options
        if (options.isEmpty()) {
            spinnerSector.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("(no sectors)")
            )
            selectedSector = null
            updateStatus("섹터 없음")
            return
        }
        spinnerSector.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options.map { it.display }
        )
        spinnerSector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSector = sectorOptions.getOrNull(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        selectedSector = options.first()
        updateStatus("섹터 ${options.size}개 로드됨")
    }

    private fun ensureGenerators() {
        if (rfdGenerator == null) rfdGenerator = RFDGenerator(application, tenantUserName)
        if (uvdGenerator == null) uvdGenerator = UVDGenerator(application, tenantUserName)
    }

    private fun startServices() {
        if (isRunning) return
        if (!isAuthed || tenantUserName.isBlank()) {
            updateStatus("먼저 Auth 를 완료하세요")
            return
        }
        val sector = selectedSector
        if (sector == null) {
            updateStatus("섹터를 먼저 선택하세요")
            return
        }

        val saveData = switchSaveData.isChecked
        val selectedMode = ScanMode.values()[spinnerScanMode.selectedItemPosition]
        val selectedUserMode = getSelectedUserMode()

        serviceStartTime = TJLabsUtilFunctions.getCurrentTimeInMilliseconds().toString()
        JupiterDataManager.setServiceStartTime(serviceStartTime.toLong())
        JupiterDataManager.addEvent(
            application,
            tenantUserName,
            JupiterDataManager.JupiterEventCode.START_SERVICE
        )
        SessionMetaStore.write(
            application,
            SessionMeta(
                userId = tenantUserName,
                serviceStartTime = serviceStartTime,
                sectorId = sector.id,
                sectorDisplay = sector.display,
                scanMode = selectedMode.name,
                userMode = selectedUserMode.name,
                saveData = saveData,
                simulated = false
            )
        )

        ensureGenerators()
        val rfd = rfdGenerator!!
        val uvd = uvdGenerator!!

        rfd.setScanMode(selectedMode)
        rfd.generateRfd(
            -100,
            -40,
            getPressure = { pressure },
            isSaveData = saveData,
            callback = object : RFDGenerator.RFDCallback {
                override fun onRfdResult(rfd: ReceivedForce) = updateRfdRows(rfd)
                override fun onRfdError(code: Int, msg: String) = updateStatus("RFD error: $msg")
                override fun onRfdEmptyMillis(time: Long) = Unit
            }
        )

        uvd.setUserMode(selectedUserMode)
        uvd.generateUvd(
            maxPDRStepLength = 0.7f,
            isSaveData = saveData,
            callback = object : UVDGenerator.UVDCallback {
                override fun onUvdResult(mode: UserMode, uvd: UserVelocity) = updateUvdLatest(mode, uvd)
                override fun onPressureResult(hPa: Float) { pressure = hPa }
                override fun onVelocityResult(kmPh: Float) {
                    Log.d("MainActivity", "velocity(kmPh): $kmPh")
                }
                override fun onMagNormSmoothingVarResult(value: Float) = Unit
                override fun onUvdPauseMillis(time: Long) = Unit
                override fun onUvdError(error: String) = updateStatus("UVD error: $error")
            }
        )
        applyRunningState(true)
        updateStatus(
            "Start sector=${sector.id} user=$tenantUserName ts=$serviceStartTime " +
                "scan=${selectedMode.name} mode=${selectedUserMode.name} save=$saveData"
        )
    }

    private fun startSimulation() {
        if (isRunning) return
        if (tenantUserName.isBlank()) {
            tenantUserName = "common_${System.currentTimeMillis()}"
            updateStatus("Auth 없이 시뮬레이션 사용: userId=$tenantUserName")
        }
        ensureGenerators()
        val rfd = rfdGenerator!!
        val uvd = uvdGenerator!!

        val initStartTime = 1775451068715L
        serviceStartTime = initStartTime.toString()

        val selectedUserModeForMeta = getSelectedUserMode()
        SessionMetaStore.write(
            application,
            SessionMeta(
                userId = tenantUserName,
                serviceStartTime = serviceStartTime,
                sectorId = selectedSector?.id ?: -1,
                sectorDisplay = selectedSector?.display ?: "(simulation)",
                scanMode = "(simulation)",
                userMode = selectedUserModeForMeta.name,
                saveData = false,
                simulated = true
            )
        )

        rfd.generateSimulationRfdFromJson(
            tenantUserName,
            initStartTime.toString(),
            callback = object : RFDGenerator.RFDCallback {
                override fun onRfdResult(rfd: ReceivedForce) = updateRfdRows(rfd)
                override fun onRfdError(code: Int, msg: String) = updateStatus("Sim RFD error: $msg")
                override fun onRfdEmptyMillis(time: Long) = Unit
            }
        )

        val selectedUserMode = getSelectedUserMode()
        uvd.setUserMode(selectedUserMode)
        uvd.generateSimulationUvdFromJson(
            tenantUserName,
            initStartTime.toString(),
            callback = object : UVDGenerator.UVDCallback {
                override fun onUvdResult(mode: UserMode, uvd: UserVelocity) = updateUvdLatest(mode, uvd)
                override fun onPressureResult(hPa: Float) { pressure = hPa }
                override fun onVelocityResult(kmPh: Float) = Unit
                override fun onMagNormSmoothingVarResult(value: Float) = Unit
                override fun onUvdPauseMillis(time: Long) = Unit
                override fun onUvdError(error: String) = Unit
            }
        )
        applyRunningState(true)
        updateStatus("Simulation started (${selectedUserMode.name})")
    }

    private fun stopServices() {
        if (!isRunning) return
        rfdGenerator?.stopRfdGeneration()
        uvdGenerator?.stopUvdGeneration()
        if (tenantUserName.isNotBlank()) {
            JupiterDataManager.addEvent(
                application,
                tenantUserName,
                JupiterDataManager.JupiterEventCode.STOP_SERVICE
            )
        }
        // Drop SDK instances so the next Start creates fresh generators and UVD/RFD restart from 0.
        rfdGenerator = null
        uvdGenerator = null
        pressure = 0f
        serviceStartTime = ""
        runOnUiThread {
            tvUvdLatest.text = "No UVD data yet"
            rfdAdapter.submit(emptyList())
        }
        applyRunningState(false)
        updateStatus("Stopped")
    }

    private fun updateStatus(message: String) {
        runOnUiThread { tvStatus.text = "Status: $message" }
    }

    private fun updateUvdLatest(mode: UserMode, uvd: UserVelocity) {
        runOnUiThread {
            tvUvdLatest.text =
                "mode=${mode.name}\nindex=${uvd.index}\nlength=${uvd.length}\nheading=${uvd.heading}\nmobile_time=${uvd.mobile_time}"
        }
    }

    private fun updateRfdRows(rfd: ReceivedForce) {
        ScannedWardTracker.record(rfd)
        val rows = rfd.rfs.entries
            .sortedByDescending { it.value }
            .map { (name, rssi) ->
                RfdScanRow(name = name, rssi = rssi.toInt(), scannedAtMillis = rfd.mobile_time)
            }
        runOnUiThread { rfdAdapter.submit(rows) }
    }

    private fun getSelectedUserMode(): UserMode {
        return when (rgUserMode.checkedRadioButtonId) {
            R.id.rbUserModePdr -> UserMode.MODE_PEDESTRIAN
            R.id.rbUserModeAuto -> UserMode.MODE_AUTO
            else -> UserMode.MODE_VEHICLE
        }
    }

    private fun checkPermissions() {
        val rejected = ArrayList<String>()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                rejected.add(permission)
            }
        }
        if (rejected.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, rejected.toTypedArray(), multiplePermissionsCode)
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
