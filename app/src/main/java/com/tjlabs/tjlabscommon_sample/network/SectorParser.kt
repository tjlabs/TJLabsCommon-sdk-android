package com.tjlabs.tjlabscommon_sample.network

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class SectorOption(val id: Int, val name: String) {
    val display: String get() = "$id - $name"
}

object SectorParser {
    fun parse(root: JsonElement?): List<SectorOption> {
        if (root == null) return emptyList()
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> extractArray(root.asJsonObject)
            else -> JsonArray()
        }
        return array.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val obj = el.asJsonObject
            val id = readInt(obj, "id", "sector_id", "sector_code", "code") ?: return@mapNotNull null
            val name = readString(obj, "name", "sector_name", "label") ?: "sector-$id"
            SectorOption(id, name)
        }
    }

    private fun extractArray(obj: JsonObject): JsonArray {
        listOf("sectors", "data", "items", "result").forEach { key ->
            val value = obj.get(key)
            if (value != null && value.isJsonArray) return value.asJsonArray
        }
        return JsonArray()
    }

    private fun readInt(obj: JsonObject, vararg keys: String): Int? {
        for (key in keys) {
            val v = obj.get(key) ?: continue
            if (v.isJsonPrimitive) {
                val p = v.asJsonPrimitive
                if (p.isNumber) return p.asInt
                if (p.isString) return p.asString.toIntOrNull()
            }
        }
        return null
    }

    private fun readString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val v = obj.get(key) ?: continue
            if (v.isJsonPrimitive && v.asJsonPrimitive.isString) return v.asString
        }
        return null
    }
}
