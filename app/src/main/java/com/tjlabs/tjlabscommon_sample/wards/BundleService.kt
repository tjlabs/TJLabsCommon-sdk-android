package com.tjlabs.tjlabscommon_sample.wards

import android.app.Application
import android.util.Log
import com.tjlabs.tjlabsresource_sdk_android.ResourceRegion
import com.tjlabs.tjlabsresource_sdk_android.ServerProvider
import com.tjlabs.tjlabsresource_sdk_android.TJLabsResourceManager

data class BundleWardMap(
    val sectorId: Int,
    val nameToLevelLabel: Map<String, String>,
) {
    val allWardNames: Set<String> get() = nameToLevelLabel.keys
    val levelCount: Int get() = nameToLevelLabel.values.toSet().size
}

object BundleService {

    private const val TAG = "BundleService"

    fun load(
        application: Application,
        sectorId: Int,
        provider: String = ServerProvider.GCP.value,
        region: String = ResourceRegion.KOREA.value,
        onDone: (Result<BundleWardMap>) -> Unit,
    ) {
        val manager = TJLabsResourceManager()
        Log.d(TAG, "loadResource sector=$sectorId provider=$provider region=$region")
        manager.loadResource(application, provider, region, sectorId) { success ->
            if (!success) {
                onDone(Result.failure(IllegalStateException("loadResource failed")))
                return@loadResource
            }
            val raw = manager.getLevelWardsData()
            val map = LinkedHashMap<String, String>()
            for ((key, wardNames) in raw) {
                val label = levelLabelFromKey(key, sectorId)
                for (name in wardNames) {
                    map.putIfAbsent(name, label)
                }
            }
            Log.d(TAG, "loadResource done levels=${raw.size} wards=${map.size}")
            onDone(Result.success(BundleWardMap(sectorId, map)))
        }
    }

    // Key format from Resource SDK: "{sectorId}_{buildingName}_{levelName}" → return only level.
    private fun levelLabelFromKey(key: String, sectorId: Int): String {
        val prefix = "${sectorId}_"
        val trimmed = if (key.startsWith(prefix)) key.substring(prefix.length) else key
        val cut = trimmed.indexOf('_')
        if (cut <= 0 || cut == trimmed.length - 1) return trimmed
        return trimmed.substring(cut + 1)
    }
}
