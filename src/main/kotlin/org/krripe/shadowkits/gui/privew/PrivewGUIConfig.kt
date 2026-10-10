package org.krripe.shadowkits.gui.privew

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

object PrivewGUIConfig {

    data class PerKitPreviewConfig(
        var frame: GuiMenuFrameCfg = GuiMenuFrameCfg(
            title = "&6Kit Preview: %kit%",
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
                    id = "back",
                    slot = 49,
                    item = GuiItemCfg(
                        item = "minecraft:barrier",
                        amount = 1,
                        name = "&cClose"
                    ),
                    actions = listOf(
                        GuiButtonActionCfg(type = "back")
                    )
                )
            )
        ),
        var contentSlots: List<String> = listOf("10-16", "19-25", "28-34", "37-43")
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

    private data class LegacyPreviewConfig(
        val title: String? = null,
        val size: Int? = null,
        val fillersEnabled: Boolean? = null,
        val fillers: List<LegacyFiller>? = null,
        val closeButton: LegacyButton? = null,
        val contentSlots: List<String>? = null
    )

    private data class VeryOldButton(
        val slot: Int? = null,
        val material: String? = null,
        val name: String? = null,
        val customModelData: Int? = null
    )

    private data class VeryOldPreviewConfig(
        val title: String? = null,
        val size: Int? = null,
        val fillItem: String? = null,
        val fillerName: String? = null,
        val fillerCustomModelData: Int? = null,
        val fillersEnabled: Boolean? = null,
        val fillerSlots: List<Int>? = null,
        val previewSlots: List<Int>? = null,
        val closeButton: VeryOldButton? = null
    )

    private val rootDir = File("config/shadowkits")
    private val previewsDir = File(rootDir, "gui/previews")
    private val oldPerKitDir = File(rootDir, "previews")
    private val oldGlobalFile = File(rootDir, "menu_preview.json")
    private val backupsDir = File(rootDir, "backups/gui/previews")

    @Volatile private var cachedOldGlobal: VeryOldPreviewConfig? = null
    @Volatile private var oldGlobalChecked = false

    fun resetCacheTemplate() {
        cachedOldGlobal = null
        oldGlobalChecked = false
    }

    fun getFor(kitName: String): PerKitPreviewConfig {
        previewsDir.mkdirs()
        backupsDir.mkdirs()

        val name = kitName.lowercase()
        val newFile = File(previewsDir, "${name}_preview.json")

        if (newFile.exists()) {
            val root = runCatching { JsonParser.parseString(newFile.readText()).asJsonObject }.getOrNull()

            if (root?.has("frame") == true) {
                return GuiConfigLoader.loadOrCreate(newFile, PerKitPreviewConfig())
            }

            backup(newFile, "${name}_preview_legacy")

            val legacy = runCatching {
                KitsConfig.gson.fromJson(newFile.readText(), LegacyPreviewConfig::class.java)
            }.getOrNull()

            val migrated = legacy?.let(::migrateLegacy) ?: PerKitPreviewConfig()
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            return migrated
        }

        val oldPerKit = File(oldPerKitDir, "${name}_preview.json")

        if (oldPerKit.exists()) {
            backup(oldPerKit, "${name}_preview_old")

            val legacy = runCatching {
                KitsConfig.gson.fromJson(oldPerKit.readText(), LegacyPreviewConfig::class.java)
            }.getOrNull()

            val migrated = legacy?.let(::migrateLegacy) ?: PerKitPreviewConfig()

            newFile.parentFile.mkdirs()
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            return migrated
        }

        val oldGlobal = loadOldGlobal()

        if (oldGlobal != null) {
            val migrated = migrateVeryOld(oldGlobal)
            newFile.writeText(KitsConfig.gson.toJson(migrated))
            return migrated
        }

        val defaults = PerKitPreviewConfig()
        newFile.writeText(KitsConfig.gson.toJson(defaults))
        return defaults
    }

    fun saveFor(kitName: String, cfg: PerKitPreviewConfig) {
        val file = File(previewsDir, "${kitName.lowercase()}_preview.json")
        file.parentFile.mkdirs()
        file.writeText(KitsConfig.gson.toJson(cfg))
    }

    private fun migrateLegacy(old: LegacyPreviewConfig): PerKitPreviewConfig {
        val defaults = PerKitPreviewConfig()

        val fillers = when {
            old.fillersEnabled == false -> emptyList()

            old.fillers != null -> old.fillers.mapIndexed { index, filler ->
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

        val backButton = old.closeButton?.let { button ->
            GuiButtonCfg(
                id = "back",
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
                actions = listOf(
                    GuiButtonActionCfg(type = "back")
                )
            )
        } ?: defaults.frame.buttons.first()

        return PerKitPreviewConfig(
            frame = GuiMenuFrameCfg(
                title = old.title ?: defaults.frame.title,
                size = old.size ?: defaults.frame.size,
                sounds = defaults.frame.sounds,
                fillers = fillers,
                buttons = listOf(backButton)
            ),
            contentSlots = old.contentSlots ?: defaults.contentSlots
        )
    }

    private fun migrateVeryOld(old: VeryOldPreviewConfig): PerKitPreviewConfig {
        val defaults = PerKitPreviewConfig()

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

        val backButton = GuiButtonCfg(
            id = "back",
            slot = oldButton?.slot ?: 49,
            item = GuiItemCfg(
                item = oldButton?.material ?: "minecraft:barrier",
                name = oldButton?.name ?: "&cClose",
                modelData = oldButton?.customModelData
            ),
            actions = listOf(
                GuiButtonActionCfg(type = "back")
            )
        )

        return PerKitPreviewConfig(
            frame = GuiMenuFrameCfg(
                title = old.title ?: defaults.frame.title,
                size = old.size ?: defaults.frame.size,
                sounds = defaults.frame.sounds,
                fillers = if (old.fillersEnabled != false) listOf(filler) else emptyList(),
                buttons = listOf(backButton)
            ),
            contentSlots = old.previewSlots?.map(Int::toString) ?: defaults.contentSlots
        )
    }

    private fun loadOldGlobal(): VeryOldPreviewConfig? {
        if (oldGlobalChecked) return cachedOldGlobal
        oldGlobalChecked = true

        if (!oldGlobalFile.exists()) return null

        backup(oldGlobalFile, "menu_preview_legacy")

        cachedOldGlobal = runCatching {
            KitsConfig.gson.fromJson(oldGlobalFile.readText(), VeryOldPreviewConfig::class.java)
        }.getOrNull()

        return cachedOldGlobal
    }

    private fun backup(source: File, prefix: String) {
        runCatching {
            if (!source.exists()) return

            backupsDir.mkdirs()

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
            val backup = File(backupsDir, "${prefix}_$stamp.json")

            source.copyTo(backup, overwrite = false)
        }
    }
}