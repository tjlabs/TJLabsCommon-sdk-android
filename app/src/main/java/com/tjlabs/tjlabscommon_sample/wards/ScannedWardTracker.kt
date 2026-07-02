package com.tjlabs.tjlabscommon_sample.wards

import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ScannedWardTracker {

    data class WardEntry(val name: String, val maxRssi: Int)

    private val maxRssiByName = linkedMapOf<String, Int>()

    private val _entries = MutableStateFlow<List<WardEntry>>(emptyList())
    val entries: StateFlow<List<WardEntry>> = _entries

    private val _checkedNames = MutableStateFlow<Set<String>>(emptySet())
    val checkedNames: StateFlow<Set<String>> = _checkedNames

    @Synchronized
    fun reset() {
        maxRssiByName.clear()
        _entries.value = emptyList()
        _checkedNames.value = emptySet()
    }

    @Synchronized
    fun toggleChecked(name: String) {
        val current = _checkedNames.value
        _checkedNames.value = if (name in current) current - name else current + name
    }

    @Synchronized
    fun resetChecks() {
        _checkedNames.value = emptySet()
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

    private fun publish() {
        _entries.value = maxRssiByName.entries
            .map { WardEntry(it.key, it.value) }
            .sortedWith(compareBy(wardNameComparator) { it.name })
    }

    // Ward name format: TJ-00CB-0001012C-0000 → sort by the 3rd segment (index 2) as hex.
    // Names that don't match fall back to whole-string compare and sink to the end.
    private val wardNameComparator = Comparator<String> { a, b ->
        val ka = hexKeyOf(a)
        val kb = hexKeyOf(b)
        when {
            ka != null && kb != null -> ka.compareTo(kb)
            ka != null -> -1
            kb != null -> 1
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    private fun hexKeyOf(name: String): Long? {
        val parts = name.split('-')
        if (parts.size < 3) return null
        return parts[2].toLongOrNull(16)
    }
}
