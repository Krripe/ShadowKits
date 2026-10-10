package org.krripe.shadowkits.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import org.krripe.shadowkits.utils.CustomColorParser
import org.krripe.shadowkits.utils.PermissionsSystem
import org.krripe.shadowkits.utils.SoundHelper

object GuiMenuUtils {
    private val permissions = PermissionsSystem()

    fun normalizeSize(size: Int): Int = when {
        size <= 9 -> 9
        size <= 18 -> 18
        size <= 27 -> 27
        size <= 36 -> 36
        size <= 45 -> 45
        else -> 54
    }

    fun title(raw: String) = CustomColorParser.processAllFormats(raw).setStyle(Style.EMPTY.withItalic(false))

    fun hasPermission(player: ServerPlayerEntity, permission: String?): Boolean {
        if (permission.isNullOrBlank()) return true
        return permissions.hasPermission(player.commandSource, permission, 0)
    }

    fun playOpen(player: ServerPlayerEntity, sounds: GuiSoundsCfg) {
        if (sounds.enabled) SoundHelper.play(player, sounds.open)
    }

    fun playClose(player: ServerPlayerEntity, sounds: GuiSoundsCfg) {
        if (sounds.enabled) SoundHelper.play(player, sounds.close)
    }

    fun playClick(player: ServerPlayerEntity, sounds: GuiSoundsCfg, override: String? = null) {
        if (sounds.enabled) SoundHelper.play(player, override ?: sounds.click)
    }

    fun playDeny(player: ServerPlayerEntity, sounds: GuiSoundsCfg, override: String? = null) {
        if (sounds.enabled) SoundHelper.play(player, override ?: sounds.deny)
    }

    fun playButtonClick(player: ServerPlayerEntity, sounds: GuiSoundsCfg, button: GuiButtonCfg) =
        playClick(player, sounds, button.clickSound)

    fun playButtonDeny(player: ServerPlayerEntity, sounds: GuiSoundsCfg, button: GuiButtonCfg) =
        playDeny(player, sounds, button.denySound)

    fun fillerItems(
        player: ServerPlayerEntity,
        fillers: List<GuiFillerCfg>,
        size: Int,
        placeholders: Map<String, String> = emptyMap()
    ): List<GuiItem> {
        val items = mutableListOf<GuiItem>()

        fillers.forEach { filler ->
            GuiSlotParser.parse(filler.slots, size).forEach { slot ->
                items += GuiItem(
                    itemStack = GuiItemFactory.fromCfg(player, filler.item, placeholders),
                    slot = slot,
                    id = filler.id
                )
            }
        }

        return items
    }

    fun buttonItems(
        player: ServerPlayerEntity,
        button: GuiButtonCfg,
        size: Int,
        placeholders: Map<String, String> = emptyMap(),
        enabled: Boolean = true
    ): List<GuiItem> {
        val itemCfg = if (enabled) button.item else button.disabledItem ?: button.item

        return GuiSlotParser.buttonSlots(button, size).map { slot ->
            GuiItem(
                itemStack = GuiItemFactory.fromCfg(player, itemCfg, placeholders),
                slot = slot,
                id = button.id
            )
        }
    }

    fun buttonBySlot(buttons: List<GuiButtonCfg>, size: Int, slot: Int): GuiButtonCfg? =
        buttons.firstOrNull { GuiSlotParser.buttonSlots(it, size).contains(slot) }

    fun executeActions(
        player: ServerPlayerEntity,
        actions: List<GuiButtonActionCfg>,
        placeholders: Map<String, String> = emptyMap(),
        onBack: (() -> Unit)? = null
    ) {
        actions.forEach { action ->
            when (action.type.lowercase()) {
                "none" -> {}

                "close" -> player.closeHandledScreen()

                "back" -> onBack?.invoke()

                "command" -> {
                    val command = GuiPlaceholders.apply(action.command, placeholders).removePrefix("/")
                    if (command.isBlank()) return@forEach

                    if (action.runAs.equals("console", true)) {
                        player.server.commandManager.executeWithPrefix(player.server.commandSource, command)
                    } else {
                        player.server.commandManager.executeWithPrefix(player.commandSource, command)
                    }
                }
            }
        }
    }
}