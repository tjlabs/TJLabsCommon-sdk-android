package com.tjlabs.tjlabscommon_sdk_android.simulation

import android.app.Application
import android.content.Context.MODE_APPEND
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode
import com.tjlabs.tjlabscommon_sdk_android.uvd.UserVelocity
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale
import org.json.JSONObject


internal object JupiterDataFunctions {
    var simulationFlag = false
    var sensorMutableList = mutableListOf<String>()
    var sensorSimulationIndex = 0

    var bleMutableList = mutableListOf<String>()
    var bleSimulationIndex = 0

    var setSimulation = false
    private var baseFileName = ""

    var loadRfdData = false
    var loadUvdData = false
    var saveServiceStartTime = ""

    internal data class RfdJsonRecord(
        val mobileTime: Long,
        val pressureHpa: Float,
        val rfs: Map<String, Float>
    )

    internal data class UvdJsonRecord(
        val mobileTime: Long,
        val mode: String,
        val index: Int,
        val length: Float,
        val heading: Float
    )

    fun setServiceStartTime(time: Long) {
        saveServiceStartTime = time.toString()
    }

    fun getServiceStartTime(): String {
        return saveServiceStartTime
    }

    fun clearServiceStartTime() {
        saveServiceStartTime = ""
    }

    fun setBaseFileName(fileName: String) {
        baseFileName = fileName
    }
    fun loadBleData(app : Application, fileName : String) : Boolean{
        bleSimulationIndex = 0
        val bleFileContent = mutableListOf<String>()
        var bleSuccess = false

        try {
            val fileInputStream: FileInputStream = app.openFileInput(fileName + "_ble.csv")
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            var line: String? = bufferedReader.readLine()
            while (line != null) {
                bleFileContent.add(line)
                line = bufferedReader.readLine()
            }

            bufferedReader.close()
            inputStreamReader.close()
            fileInputStream.close()
            bleSuccess = true
            loadRfdData = true

        } catch (e: Exception) {
            e.printStackTrace()
        }
        bleMutableList = bleFileContent
        return bleSuccess

    }

    fun loadSensorData(app : Application, fileName : String) : Boolean{
        sensorSimulationIndex = 0

        val fileContent = mutableListOf<String>()

        var sensorSuccess = false
        try {
            val fileInputStream: FileInputStream = app.openFileInput(fileName + "_sensor.csv")
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            var line: String? = bufferedReader.readLine()
            while (line != null) {
                fileContent.add(line)
                line = bufferedReader.readLine()
            }

            bufferedReader.close()
            inputStreamReader.close()
            fileInputStream.close()
            sensorSuccess = true
            loadUvdData = true

        } catch (e: Exception) {
            e.printStackTrace()
        }
        sensorMutableList = fileContent
        return sensorSuccess
    }


    fun convertToSensorData(dataString: String): Pair<Long, SensorData> {
        val sensorData = SensorData()

        val parts = dataString.split(",")

        // Accelerometer
        sensorData.acc[0] = parts[1].toFloat()
        sensorData.acc[1] = parts[2].toFloat()
        sensorData.acc[2] = parts[3].toFloat()

        // Gyroscope
        sensorData.gyro[0] = parts[4].toFloat()
        sensorData.gyro[1] = parts[5].toFloat()
        sensorData.gyro[2] = parts[6].toFloat()

        // Magnetic Raw
        sensorData.magRaw[0] = parts[7].toFloat()
        sensorData.magRaw[1] = parts[8].toFloat()
        sensorData.magRaw[2] = parts[9].toFloat()
        sensorData.magRaw[3] = parts[10].toFloat()
        sensorData.magRaw[4] = parts[11].toFloat()
        sensorData.magRaw[5] = parts[12].toFloat()

        // Game Vector
        sensorData.gameVector[0] = parts[13].toFloat()
        sensorData.gameVector[1] = parts[14].toFloat()
        sensorData.gameVector[2] = parts[15].toFloat()
        sensorData.gameVector[3] = parts[16].toFloat()

        // Rotation Vector
        sensorData.rotVector[0] = parts[17].toFloat()
        sensorData.rotVector[1] = parts[18].toFloat()
        sensorData.rotVector[2] = parts[19].toFloat()
        sensorData.rotVector[3] = parts[20].toFloat()
        sensorData.rotVector[4] = parts[21].toFloat()

        // Pressure
        sensorData.pressure[0] = parts[22].toFloat()
        // Azimuth

        return Pair(parts[0].toLong(), sensorData)
    }


