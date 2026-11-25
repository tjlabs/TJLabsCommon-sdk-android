package com.tjlabs.tjlabscommon_sdk_android.simulation

import android.app.Application
import android.content.Context.MODE_APPEND
import android.os.Build
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import com.tjlabs.tjlabscommon_sdk_android.uvd.sensorFrequency
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsCommonLog
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.Locale

/**
 * UVD 를 만드는 데 쓴 센서값(40 Hz 틱마다 하나)을 `{userId}_{serviceStartTime}_sensor.jsonl` 로 남긴다.
 * uvd · rfd json 과 같이 **파일 저장 설정(isSaveData)이 켜졌을 때만** 쓴다 — 배포 기본값에서는 만들지 않는다.
 *
 * - 첫 줄 header (`type=header`, `sample_hz`, `fields` …), 이후 한 줄에 한 틱 (`type=sample`).
 * - `mobile_time` 은 같은 틱의 UVD 계산 시각과 같은 값이라 uvd.json 과 시각으로 맞출 수 있다.
 * - 40 Hz 라 줄마다 파일을 열고 닫지 않고, 세션 동안 열어 두고 [FLUSH_EVERY] 줄마다 flush 한다.
 */
internal class SensorLogWriter private constructor(
    private val writer: BufferedWriter,
    val fileName: String,
) {
    private var lines = 0

    @Synchronized
    fun append(mobileTime: Long, s: SensorData) {
        val line = StringBuilder(256)
            .append("{\"type\":\"sample\",\"mobile_time\":").append(mobileTime)
            .append(",\"acc\":").arr(s.acc, 5)
            .append(",\"gyro\":").arr(s.gyro, 5)
            .append(",\"mag_raw\":").arr(s.magRaw, 3)
            .append(",\"game_vector\":").arr(s.gameVector, 6)
            .append(",\"rot_vector\":").arr(s.rotVector, 6)
            .append(",\"pressure\":").append(num(s.pressure[0], 3))
            .append("}\n")
        runCatching {
            writer.append(line)
            if (++lines % FLUSH_EVERY == 0) writer.flush()
        }.onFailure { TJLabsCommonLog.e(TAG, "append failed: $fileName", it) }
    }

    @Synchronized
    fun close() {
        runCatching { writer.flush(); writer.close() }
            .onFailure { TJLabsCommonLog.e(TAG, "close failed: $fileName", it) }
    }

    companion object {
        private const val TAG = "SensorLogWriter"
        /** 1초(40줄)마다 flush */
        private const val FLUSH_EVERY = sensorFrequency

        /** serviceStartTime 이 비어 있으면(uvd · rfd 도 저장 안 하는 상태) null */
        fun open(app: Application, userId: String, serviceStartTime: String): SensorLogWriter? {
            if (serviceStartTime.isBlank()) return null
            val fileName = "${token(userId, "unknown_user")}_${token(serviceStartTime, "0")}_sensor.jsonl"
            return runCatching {
                val isNew = !app.getFileStreamPath(fileName).exists()
                val w = BufferedWriter(OutputStreamWriter(app.openFileOutput(fileName, MODE_APPEND), Charsets.UTF_8))
                if (isNew) w.append(header(app, userId, serviceStartTime))
                SensorLogWriter(w, fileName)
            }.onFailure { TJLabsCommonLog.e(TAG, "open failed: $fileName", it) }.getOrNull()
        }

        private fun header(app: Application, userId: String, serviceStartTime: String): String {
            val appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
            return "{\"type\":\"header\",\"source\":\"common_uvd\"" +
                ",\"tenant_user_name\":\"${esc(userId)}\"" +
                ",\"service_start_time\":${serviceStartTime.toLongOrNull() ?: 0}" +
                ",\"sample_hz\":$sensorFrequency" +
                ",\"device_manufacturer\":\"${esc(Build.MANUFACTURER)}\"" +
                ",\"device_model\":\"${esc(Build.MODEL)}\"" +
                ",\"os_sdk_int\":${Build.VERSION.SDK_INT}" +
                ",\"app_id\":\"${esc(app.packageName)}\",\"app_version\":\"${esc(appVersion)}\"" +
                ",\"fields\":[\"mobile_time\",\"acc\",\"gyro\",\"mag_raw\",\"game_vector\",\"rot_vector\",\"pressure\"]" +
                ",\"units\":{\"acc\":\"m/s^2\",\"gyro\":\"rad/s\",\"mag_raw\":\"uT (x,y,z,bias_x,bias_y,bias_z)\",\"pressure\":\"hPa\"}}\n"
        }

        private fun token(v: String, fallback: String) = v.trim().ifEmpty { fallback }.replace("\\s+".toRegex(), "_")
        private fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

        private fun num(v: Float, decimals: Int): String {
            if (v.isNaN() || v.isInfinite()) return "0"
            if (v % 1f == 0f) return v.toInt().toString()
            return String.format(Locale.US, "%.${decimals}f", v).trimEnd('0').trimEnd('.')
        }

        private fun StringBuilder.arr(values: FloatArray, decimals: Int): StringBuilder {
            append('[')
            values.forEachIndexed { i, v -> if (i > 0) append(','); append(num(v, decimals)) }
            return append(']')
        }
    }
}
