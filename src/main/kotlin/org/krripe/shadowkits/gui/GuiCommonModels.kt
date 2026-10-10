package org.krripe.shadowkits.gui

data class GuiItemCfg(
    var item: String = "minecraft:paper",
    var amount: Int = 1,
    var name: String? = null,
    var lore: List<String> = emptyList(),
    var modelData: Int? = null,
    var glow: Boolean = false,
    var hideAdditionalTooltip: Boolean = true,
    var hideTooltip: Boolean = false
)

data class GuiSoundsCfg(
    var enabled: Boolean = true,
    var open: String? = "minecraft:ui.button.click",
    var close: String? = "minecraft:ui.button.click",
    var click: String? = "minecraft:ui.button.click",
    var deny: String? = "minecraft:block.note_block.bass"
)

data class GuiFillerCfg(
    var id: String = "filler",
    var item: GuiItemCfg = GuiItemCfg(
        item = "minecraft:gray_stained_glass_pane",
        name = " "
    ),
    var slots: List<String> = emptyList()
)

data class GuiButtonActionCfg(
    var type: String = "none",
    var command: String = "",
    var runAs: String = "player"
)

data class GuiButtonCfg(
    var id: String = "button",
    var slot: Int = 0,
    var slots: List<String> = emptyList(),
    var item: GuiItemCfg = GuiItemCfg(),
    var disabledItem: GuiItemCfg? = null,
    var requiresPermission: String? = null,
    var clickSound: String? = null,
    var denySound: String? = null,
    var closeOnClick: Boolean = false,
    var actions: List<GuiButtonActionCfg> = emptyList()
)

data class GuiMenuFrameCfg(
    var title: String = "&8Menu",
    var size: Int = 27,
    var sounds: GuiSoundsCfg = GuiSoundsCfg(),
    var fillers: List<GuiFillerCfg> = emptyList(),
    var buttons: List<GuiButtonCfg> = emptyList()
)