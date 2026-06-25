package com.tjlabs.tjlabscommon_sample.preview

import android.app.Application
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class UvdSample(
    val mobileTime: Long,
    val index: Int,
    val length: Float,
    val heading: Float,
    val mode: String
)

data class RfdSample(
    val mobileTime: Long,
    val rfs: Map<String, Float>
)

object TelemetryFileReader {
    private const val TAG = "TelemetryFileReader"

    fun readUvd(application: Application, userId: String, serviceStartTime: String): List<UvdSample> {
        val fileName = "${userId}_${serviceStartTime}_uvd.json"
        return readLines(application, fileName).mapNotNull { line ->
            runCatching {
                val obj = JSONObject(line)
                UvdSample(
                    mobileTime = obj.optLong("mobile_time"),
                    index = obj.optInt("index"),
                    length = obj.optDouble("length", 0.0).toFloat(),
                    heading = obj.optDouble("heading", 0.0).toFloat(),
                    mode = obj.optString("mode", "")
                )
            }.getOrNull()
        }.sortedBy { it.mobileTime }
    }

    fun readRfd(application: Application, userId: String, serviceStartTime: String): List<RfdSample> {
        val fileName = "${userId}_${serviceStartTime}_rfd.json"
        return readLines(application, fileName).mapNotNull { line ->
            runCatching {
                val obj = JSONObject(line)
                val rfsObj = obj.optJSONObject("rfs") ?: JSONObject()
                val map = mutableMapOf<String, Float>()
                val keys = rfsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = rfsObj.optDouble(key, -100.0).toFloat()
                }
                RfdSample(
                    mobileTime = obj.optLong("mobile_time"),
                    rfs = map
                )
            }.getOrNull()
        }.sortedBy { it.mobileTime }
    }

    private fun readLines(application: Application, fileName: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val fis = application.openFileInput(fileName)
            val br = BufferedReader(InputStreamReader(fis))
            var line = br.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) out.add(trimmed)
                line = br.readLine()
            }
            br.close()
        } catch (e: Exception) {
            Log.d(TAG, "readLines($fileName) -> ${e.message}")
        }
        return out
    }
}
