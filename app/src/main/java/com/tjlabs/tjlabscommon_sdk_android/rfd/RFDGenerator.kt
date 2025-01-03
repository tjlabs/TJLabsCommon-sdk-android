package com.tjlabs.tjlabscommon_sdk_android.rfd
import android.os.Handler
import android.os.Looper

class RFDGenerator() {
    interface RFDCallback {
        fun onRFDResult(rfdList: List<ReceivedForce>)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    val inputReceivedForce = mutableListOf<ReceivedForce>()
    var count = 0

    fun generateRFD(
        rfdLength : Int,
        intervalMillis: Long,
        id: String = "",
        getBleScanInfoSet: () -> MutableSet<BLEScanInfo>, // 최신 데이터 참조를 위한 람다
        pressure: Float = 0f,
        callback: RFDCallback
    ) {
        timerRunnable = object : Runnable {
            override fun run() {
                val currentBleScanInfoSet = getBleScanInfoSet()
                val averageBleMap = TJLabsBluetoothFunctions.averageBLEScanInfoSet(currentBleScanInfoSet)
                if (count < rfdLength) {
                    inputReceivedForce.add(
                    ReceivedForce(id, System.currentTimeMillis(), averageBleMap, pressure))
                    count++
                }
                if (count == rfdLength) {
                    callback.onRFDResult(inputReceivedForce) // 결과 리턴
                    count = 0
                    inputReceivedForce.clear()
                }
                handler.postDelayed(this, intervalMillis)
            }
        }
        handler.postDelayed(timerRunnable!!, intervalMillis)
    }


}