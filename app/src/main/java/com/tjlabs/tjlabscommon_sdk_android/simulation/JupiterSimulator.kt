package com.tjlabs.tjlabscommon_sdk_android.simulation

import android.app.Application
import android.content.Context.MODE_APPEND
import com.tjlabs.tjlabscommon_sdk_android.uvd.SensorData
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader


internal object JupiterSimulator {
    var simulationFlag = false
    var sensorMutableList = mutableListOf<String>()
    var sensorSimulationIndex = 0

    var bleMutableList = mutableListOf<String>()
    var bleSimulationIndex = 0

    var setSimulation = false
    private var baseFileName = ""

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

        return Pair(0, sensorData)
    }


    fun parseStringToMap(input: String): Map<String, Float> {
        return parseMapString(input)
    }

    private fun parseMapString(mapString: String): Map<String, Float> {
        val entries = mapString.trim('{', '}').split(", ").map { it.trim() }
        val map = mutableMapOf<String, Float>()

        if (mapString != "{}") {
            for (entry in entries) {
                if (entry.contains('=')){
                    var (key, value) = entry.split('=').map { it.trim() }
                    if (value.contains("}")){
                        value = value.split("}")[0]
                    }
                    map[key] = value.toFloat()
                }
            }
        }

        return map
    }

    private fun saveDataFunction(app : Application, saveFlag : Boolean, fileName : String, data : String){
        if (saveFlag && fileName.isNotEmpty()) {
            app.openFileOutput("$fileName.csv", MODE_APPEND)
                .bufferedWriter().use { it.append(data)
                }
        }
    }
}