package org.krripe.shadowkits.gui

data class Button(
    val slot: Int,
    val icon: String,
    val name: String,
    val lore: List<String> = emptyList(),
    val modelData: Int? = null,
    val glow: Boolean = false,
    val hideAdditionalTooltip: Boolean = true,
    val hideTooltip: Boolean = false
)

data class Filler(
    val name: String = " ",
    val lore: List<String> = emptyList(),
    val icon: String = "minecraft:gray_stained_glass_pane",
    val slots: List<String> = emptyList(),
    val modelData: Int? = null,
    val glow: Boolean = false,
    val hideAdditionalTooltip: Boolean = true,
    val hideTooltip: Boolean = false
)

data class GuiSound(
    val id: String,
    val volume: Float = 1f,
    val pitch: Float = 1f
)

data class MainGuiSounds(
    val open: GuiSound   = GuiSound("minecraft:block.chest.open"),
    val close: GuiSound  = GuiSound("minecraft:block.chest.close"),
    val preview: GuiSound= GuiSound("minecraft:block.chest.open"),
    val claim: GuiSound  = GuiSound("minecraft:entity.experience_orb.pickup")
)

data class PagedWithContent(
    val title: String,
    val size: Int = 54,
    val fillersEnabled: Boolean = true,
    val fillers: MutableList<Filler> = mutableListOf(),
    val nextPageButton: Button? = null,
    val prevPageButton: Button? = null,
    val closeButton: Button? = null,
    val contentSlots: List<String> = emptyList(),
    val sounds: MainGuiSounds = MainGuiSounds()
)
