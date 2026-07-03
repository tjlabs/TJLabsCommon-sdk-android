package com.tjlabs.tjlabscommon_sample.wards

import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ScannedWardTracker {

    enum class Source { SCAN, BUNDLE_ONLY }

    data class WardEntry(
        val name: String,
        val maxRssi: Int?,
        val source: Source,
        val matchedLevel: String?,
    ) {
        val isScanned: Boolean get() = source == Source.SCAN
        val isMatched: Boolean get() = matchedLevel != null
    }

    private val maxRssiByName = linkedMapOf<String, Int>()
    private var bundleWards: BundleWardMap? = null

    private val _entries = MutableStateFlow<List<WardEntry>>(emptyList())
    val entries: StateFlow<List<WardEntry>> = _entries

    private val _bundleSectorId = MutableStateFlow<Int?>(null)
    val bundleSectorId: StateFlow<Int?> = _bundleSectorId

    @Synchronized
    fun reset() {
        maxRssiByName.clear()
        bundleWards = null
        _bundleSectorId.value = null
        _entries.value = emptyList()
    }

    @Synchronized
    fun clearBundle() {
        bundleWards = null
        _bundleSectorId.value = null
        publish()
    }

    @Synchronized
    fun record(rfd: ReceivedForce) {
        var changed = false
        for ((name, rssi) in rfd.rfs) {
            val rounded = rssi.toInt()
            val prev = maxRssiByName[name]
            if (prev == null || rounded > prev) {
                maxRssiByName[name] = rounded
                changed = true
            }
        }
        if (changed) publish()
    }

    @Synchronized
    fun applyBundle(bundle: BundleWardMap) {
        bundleWards = bundle
        _bundleSectorId.value = bundle.sectorId
        publish()
    }

    private fun publish() {
        val bundle = bundleWards
        val scanEntries = maxRssiByName.map { (name, rssi) ->
            WardEntry(
                name = name,
                maxRssi = rssi,
                source = Source.SCAN,
                matchedLevel = bundle?.nameToLevelLabel?.get(name),
            )
        }
        val bundleOnlyEntries = bundle?.let { b ->
            b.nameToLevelLabel.entries
                .filter { it.key !in maxRssiByName }
                .map { (name, level) ->
                    WardEntry(
                        name = name,
                        maxRssi = null,
                        source = Source.BUNDLE_ONLY,
                        matchedLevel = level,
                    )
                }
        }.orEmpty()

        _entries.value = (scanEntries + bundleOnlyEntries)
            .sortedWith(entryOrder)
    }

    private val entryOrder = Comparator<WardEntry> { a, b ->
        val groupA = groupRank(a)
        val groupB = groupRank(b)
        if (groupA != groupB) return@Comparator groupA - groupB
        val ka = hexKeyOf(a.name)
        val kb = hexKeyOf(b.name)
        when {
            ka != null && kb != null -> ka.compareTo(kb)
            ka != null -> -1
            kb != null -> 1
            else -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }

    private fun groupRank(e: WardEntry): Int = when {
        e.source == Source.SCAN && e.isMatched -> 0
        e.source == Source.SCAN -> 1
        else -> 2
    }

    private fun hexKeyOf(name: String): Long? {
        val parts = name.split('-')
        if (parts.size < 3) return null
        return parts[2].toLongOrNull(16)
    }
}
