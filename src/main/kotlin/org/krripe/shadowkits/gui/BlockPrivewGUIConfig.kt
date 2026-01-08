package org.krripe.shadowkits.gui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.krripe.shadowkits.config.KitsConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object BlockPrivewGUIConfig {

    enum class ClickAction { PREVIEW, CLAIM, NONE }

    data class PerKitBlockPreviewConfig(
        val title: String = "&6Block Kit Preview: %kit%",
        val size: Int = 54,
        val fillersEnabled: Boolean = true,
        val fillers: MutableList<Filler> = mutableListOf(
            Filler(
                name = " ",
                icon = "minecraft:black_stained_glass_pane",
                slots = listOf("0","1","2","3","4","5","6","7","8","9","17","18","26","27","35","36","44","45","46","47","48","50","51","52","53")
            )
        ),
        val closeButton: Button? = Button(slot = 49, icon = "minecraft:barrier", name = "&cClose"),
        val contentSlots: List<String> = listOf("10..16","19..25","28..34","37..43"),
        val leftClickAction: String? = "claim",
        val rightClickAction: String? = "preview",
        val allowClaim: Boolean? = true
    )

    private val rootDir      = File("config/shadowkits")
    private val guiDir       = File(rootDir, "gui")
    private val newDir       = File(guiDir, "block_previews")
    private val backupsDir   = File(rootDir, "backups")
    private val oldDirPerKit = File(rootDir, "block_previews")
    private val oldDirLegacy = File(rootDir, "blocks_configs")

    private data class OldButtonConfig(val slot: Int, val material: String, val name: String, val customModelData: Int? = null)
    private data class OldBlockPreviewConfig(
        val title: String = "&6Block Kit Preview: %kit%",
        val size: Int = 54,
        val fillersEnabled: Boolean = true,
        val fillItem: String = "minecraft:black_stained_glass_pane",
        val fillerName: String = " ",
        val fillerCustomModelData: Int? = null,
        val fillerSlots: List<Int> = listOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53),
        val previewSlots: List<Int> = listOf(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43),
        val closeButton: OldButtonConfig = OldButtonConfig(49, "minecraft:barrier", "&cClose"),
        val leftClickAction: String? = "claim",
        val rightClickAction: String? = "preview",
        val allowClaim: Boolean? = true
    )

    fun reload() {
        newDir.mkdirs(); backupsDir.mkdirs()
    }

    fun ensureKitTemplate(kitName: String) {
        newDir.mkdirs()
        val f = File(newDir, "${kitName.lowercase()}_block_preview.json")
        if (!f.exists()) {
            f.parentFile.mkdirs()
            f.writeText(KitsConfig.gson.toJson(PerKitBlockPreviewConfig()))
        }
    }

    fun getFor(kitName: String): PerKitBlockPreviewConfig {
        newDir.mkdirs(); backupsDir.mkdirs()
        val newFile = File(newDir, "${kitName.lowercase()}_block_preview.json")
        if (newFile.exists()) return loadAndFix(newFile)

        val oldPerKit = File(oldDirPerKit, "${kitName.lowercase()}_block_preview.json")
        if (oldPerKit.exists()) {
            val txt = runCatching { oldPerKit.readText() }.getOrNull()
            if (txt != null) {
                newFile.parentFile.mkdirs()
                newFile.writeText(txt)
                backupOnce(oldPerKit, "block_preview_${kitName.lowercase()}")
                return loadAndFix(newFile)
            }
        }

        val oldLegacy = File(oldDirLegacy, "block_${kitName.lowercase()}.json")
        if (oldLegacy.exists()) {
            val oldCfg = runCatching<OldBlockPreviewConfig> {
                KitsConfig.gson.fromJson(oldLegacy.readText(), object : TypeToken<OldBlockPreviewConfig>() {}.type)
            }.getOrNull() ?: OldBlockPreviewConfig()

            val migrated = migrateOldToNew(oldCfg)
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            backupOnce(oldLegacy, "block_${kitName.lowercase()}")
            return migrated
        }

        val def = PerKitBlockPreviewConfig()
        newFile.writeText(KitsConfig.gson.toJson(def))
        return def
    }

    fun saveFor(kitName: String, cfg: PerKitBlockPreviewConfig) {
        val f = File(newDir, "${kitName.lowercase()}_block_preview.json")
        f.parentFile.mkdirs()
        f.writeText(KitsConfig.gson.toJson(cfg))
    }

    fun actionForClick(cfg: PerKitBlockPreviewConfig, isRightClick: Boolean): ClickAction {
        val allowClaim = cfg.allowClaim != false
        val raw = (if (isRightClick) cfg.rightClickAction else cfg.leftClickAction)?.lowercase() ?: ""
        return when (raw) {
            "preview" -> ClickAction.PREVIEW
            "claim"   -> if (allowClaim) ClickAction.CLAIM else ClickAction.NONE
            "none"    -> ClickAction.NONE
            else      -> if (isRightClick) ClickAction.PREVIEW else if (allowClaim) ClickAction.CLAIM else ClickAction.NONE
        }
    }

    private fun migrateOldToNew(old: OldBlockPreviewConfig): PerKitBlockPreviewConfig {
        val filler = Filler(
            name = old.fillerName,
            icon = old.fillItem,
            slots = old.fillerSlots.map { it.toString() },
            modelData = old.fillerCustomModelData,
            glow = false,
            hideAdditionalTooltip = true,
            hideTooltip = false
        )
        val closeBtn = Button(
            slot = old.closeButton.slot,
            icon = old.closeButton.material,
            name = old.closeButton.name,
            modelData = old.closeButton.customModelData
        )
        return PerKitBlockPreviewConfig(
            title = old.title,
            size = old.size,
            fillersEnabled = old.fillersEnabled,
            fillers = mutableListOf(filler),
            closeButton = closeBtn,
            contentSlots = old.previewSlots.map { it.toString() },
            leftClickAction = old.leftClickAction,
            rightClickAction = old.rightClickAction,
            allowClaim = old.allowClaim
        )
    }

    private fun backupOnce(file: File, prefix: String) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val bak = File(backupsDir, "${prefix}_$stamp.json")
            bak.parentFile.mkdirs()
            file.renameTo(bak)
        }
    }

    private fun readJsonObject(file: File): JsonObject? {
        val txt = runCatching { file.readText() }.getOrNull() ?: return null
        return runCatching { JsonParser.parseString(txt).asJsonObject }.getOrNull()
    }

    private fun loadAndFix(file: File): PerKitBlockPreviewConfig {
        val def = PerKitBlockPreviewConfig()
        val txt = runCatching { file.readText() }.getOrNull() ?: return def
        val json = runCatching { JsonParser.parseString(txt).asJsonObject }.getOrNull()

        val parsed = runCatching {
            KitsConfig.gson.fromJson<PerKitBlockPreviewConfig>(txt, object : TypeToken<PerKitBlockPreviewConfig>() {}.type)
        }.getOrNull() ?: def

        val fixedFillersEnabled =
            if (json != null && json.has("fillersEnabled")) parsed.fillersEnabled else def.fillersEnabled

        val parsedFillers = runCatching { parsed.fillers }.getOrNull()
        val hasFillersField = json != null && json.has("fillers")
        val fixedFillers =
            if (!hasFillersField || parsedFillers.isNullOrEmpty()) def.fillers.toMutableList()
            else parsedFillers.toMutableList()

        val fixedCloseButton =
            if (json != null && json.has("closeButton")) parsed.closeButton else def.closeButton

        val fixedContentSlots =
            if (json != null && json.has("contentSlots")) parsed.contentSlots else def.contentSlots

        val fixed = parsed.copy(
            fillersEnabled = fixedFillersEnabled,
            fillers = fixedFillers,
            closeButton = fixedCloseButton,
            contentSlots = fixedContentSlots
        )

        if (fixed != parsed) {
            runCatching { file.writeText(KitsConfig.gson.toJson(fixed)) }
        }

        return fixed
    }
}