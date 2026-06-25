package com.tjlabs.tjlabscommon_sample.preview

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

data class SessionMeta(
    val userId: String = "",
    val serviceStartTime: String = "",
    val sectorId: Int = -1,
    val sectorDisplay: String = "",
    val scanMode: String = "",
    val userMode: String = "",
    val saveData: Boolean = false,
    val simulated: Boolean = false
) {
    fun infoLine(): String {
        val parts = mutableListOf<String>()
        when {
            sectorDisplay.isNotBlank() -> parts += "sector ${sectorDisplay}"
            sectorId >= 0 -> parts += "sector $sectorId"
        }
        if (scanMode.isNotBlank()) parts += "scan $scanMode"
        if (userMode.isNotBlank()) parts += "mode $userMode"
        if (simulated) parts += "simulation"
        return parts.joinToString("  ·  ")
    }
}

object SessionMetaStore {
    private const val TAG = "SessionMetaStore"
    private val gson = Gson()

    fun fileName(userId: String, serviceStartTime: String): String =
        "${userId}_${serviceStartTime}_meta.json"

    fun write(application: Application, meta: SessionMeta) {
        val name = fileName(meta.userId, meta.serviceStartTime)
        try {
            application.openFileOutput(name, Context.MODE_PRIVATE).use {
                it.write(gson.toJson(meta).toByteArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "write meta failed for $name: ${e.message}")
        }
    }

    fun read(application: Application, userId: String, serviceStartTime: String): SessionMeta? {
        val file = File(application.filesDir, fileName(userId, serviceStartTime))
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), SessionMeta::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "read meta failed: ${e.message}")
            null
        }
    }

    fun delete(application: Application, userId: String, serviceStartTime: String): Boolean {
        val file = File(application.filesDir, fileName(userId, serviceStartTime))
        return if (file.exists()) file.delete() else false
    }
}
