package org.krripe.shadowkits.gui

import com.google.gson.JsonObject
import org.krripe.shadowkits.config.KitsConfig
import java.io.File

object GuiConfigLoader {

    inline fun <reified T : Any> loadOrCreate(file: File, defaults: T): T {
        val gson = KitsConfig.gson
        val defaultsJson = gson.toJsonTree(defaults).asJsonObject

        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(defaultsJson))
            return defaults
        }

        val existing = runCatching {
            gson.fromJson(file.readText(), JsonObject::class.java)
        }.getOrNull() ?: JsonObject()

        val merged = deepMerge(defaultsJson, existing)

        file.parentFile.mkdirs()
        file.writeText(gson.toJson(merged))
        return gson.fromJson(merged, T::class.java)
    }

    fun deepMerge(defaults: JsonObject, overrides: JsonObject): JsonObject {
        val out = JsonObject()

        defaults.entrySet().forEach { (key, value) -> out.add(key, value.deepCopy()) }

        overrides.entrySet().forEach { (key, overrideValue) ->
            val current = out.get(key)
            if (current is JsonObject && overrideValue is JsonObject) out.add(key, deepMerge(current, overrideValue))
            else out.add(key, overrideValue)
        }

        return out
    }
}