    fun parseStringToMap(input: String): Map<String, Float> {
        return parseMapString(input)
    }

    private fun parseMapString(mapString: String): Map<String, Float> {
        val parts = mapString.trim().split(',', limit = 2)
        val isHaveTimeFieldData = parts.size == 2 && parts[0].all { it.isDigit() }

        var entries: List<String> = listOf()

        // 시간 있으면 뒤쪽(part[1]), 없으면 전체(part[0] 또는 mapString)에서 {..}만 추출
        val mapSection = if (isHaveTimeFieldData) parts[1] else parts[0]
        val body = mapSection.trim().removePrefix("{").removeSuffix("}")

        entries = if (body.isBlank()) {
            emptyList()
        } else {
            // ", " 가 아닐 수도 있으니 "," 기준으로 안전하게 나눠서 trim
            body.split(',').map { it.trim() }
        }

        val map = mutableMapOf<String, Float>()

        if (body.isNotEmpty()) {
            for (entry in entries) {
                if (entry.contains('=')) {
                    // value 쪽에 '='이 더 있어도 안전하게 처리
                    val (key, valueStrRaw) = entry.split('=', limit = 2).map { it.trim() }.let {
                        it[0] to it.getOrNull(1).orEmpty()
                    }
                    val valueStr = valueStrRaw.removeSuffix("}")
                    val value = valueStr.toFloatOrNull() ?: continue
                    if (key.isNotEmpty()) {
                        map[key] = value
                    }
                }
            }
        }

        return map
    }


    fun saveDataFunction(app : Application, saveFlag : Boolean, fileName : String, data : String){
        if (saveFlag && fileName.isNotEmpty()) {
            app.openFileOutput("$fileName.csv", MODE_APPEND)
                .bufferedWriter().use { it.append(data)
                }
        }
    }

    fun saveRfdResultAsJson(
        app: Application,
        saveFlag: Boolean,
        isBackGround: Boolean,
        rfd: ReceivedForce
    ) {
        if (!saveFlag || isBackGround) return
        if (saveServiceStartTime.isBlank()) return

        val userId = rfd.tenant_user_name
        val rfdFileName = "${normalizeFileToken(userId, "unknown_user")}_${normalizeFileToken(saveServiceStartTime, "0")}_rfd.json"
        val rfsBody = rfd.rfs.entries.joinToString(",") { (key, value) ->
            "\"${escapeJson(key)}\":${formatNumber(value)}"
        }
        val jsonLine = "{\"tenant_user_name\":\"${escapeJson(rfd.tenant_user_name)}\"," +
            "\"mobile_time\":${rfd.mobile_time}," +
            "\"rfs\":{$rfsBody}," +
            "\"pressure\":${formatNumber(rfd.pressure)}}\n"

        appendJsonLine(app, rfdFileName, jsonLine)
    }

    fun saveUvdResultAsJson(
        app: Application,
        saveFlag: Boolean,
        isBackGround: Boolean,
        userMode: UserMode,
        userVelocity: UserVelocity
    ) {
        if (!saveFlag || isBackGround) return
        if (saveServiceStartTime.isBlank()) return

        val userId = userVelocity.tenant_user_name
        val uvdFileName = "${normalizeFileToken(userId, "unknown_user")}_${normalizeFileToken(saveServiceStartTime, "0")}_uvd.json"
        val jsonLine = "{\"tenant_user_name\":\"${escapeJson(userVelocity.tenant_user_name)}\"," +
            "\"mobile_time\":${userVelocity.mobile_time}," +
            "\"mode\":\"${escapeJson(userMode.value)}\"," +
            "\"index\":${userVelocity.index}," +
            "\"length\":${formatNumber(userVelocity.length)}," +
            "\"heading\":${formatNumber(userVelocity.heading)}," +
            "\"looking\":${userVelocity.looking}}\n"

        appendJsonLine(app, uvdFileName, jsonLine)
    }

