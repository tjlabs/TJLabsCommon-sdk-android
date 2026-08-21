package com.tjlabs.tjlabscommon_sdk_android.rfd

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsCommonLog

/**
 * BLE 스캔에 필요한 조건 (Manifest flag · 런타임 권한) 사전 진단 유틸리티.
 *
 * ── 배경 ──
 * 호스트 앱이 BLUETOOTH_SCAN 권한에 `neverForLocation` flag 를 붙이면 OS 는 Location 권한 없이도
 * BLE 스캔을 허용한다. 그러나 이 SDK 내부의 [TJLabsBluetoothManager.checkPermissions] 는
 * 여전히 Location 권한(FINE 또는 COARSE) 하나를 요구하므로, 앱이 `neverForLocation` 만 붙이고
 * Location 런타임 권한을 요청하지 않으면 OS 는 통과했는데 SDK 내부 체크에서 걸려 스캔이 시작되지 않는
 * mismatch 가 발생한다. 이 경우 startService 는 실패하지만 원인 특정이 어려워 필드에서 재현 시 시간이
 * 많이 걸린다.
 *
 * 이 클래스는 앱이 startService 호출 전에 상태를 사전 점검하거나, 실패 시 사람이 이해할 수 있는
 * 해결책 문구를 얻는 데 사용된다.
 */
object PermissionDiagnostic {
    private const val TAG = "PermissionDiagnostic"

    /**
     * 병합된 Manifest 에서 BLUETOOTH_SCAN 권한 선언에 `neverForLocation` flag 가 설정되었는지 확인.
     * API 31 미만은 flag 자체가 존재하지 않으므로 항상 false 반환.
     */
    fun hasBluetoothScanNeverForLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            val permissions = pkgInfo.requestedPermissions ?: return false
            val flags = pkgInfo.requestedPermissionsFlags ?: return false
            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.BLUETOOTH_SCAN) {
                    return (flags[i] and PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0
                }
            }
            false
        } catch (e: PackageManager.NameNotFoundException) {
            TJLabsCommonLog.w(TAG, "hasBluetoothScanNeverForLocation: package not found - ${e.message}")
            false
        }
    }

    /**
     * 런타임에 BLUETOOTH_SCAN 권한이 승인되었는지. API 31 미만은 install-time 권한이므로 항상 true.
     */
    fun hasBluetoothScanRuntimePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 런타임에 Location 권한 (ACCESS_FINE_LOCATION 또는 ACCESS_COARSE_LOCATION) 이 하나라도 승인되었는지.
     */
    fun hasLocationRuntimePermission(context: Context): Boolean {
        val fine = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * BLE 스캔 준비 상태 종합 진단. 앱이 startService 호출 전 pre-check 용도로 사용 가능.
     * [BleScanReadinessReport.isReady] true 면 스캔 시작 가능. false 면
     * [BleScanReadinessReport.problems] 와 [BleScanReadinessReport.remediation] 으로 원인·해결책 확인.
     */
    fun diagnoseBleScanReadiness(context: Context): BleScanReadinessReport {
        val hasScanPerm = hasBluetoothScanRuntimePermission(context)
        val hasLocPerm = hasLocationRuntimePermission(context)
        val hasNeverForLocationFlag = hasBluetoothScanNeverForLocation(context)

        val problems = mutableListOf<String>()
        var remediation: String? = null

        if (!hasScanPerm) {
            problems += "BLUETOOTH_SCAN runtime permission is not granted (API 31+)."
        }
        // neverForLocation flag 감지 시 무조건 problem 승격.
        // OS 는 스캔을 허용하지만 iBeacon/Eddystone 광고를 필터링해 silent 실패를 유발한다. 정상 상태가
        // 아니면 무조건 fail 시켜 개발자가 즉시 인지하도록 하는 정책 (TJLabsBluetoothManager.checkPermissions 와 정합).
        if (hasNeverForLocationFlag) {
            problems += "BLUETOOTH_SCAN is declared with 'neverForLocation' flag. OS may filter " +
                "iBeacon/Eddystone advertisements from scan results, causing zero BLE data even though " +
                "the scan starts successfully. Remove the flag from your app manifest."
            remediation = "Remove 'android:usesPermissionFlags=\"neverForLocation\"' from the " +
                "BLUETOOTH_SCAN declaration in your app's AndroidManifest.xml and request " +
                "ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION at runtime instead."
        }
        if (!hasLocPerm) {
            problems += "Location runtime permission (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION) is not granted."
        }

        val report = BleScanReadinessReport(
            isReady = problems.isEmpty(),
            hasBluetoothScanPermission = hasScanPerm,
            hasLocationPermission = hasLocPerm,
            manifestHasNeverForLocationFlag = hasNeverForLocationFlag,
            problems = problems,
            remediation = remediation,
        )
        val level = if (report.isReady) "ok" else "problems"
        TJLabsCommonLog.i(TAG, "diagnoseBleScanReadiness ($level): $report")
        return report
    }
}

/**
 * BLE 스캔 준비 상태 진단 결과.
 *
 * @property isReady 스캔 시작이 가능한 상태 여부 ([problems] 가 비어있으면 true).
 * @property hasBluetoothScanPermission BLUETOOTH_SCAN 런타임 승인 여부 (API 31+ 만 유의미)
 * @property hasLocationPermission FINE 또는 COARSE 중 하나라도 런타임 승인 여부
 * @property manifestHasNeverForLocationFlag 병합 Manifest 의 BLUETOOTH_SCAN 에 neverForLocation flag 유무.
 *   true 면 problem 으로 승격되어 [isReady] 는 false — OS 는 스캔을 허용하나 iBeacon/Eddystone 광고 필터링으로
 *   RFD 가 생성되지 않는 silent 실패를 유발하기 때문.
 * @property problems 스캔 시작을 막는 미충족 조건 리스트 (SDK 내부 precheck 실패 유발)
 * @property remediation problem 이 있을 때 사람 읽기 가능한 해결책. 없으면 null.
 */
data class BleScanReadinessReport(
    val isReady: Boolean,
    val hasBluetoothScanPermission: Boolean,
    val hasLocationPermission: Boolean,
    val manifestHasNeverForLocationFlag: Boolean,
    val problems: List<String>,
    val remediation: String?,
)
