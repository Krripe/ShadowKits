package org.krripe.shadowkits.gui.block

import net.minecraft.server.network.ServerPlayerEntity
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.gui.GuiInteraction
import org.krripe.shadowkits.gui.GuiItem
import org.krripe.shadowkits.gui.GuiMenuUtils
import org.krripe.shadowkits.gui.GuiPlaceholders
import org.krripe.shadowkits.gui.GuiSlotParser
import org.krripe.shadowkits.gui.RewardIconFactory
import org.krripe.shadowkits.gui.getGuiScreenHandlerType
import org.krripe.shadowkits.gui.openGui
import org.krripe.shadowkits.kits.KitManager
import kotlin.collections.plusAssign

object BlockPrivewGUI {

    private fun placeholders(kit: KitsConfig.KitConfig): Map<String, String> {
        val rewards = KitManager.effectiveRewards(kit)

        return mapOf(
            "kit" to kit.name,
            "kit_name" to kit.name,
            "reward_count" to rewards.size.toString()
        )
    }

    private fun buildItems(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: BlockPrivewGUIConfig.PerKitBlockPreviewConfig
    ): List<GuiItem> {
        val frame = cfg.frame
        val size = GuiMenuUtils.normalizeSize(frame.size)
        val placeholders = placeholders(kit)
        val items = mutableListOf<GuiItem>()

        items += GuiMenuUtils.fillerItems(player, frame.fillers, size, placeholders)

        val contentSlots = GuiSlotParser.parse(cfg.contentSlots, size)
        val rewards = KitManager.effectiveRewards(kit)

        rewards.forEachIndexed { index, reward ->
            val slot = contentSlots.getOrNull(index) ?: return@forEachIndexed
            items += GuiItem(
                itemStack = RewardIconFactory.iconFor(player, reward),
                slot = slot,
                id = "reward:$index"
            )
        }

        frame.buttons.forEach { button ->
            val enabled = GuiMenuUtils.hasPermission(player, button.requiresPermission)

            if (enabled || button.disabledItem != null) {
                items += GuiMenuUtils.buttonItems(player, button, size, placeholders, enabled)
            }
        }

        return items
    }

    fun openOrUpdate(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: BlockPrivewGUIConfig.PerKitBlockPreviewConfig
    ) {
        val frame = cfg.frame
        val size = GuiMenuUtils.normalizeSize(frame.size)
        val placeholders = placeholders(kit)

        GuiMenuUtils.playOpen(player, frame.sounds)

        openGui(
            player = player,
            title = GuiMenuUtils.title(GuiPlaceholders.apply(frame.title, placeholders)),
            guiItems = buildItems(player, kit, cfg),
            inventorySize = size,
            screenHandlerType = getGuiScreenHandlerType(size),
            interaction = object : GuiInteraction {
                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    val button = GuiMenuUtils.buttonBySlot(frame.buttons, size, slot) ?: return

                    if (!GuiMenuUtils.hasPermission(player, button.requiresPermission)) {
                        GuiMenuUtils.playButtonDeny(player, frame.sounds, button)
                        player.sendMessage(KitsConfig.getMessage("noPermission"), false)
                        return
                    }

                    GuiMenuUtils.playButtonClick(player, frame.sounds, button)

                    GuiMenuUtils.executeActions(
                        player = player,
                        actions = button.actions,
                        placeholders = placeholders
                    )

                    if (button.closeOnClick) player.closeHandledScreen()
                }

                override fun onGuiClose(player: ServerPlayerEntity) {
                    GuiMenuUtils.playClose(player, frame.sounds)
                }
            }
        )
    }
}