    fun saveEventResultAsJson(
        app: Application,
        saveFlag: Boolean,
        userId: String,
        mobileTime: Long,
        eventCode: Int,
        eventInfo : String = ""
    ) {
        if (!saveFlag) return
        if (saveServiceStartTime.isBlank()) return

        val eventFileName = "${normalizeFileToken(userId, "unknown_user")}_${normalizeFileToken(saveServiceStartTime, "0")}_event.json"
        val jsonLine = "{\"mobile_time\":$mobileTime," +
                "\"event_code\":$eventCode," +
                "\"event_info\":\"${escapeJson(eventInfo)}\"}\n"

        appendJsonLine(app, eventFileName, jsonLine)

    }

    private fun appendJsonLine(app: Application, fileName: String, data: String) {
        app.openFileOutput(fileName, MODE_APPEND)
            .bufferedWriter()
            .use { it.append(data) }
    }

    private fun normalizeFileToken(value: String, fallback: String): String {
        val token = value.trim()
        return if (token.isEmpty()) fallback else token.replace("\\s+".toRegex(), "_")
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun formatNumber(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        if (value % 1f == 0f) return value.toInt().toString()
        val formatted = String.format(Locale.US, "%.6f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }

    fun loadRfdJsonData(app: Application, userId: String, serviceStartTime: String): List<RfdJsonRecord> {
        val fileName = "${normalizeFileToken(userId, "unknown_user")}_${normalizeFileToken(serviceStartTime, "0")}_rfd.json"
        val records = mutableListOf<RfdJsonRecord>()
        try {
            val fileInputStream: FileInputStream = app.openFileInput(fileName)
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val obj = JSONObject(trimmed)
                    val mobileTime = obj.getLong("mobile_time")
                    val pressureHpa = if (obj.has("pressure")) {
                        obj.optDouble("pressure", 0.0).toFloat()
                    } else {
                        obj.optDouble("pressure_hpa", 0.0).toFloat()
                    }
                    val rfsObj = obj.optJSONObject("rfs") ?: JSONObject()
                    val rfsMap = mutableMapOf<String, Float>()
                    val keys = rfsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        rfsMap[key] = rfsObj.optDouble(key, -100.0).toFloat()
                    }
                    records.add(RfdJsonRecord(mobileTime, pressureHpa, rfsMap))
                }
                line = bufferedReader.readLine()
            }
            bufferedReader.close()
            inputStreamReader.close()
            fileInputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return records.sortedBy { it.mobileTime }
    }

    fun loadUvdJsonData(app: Application, userId: String, serviceStartTime: String): List<UvdJsonRecord> {
        val fileName = "${normalizeFileToken(userId, "unknown_user")}_${normalizeFileToken(serviceStartTime, "0")}_uvd.json"
        val records = mutableListOf<UvdJsonRecord>()
        try {
            val fileInputStream: FileInputStream = app.openFileInput(fileName)
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val obj = JSONObject(trimmed)
                    records.add(
                        UvdJsonRecord(
                            mobileTime = obj.getLong("mobile_time"),
                            mode = obj.optString("mode", "DR"),
                            index = obj.optInt("index", 0),
                            length = obj.optDouble("length", 0.0).toFloat(),
                            heading = obj.optDouble("heading", 0.0).toFloat()
                        )
                    )
                }
                line = bufferedReader.readLine()
            }
            bufferedReader.close()
            inputStreamReader.close()
            fileInputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return records.sortedBy { it.mobileTime }
    }
}
