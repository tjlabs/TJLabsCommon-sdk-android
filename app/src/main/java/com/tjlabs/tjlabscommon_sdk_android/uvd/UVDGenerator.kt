package com.tjlabs.tjlabscommon_sdk_android.uvd

import android.app.Application
import android.util.Log
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import com.tjlabs.tjlabscommon_sdk_android.rfd.ScanMode

class UVDGenerator(application: Application, val userId : String = "") {
    interface UVDCallback {
        fun onUvdResult(success : Boolean, msg : String, uvd: ReceivedForce)
    }

    private val tjLabsSensorManager : TJLabsSensorManager = TJLabsSensorManager(application)


    fun setUserMode(userMode: ScanMode) {

    }

    fun generateUvd(sensorFrequency : Int = 40) {
        val (isCheckSensorSuccess, msgCheckSensor) = tjLabsSensorManager.checkSensorAvailability()
        if (isCheckSensorSuccess) {
            tjLabsSensorManager.setSensorFrequency(sensorFrequency)
            tjLabsSensorManager.getSensorDataResultOrNull(object : TJLabsSensorManager.SensorResultListener{
                override fun onSensorChangedResult(sensorData: SensorData) {
                    Log.d("onSensorChanged", sensorData.toString())
                }
            })
        }
    }

    fun stopUvdGeneration() {
        tjLabsSensorManager.stopSensorChanged()
    }
}
