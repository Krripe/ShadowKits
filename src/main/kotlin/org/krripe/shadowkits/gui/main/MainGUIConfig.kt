package org.krripe.shadowkits.gui.main

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

data class MainMenuCfgData(
    var frame: GuiMenuFrameCfg = GuiMenuFrameCfg(
        title = "&6Select a Kit",
        size = 54,
        sounds = GuiSoundsCfg(
            enabled = true,
            open = "minecraft:block.chest.open",
            close = "minecraft:block.chest.close",
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
                id = "previous_page",
                slot = 45,
                item = GuiItemCfg(
                    item = "minecraft:arrow",
                    name = "&aPrevious Page",
                    lore = listOf("&7Page &f%page%&7/&f%pages%", "", "&eClick to go back.")
                ),
                disabledItem = GuiItemCfg(
                    item = "minecraft:gray_dye",
                    name = "&7Previous Page",
                    lore = listOf("&7Page &f%page%&7/&f%pages%", "", "&8No previous page.")
                )
            ),
            GuiButtonCfg(
                id = "close",
                slot = 49,
                item = GuiItemCfg(
                    item = "minecraft:barrier",
                    name = "&cClose",
                    lore = listOf("&7Close this menu.")
                ),
                actions = listOf(GuiButtonActionCfg(type = "close"))
            ),
            GuiButtonCfg(
                id = "next_page",
                slot = 53,
                item = GuiItemCfg(
                    item = "minecraft:arrow",
                    name = "&aNext Page",
                    lore = listOf("&7Page &f%page%&7/&f%pages%", "", "&eClick to continue.")
                ),
                disabledItem = GuiItemCfg(
                    item = "minecraft:gray_dye",
                    name = "&7Next Page",
                    lore = listOf("&7Page &f%page%&7/&f%pages%", "", "&8No next page.")
                )
            )
        )
    ),
    var contentSlots: List<String> = emptyList(),
    var claimSound: String? = "minecraft:entity.experience_orb.pickup",
    var previewSound: String? = "minecraft:block.chest.open"
)

object MainGUIConfig {
    private val file = File("config/shadowkits/gui/main_menu.json")
    private val veryOldFile = File("config/shadowkits/menu.json")
    private val backupsDir = File("config/shadowkits/backups/gui")

    @Volatile private var loaded = false

    var config: MainMenuCfgData = MainMenuCfgData()
        private set

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

    private data class LegacySound(
        val id: String? = null,
        val volume: Float? = null,
        val pitch: Float? = null
    )

    private data class LegacySounds(
        val open: LegacySound? = null,
        val close: LegacySound? = null,
        val preview: LegacySound? = null,
        val claim: LegacySound? = null
    )

    private data class LegacyMainMenu(
        val title: String? = null,
        val size: Int? = null,
        val fillersEnabled: Boolean? = null,
        val fillers: List<LegacyFiller>? = null,
        val nextPageButton: LegacyButton? = null,
        val prevPageButton: LegacyButton? = null,
        val closeButton: LegacyButton? = null,
        val contentSlots: List<String>? = null,
        val sounds: LegacySounds? = null
    )

    private data class VeryOldButton(
        val slot: Int? = null,
        val material: String? = null,
        val name: String? = null,
        val customModelData: Int? = null
    )

    private data class VeryOldMenu(
        val title: String? = null,
        val size: Int? = null,
        val fillItem: String? = null,
        val fillerName: String? = null,
        val fillerCustomModelData: Int? = null,
        val fillersEnabled: Boolean? = null,
        val fillerSlots: List<Int>? = null,
        val nextPageButton: VeryOldButton? = null,
        val prevPageButton: VeryOldButton? = null,
        val closeButton: VeryOldButton? = null
    )

    fun current(): MainMenuCfgData {
        if (!loaded) load()
        return config
    }

    fun load() {
        file.parentFile.mkdirs()

        if (file.exists()) {
            val root = runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrNull()

            if (root?.has("frame") == true) {
                config = GuiConfigLoader.loadOrCreate(file, MainMenuCfgData())
                loaded = true
                return
            }

            val legacy = runCatching { KitsConfig.gson.fromJson(file.readText(), LegacyMainMenu::class.java) }.getOrNull()
            if (legacy != null) {
                backup(file, "main_menu_legacy")
                config = migrateLegacy(legacy)
                file.writeText(KitsConfig.gson.toJson(config))
                loaded = true
                return
            }
        }

        if (veryOldFile.exists()) {
            val legacy = runCatching { KitsConfig.gson.fromJson(veryOldFile.readText(), VeryOldMenu::class.java) }.getOrNull()

            if (legacy != null) {
                backup(veryOldFile, "menu_legacy")
                config = migrateVeryOld(legacy)
                file.writeText(KitsConfig.gson.toJson(config))
                veryOldFile.delete()
                loaded = true
                return
            }
        }

        config = GuiConfigLoader.loadOrCreate(file, MainMenuCfgData())
        loaded = true
    }

