package org.krripe.shadowkits.gui.block

import com.google.gson.JsonParser
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.gui.GuiButtonActionCfg
import org.krripe.shadowkits.gui.GuiButtonCfg
import org.krripe.shadowkits.gui.GuiConfigLoader
import org.krripe.shadowkits.gui.GuiFillerCfg
import org.krripe.shadowkits.gui.GuiItemCfg
import org.krripe.shadowkits.gui.GuiMenuFrameCfg
import org.krripe.shadowkits.gui.GuiSoundsCfg
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object BlockPrivewGUIConfig {

    enum class ClickAction { PREVIEW, CLAIM, NONE }

    data class PerKitBlockPreviewConfig(
        var frame: GuiMenuFrameCfg = GuiMenuFrameCfg(
            title = "&6Block Kit Preview: %kit%",
            size = 54,
            sounds = GuiSoundsCfg(
                enabled = true,
                open = null,
                close = null,
                click = "minecraft:ui.button.click",
                deny = "minecraft:block.note_block.bass"
            ),
            fillers = listOf(
                GuiFillerCfg(
                    id = "border",
                    item = GuiItemCfg(
                        item = "minecraft:black_stained_glass_pane",
                        amount = 1,
                        name = " "
                    ),
                    slots = listOf("0-8", "9", "17", "18", "26", "27", "35", "36", "44", "45-48", "50-53")
                )
            ),
            buttons = listOf(
                GuiButtonCfg(
                    id = "close",
                    slot = 49,
                    item = GuiItemCfg(
                        item = "minecraft:barrier",
                        amount = 1,
                        name = "&cClose"
                    ),
                    actions = listOf(GuiButtonActionCfg(type = "close"))
                )
            )
        ),
        var contentSlots: List<String> = listOf("10-16", "19-25", "28-34", "37-43"),
        var leftClickAction: String? = "claim",
        var rightClickAction: String? = "preview",
        var allowClaim: Boolean? = true
    )

    private data class LegacyFiller(
        val name: String? = null,
        val lore: List<String>? = null,
        val icon: String? = null,
        val slots: List<String>? = null,
        val modelData: Int? = null,
        val glow: Boolean? = null,
        val hideAdditionalTooltip: Boolean? = null,
        val hideTooltip: Boolean? = null
    )

    private data class LegacyButton(
        val slot: Int? = null,
        val icon: String? = null,
        val name: String? = null,
        val lore: List<String>? = null,
        val modelData: Int? = null,
        val glow: Boolean? = null,
        val hideAdditionalTooltip: Boolean? = null,
        val hideTooltip: Boolean? = null
    )

    private data class LegacyBlockPreviewConfig(
        val title: String? = null,
        val size: Int? = null,
        val fillersEnabled: Boolean? = null,
        val fillers: List<LegacyFiller>? = null,
        val closeButton: LegacyButton? = null,
        val contentSlots: List<String>? = null,
        val leftClickAction: String? = null,
        val rightClickAction: String? = null,
        val allowClaim: Boolean? = null
    )

    private data class VeryOldButton(
        val slot: Int? = null,
        val material: String? = null,
        val name: String? = null,
        val customModelData: Int? = null
    )

    private data class VeryOldBlockPreviewConfig(
        val title: String? = null,
        val size: Int? = null,
        val fillersEnabled: Boolean? = null,
        val fillItem: String? = null,
        val fillerName: String? = null,
        val fillerCustomModelData: Int? = null,
        val fillerSlots: List<Int>? = null,
        val previewSlots: List<Int>? = null,
        val closeButton: VeryOldButton? = null,
        val leftClickAction: String? = null,
        val rightClickAction: String? = null,
        val allowClaim: Boolean? = null
    )

    private val rootDir = File("config/shadowkits")
    private val newDir = File(rootDir, "gui/block_previews")
    private val oldPerKitDir = File(rootDir, "block_previews")
    private val oldLegacyDir = File(rootDir, "blocks_configs")
    private val backupsDir = File(rootDir, "backups/gui/block_previews")

    fun reload() {
        newDir.mkdirs()
        backupsDir.mkdirs()
    }

    fun ensureKitTemplate(kitName: String) {
        val file = File(newDir, "${kitName.lowercase()}_block_preview.json")
        if (!file.exists()) getFor(kitName)
    }

    fun getFor(kitName: String): PerKitBlockPreviewConfig {
        newDir.mkdirs()
        backupsDir.mkdirs()

        val name = kitName.lowercase()
        val newFile = File(newDir, "${name}_block_preview.json")

        if (newFile.exists()) {
            val root = runCatching { JsonParser.parseString(newFile.readText()).asJsonObject }.getOrNull()

            if (root?.has("frame") == true) {
                return GuiConfigLoader.loadOrCreate(newFile, PerKitBlockPreviewConfig())
            }

            backup(newFile, "${name}_block_preview_legacy")

            val legacy = runCatching {
                KitsConfig.gson.fromJson(newFile.readText(), LegacyBlockPreviewConfig::class.java)
            }.getOrNull()

            val migrated = legacy?.let(::migrateLegacy) ?: PerKitBlockPreviewConfig()
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            return migrated
        }

        val oldPerKit = File(oldPerKitDir, "${name}_block_preview.json")

        if (oldPerKit.exists()) {
            backup(oldPerKit, "${name}_block_preview_old")

            val legacy = runCatching {
                KitsConfig.gson.fromJson(oldPerKit.readText(), LegacyBlockPreviewConfig::class.java)
            }.getOrNull()

            val migrated = legacy?.let(::migrateLegacy) ?: PerKitBlockPreviewConfig()

            newFile.parentFile.mkdirs()
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            return migrated
        }

        val oldLegacy = File(oldLegacyDir, "block_$name.json")

        if (oldLegacy.exists()) {
            backup(oldLegacy, "block_${name}_legacy")

            val legacy = runCatching {
                KitsConfig.gson.fromJson(oldLegacy.readText(), VeryOldBlockPreviewConfig::class.java)
            }.getOrNull()

            val migrated = legacy?.let(::migrateVeryOld) ?: PerKitBlockPreviewConfig()

            newFile.parentFile.mkdirs()
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            return migrated
        }

        val defaults = PerKitBlockPreviewConfig()
        newFile.parentFile.mkdirs()
        newFile.writeText(KitsConfig.gson.toJson(defaults))
        return defaults
    }

    fun saveFor(kitName: String, cfg: PerKitBlockPreviewConfig) {
        val file = File(newDir, "${kitName.lowercase()}_block_preview.json")
        file.parentFile.mkdirs()
        file.writeText(KitsConfig.gson.toJson(cfg))
    }

    fun actionForClick(cfg: PerKitBlockPreviewConfig, isRightClick: Boolean): ClickAction {
        val allowClaim = cfg.allowClaim != false
        val raw = (if (isRightClick) cfg.rightClickAction else cfg.leftClickAction)?.lowercase() ?: ""

        return when (raw) {
            "preview" -> ClickAction.PREVIEW
            "claim" -> if (allowClaim) ClickAction.CLAIM else ClickAction.NONE
            "none" -> ClickAction.NONE
            else -> if (isRightClick) ClickAction.PREVIEW else if (allowClaim) ClickAction.CLAIM else ClickAction.NONE
        }
    }

    private fun migrateLegacy(old: LegacyBlockPreviewConfig): PerKitBlockPreviewConfig {
        val defaults = PerKitBlockPreviewConfig()

        val fillers = when {
            old.fillersEnabled == false -> emptyList()

            !old.fillers.isNullOrEmpty() -> old.fillers.mapIndexed { index, filler ->
                GuiFillerCfg(
                    id = "legacy_filler_$index",
                    item = GuiItemCfg(
                        item = filler.icon ?: "minecraft:black_stained_glass_pane",
                        name = filler.name ?: " ",
                        lore = filler.lore.orEmpty(),
                        modelData = filler.modelData,
                        glow = filler.glow == true,
                        hideAdditionalTooltip = filler.hideAdditionalTooltip != false,
                        hideTooltip = filler.hideTooltip == true
                    ),
                    slots = filler.slots.orEmpty()
                )
            }

            else -> defaults.frame.fillers
        }

        val closeButton = old.closeButton?.let { button ->
            GuiButtonCfg(
                id = "close",
                slot = button.slot ?: 49,
                item = GuiItemCfg(
                    item = button.icon ?: "minecraft:barrier",
                    name = button.name ?: "&cClose",
                    lore = button.lore.orEmpty(),
                    modelData = button.modelData,
                    glow = button.glow == true,
                    hideAdditionalTooltip = button.hideAdditionalTooltip != false,
                    hideTooltip = button.hideTooltip == true
                ),
                actions = listOf(GuiButtonActionCfg(type = "close"))
            )
        } ?: defaults.frame.buttons.first()

        return PerKitBlockPreviewConfig(
            frame = GuiMenuFrameCfg(
                title = old.title ?: defaults.frame.title,
                size = old.size ?: defaults.frame.size,
                sounds = defaults.frame.sounds,
                fillers = fillers,
                buttons = listOf(closeButton)
            ),
            contentSlots = old.contentSlots ?: defaults.contentSlots,
            leftClickAction = old.leftClickAction ?: defaults.leftClickAction,
            rightClickAction = old.rightClickAction ?: defaults.rightClickAction,
            allowClaim = old.allowClaim ?: defaults.allowClaim
        )
    }

    private fun migrateVeryOld(old: VeryOldBlockPreviewConfig): PerKitBlockPreviewConfig {
        val defaults = PerKitBlockPreviewConfig()

        val filler = GuiFillerCfg(
            id = "legacy_border",
            item = GuiItemCfg(
                item = old.fillItem ?: "minecraft:black_stained_glass_pane",
                name = old.fillerName ?: " ",
                modelData = old.fillerCustomModelData
            ),
            slots = old.fillerSlots.orEmpty().map(Int::toString)
        )

        val oldButton = old.closeButton

        val closeButton = GuiButtonCfg(
            id = "close",
            slot = oldButton?.slot ?: 49,
            item = GuiItemCfg(
                item = oldButton?.material ?: "minecraft:barrier",
                name = oldButton?.name ?: "&cClose",
                modelData = oldButton?.customModelData
            ),
            actions = listOf(GuiButtonActionCfg(type = "close"))
        )

        return PerKitBlockPreviewConfig(
            frame = GuiMenuFrameCfg(
                title = old.title ?: defaults.frame.title,
                size = old.size ?: defaults.frame.size,
                sounds = defaults.frame.sounds,
                fillers = if (old.fillersEnabled != false) listOf(filler) else emptyList(),
                buttons = listOf(closeButton)
            ),
            contentSlots = old.previewSlots?.map(Int::toString) ?: defaults.contentSlots,
            leftClickAction = old.leftClickAction ?: defaults.leftClickAction,
            rightClickAction = old.rightClickAction ?: defaults.rightClickAction,
            allowClaim = old.allowClaim ?: defaults.allowClaim
        )
    }

    private fun backup(source: File, prefix: String) {
        runCatching {
            if (!source.exists()) return

            backupsDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
            source.copyTo(File(backupsDir, "${prefix}_$stamp.json"), overwrite = false)
        }
    }
}