package org.krripe.shadowkits.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Paths

object BlockKitsConfig {

    enum class ClickAction { PREVIEW, CLAIM, NONE }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val rootDir      = Paths.get("config", "shadowkits").toFile()
    private val blocksFolder = File(rootDir, "blocks_configs")

    data class ButtonConfig(
        val slot: Int,
        val material: String,
        val name: String,
        val customModelData: Int? = null
    )

    data class BlockPreviewConfig(
        val title: String = "&6Block Kit Preview: %kit%",
        val size: Int = 54,

        val fillersEnabled: Boolean = true,
        val fillItem: String = "minecraft:light_gray_stained_glass_pane",
        val fillerName: String = " ",
        val fillerCustomModelData: Int? = null,
        val fillerSlots: List<Int> = listOf(
            0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53
        ),

        val previewSlots: List<Int> = listOf(
            10,11,12,13,14,15,16,19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,37,38,39,40,41,42,43
        ),

        val closeButton: ButtonConfig = ButtonConfig(49, "minecraft:barrier", "&cClose"),

        val leftClickAction: String? = "claim",
        val rightClickAction: String? = "preview",
        val allowClaim: Boolean? = true
    )

    private val DEFAULT = BlockPreviewConfig()

    fun load() {
        rootDir.mkdirs()
        blocksFolder.mkdirs()
    }

    fun getFor(kitName: String): BlockPreviewConfig {
        val perKit = File(blocksFolder, "block_${kitName.lowercase()}.json")
        return if (perKit.exists()) load(perKit, DEFAULT) else DEFAULT
    }

    fun ensureKitTemplate(kitName: String) {
        val f = File(blocksFolder, "block_${kitName.lowercase()}.json")
        if (!f.exists()) saveTemplate(f)
    }

    fun saveFor(kitName: String, cfg: BlockPreviewConfig) {
        write(File(blocksFolder, "block_${kitName.lowercase()}.json"), cfg)
    }

    private inline fun <reified T> load(file: File, fallback: T): T =
        try { gson.fromJson(file.readText(), object : TypeToken<T>() {}.type) ?: fallback }
        catch (_: Exception) { fallback }

    private fun saveTemplate(file: File) {
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(DEFAULT))
    }

    private fun write(file: File, cfg: BlockPreviewConfig) {
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(cfg))
    }

    fun actionForClick(cfg: BlockPreviewConfig, isRightClick: Boolean): ClickAction {
        val allowClaim = cfg.allowClaim != false
        val raw = (if (isRightClick) cfg.rightClickAction else cfg.leftClickAction)
            ?.lowercase() ?: ""

        return when (raw) {
            "preview" -> ClickAction.PREVIEW
            "claim"   -> if (allowClaim) ClickAction.CLAIM else ClickAction.NONE
            "none"    -> ClickAction.NONE
            else -> {
                if (isRightClick) ClickAction.PREVIEW
                else if (allowClaim) ClickAction.CLAIM else ClickAction.NONE
            }
        }
    }
}
