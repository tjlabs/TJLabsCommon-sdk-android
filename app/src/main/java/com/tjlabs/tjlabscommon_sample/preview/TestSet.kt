package com.tjlabs.tjlabscommon_sample.preview

data class TestSetFile(
    val type: String,
    val fileName: String,
    val sizeBytes: Long
)

data class TestSet(
    val userId: String,
    val serviceStartTime: String,
    val files: List<TestSetFile>,
    val meta: SessionMeta? = null
) {
    fun key(): String = "${userId}_${serviceStartTime}"

    fun timestampMillis(): Long = serviceStartTime.toLongOrNull() ?: 0L

    fun totalBytes(): Long = files.sumOf { it.sizeBytes }

    fun hasType(type: String): Boolean = files.any { it.type == type }
}
