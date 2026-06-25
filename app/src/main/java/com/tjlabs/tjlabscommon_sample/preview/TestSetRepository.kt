package com.tjlabs.tjlabscommon_sample.preview

import android.app.Application
import android.util.Log
import java.io.File

object TestSetRepository {
    private const val TAG = "TestSetRepository"
    private val NAME_REGEX = Regex("^(.+)_(\\d+)_(rfd|uvd|event)\\.json$")
    private val META_REGEX = Regex("^(.+)_(\\d+)_meta\\.json$")

    fun list(application: Application): List<TestSet> {
        val dir: File = application.filesDir
        val files = dir.listFiles() ?: return emptyList()
        val grouped = mutableMapOf<Pair<String, String>, MutableList<TestSetFile>>()
        val metaFiles = mutableMapOf<Pair<String, String>, File>()

        for (f in files) {
            if (!f.isFile) continue
            val dataMatch = NAME_REGEX.matchEntire(f.name)
            if (dataMatch != null) {
                val userId = dataMatch.groupValues[1]
                val ts = dataMatch.groupValues[2]
                val type = dataMatch.groupValues[3]
                grouped.getOrPut(userId to ts) { mutableListOf() }.add(
                    TestSetFile(type = type, fileName = f.name, sizeBytes = f.length())
                )
                continue
            }
            val metaMatch = META_REGEX.matchEntire(f.name)
            if (metaMatch != null) {
                val userId = metaMatch.groupValues[1]
                val ts = metaMatch.groupValues[2]
                metaFiles[userId to ts] = f
            }
        }

        // Clean orphan meta files (no data files left) so upload-deleted sessions don't leak.
        for ((key, file) in metaFiles) {
            if (!grouped.containsKey(key)) {
                if (file.delete()) Log.d(TAG, "cleaned orphan meta: ${file.name}")
            }
        }

        return grouped.map { (key, items) ->
            val meta = SessionMetaStore.read(application, key.first, key.second)
            TestSet(
                userId = key.first,
                serviceStartTime = key.second,
                files = items,
                meta = meta
            )
        }.sortedByDescending { it.timestampMillis() }
    }

    fun delete(application: Application, set: TestSet): Int {
        var deleted = 0
        for (f in set.files) {
            val file = File(application.filesDir, f.fileName)
            if (file.exists()) {
                if (file.delete()) deleted++ else Log.w(TAG, "delete failed: ${f.fileName}")
            }
        }
        SessionMetaStore.delete(application, set.userId, set.serviceStartTime)
        return deleted
    }
}