    fun reload(): MainMenuCfgData {
        loaded = false
        load()
        return config
    }

    private fun migrateLegacy(old: LegacyMainMenu): MainMenuCfgData {
        val defaults = MainMenuCfgData()

        val fillers = if (old.fillersEnabled != false) {
            old.fillers.orEmpty().mapIndexed { index, filler ->
                GuiFillerCfg(
                    id = "legacy_filler_$index",
                    item = GuiItemCfg(
                        item = filler.icon ?: "minecraft:gray_stained_glass_pane",
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
        } else {
            emptyList()
        }

        val buttons = mutableListOf<GuiButtonCfg>()
        legacyButton("previous_page", old.prevPageButton)?.let(buttons::add)
        legacyButton("close", old.closeButton, close = true)?.let(buttons::add)
        legacyButton("next_page", old.nextPageButton)?.let(buttons::add)

        return MainMenuCfgData(
            frame = GuiMenuFrameCfg(
                title = old.title ?: defaults.frame.title,
                size = old.size ?: defaults.frame.size,
                sounds = GuiSoundsCfg(
                    enabled = true,
                    open = old.sounds?.open?.id ?: defaults.frame.sounds.open,
                    close = old.sounds?.close?.id ?: defaults.frame.sounds.close,
                    click = old.sounds?.claim?.id ?: defaults.frame.sounds.click,
                    deny = defaults.frame.sounds.deny
                ),
                fillers = fillers,
                buttons = buttons
            ),
            contentSlots = old.contentSlots.orEmpty(),
            claimSound = old.sounds?.claim?.id ?: defaults.claimSound,
            previewSound = old.sounds?.preview?.id ?: defaults.previewSound
        )
    }

    private fun legacyButton(id: String, old: LegacyButton?, close: Boolean = false): GuiButtonCfg? {
        old ?: return null

        return GuiButtonCfg(
            id = id,
            slot = old.slot ?: return null,
            item = GuiItemCfg(
                item = old.icon ?: "minecraft:paper",
                name = old.name ?: "&fButton",
                lore = old.lore.orEmpty(),
                modelData = old.modelData,
                glow = old.glow == true,
                hideAdditionalTooltip = old.hideAdditionalTooltip != false,
                hideTooltip = old.hideTooltip == true
            ),
            actions = if (close) listOf(GuiButtonActionCfg(type = "close")) else emptyList()
        )
    }

    private fun migrateVeryOld(old: VeryOldMenu): MainMenuCfgData {
        val defaults = MainMenuCfgData()

        val filler = GuiFillerCfg(
            id = "legacy_border",
            item = GuiItemCfg(
                item = old.fillItem ?: "minecraft:black_stained_glass_pane",
                name = old.fillerName ?: " ",
                modelData = old.fillerCustomModelData
            ),
            slots = old.fillerSlots.orEmpty().map(Int::toString)
        )

        val buttons = mutableListOf<GuiButtonCfg>()
        veryOldButton("previous_page", old.prevPageButton)?.let(buttons::add)
        veryOldButton("close", old.closeButton, close = true)?.let(buttons::add)
        veryOldButton("next_page", old.nextPageButton)?.let(buttons::add)

        return MainMenuCfgData(
            frame = GuiMenuFrameCfg(
                title = old.title ?: defaults.frame.title,
                size = old.size ?: defaults.frame.size,
                sounds = defaults.frame.sounds,
                fillers = if (old.fillersEnabled != false) listOf(filler) else emptyList(),
                buttons = buttons
            ),
            contentSlots = emptyList(),
            claimSound = defaults.claimSound,
            previewSound = defaults.previewSound
        )
    }

    private fun veryOldButton(id: String, old: VeryOldButton?, close: Boolean = false): GuiButtonCfg? {
        old ?: return null

        return GuiButtonCfg(
            id = id,
            slot = old.slot ?: return null,
            item = GuiItemCfg(
                item = old.material ?: "minecraft:paper",
                name = old.name ?: "&fButton",
                modelData = old.customModelData
            ),
            actions = if (close) listOf(GuiButtonActionCfg(type = "close")) else emptyList()
        )
    }

    private fun backup(source: File, prefix: String) {
        runCatching {
            backupsDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
            source.copyTo(File(backupsDir, "${prefix}_$stamp.json"), overwrite = false)
        }
    }
}