package org.krripe.shadowkits.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.utils.CustomColorParser
import org.krripe.shadowkits.utils.GuiInteraction
import org.krripe.shadowkits.utils.GuiItem
import org.krripe.shadowkits.utils.getGuiScreenHandlerType
import org.krripe.shadowkits.utils.openGui
import org.krripe.shadowkits.utils.updateOpenGui

object BlockPrivewGUI {

    private fun rewardsOf(kit: KitsConfig.KitConfig): List<KitsConfig.Reward> =
        kit.rewards ?: KitsConfig.migrateLegacy(kit).rewards.orEmpty()

    private fun buildItems(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: BlockPrivewGUIConfig.PerKitBlockPreviewConfig,
        size: Int
    ): List<GuiItem> {
        val out = mutableListOf<GuiItem>()

        if (cfg.fillersEnabled) {
            cfg.fillers.forEach { f ->
                val slots = GuiUtils.parseSlots(f.slots, size)
                val stack = GuiUtils.makeFiller(f)
                slots.forEach { s ->
                    if (s in 0 until size) out += GuiItem(stack.copy(), s)
                }
            }
        }

        val contentSlots = GuiUtils.parseSlots(cfg.contentSlots, size)
        val rewards = rewardsOf(kit)

        var i = 0
        while (i < rewards.size && i < contentSlots.size) {
            val slot = contentSlots[i]
            if (slot in 0 until size) {
                val icon = RewardIconFactory.iconFor(player, rewards[i])
                out += GuiItem(icon, slot)
            }
            i++
        }

        cfg.closeButton?.let { b ->
            if (b.slot in 0 until size) {
                out += GuiItem(GuiUtils.makeButton(b), b.slot)
            }
        }

        return out
    }

    fun openOrUpdate(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: BlockPrivewGUIConfig.PerKitBlockPreviewConfig
    ) {
        val size = cfg.size.coerceIn(9, 54).let { if (it % 9 == 0) it else 54 }

        val title = CustomColorParser
            .processAllFormats(cfg.title.replace("%kit%", kit.name))
            .setStyle(Style.EMPTY.withItalic(false))

        val content = buildItems(player, kit, cfg, size)
        if (updateOpenGui(player, content, expectedSize = size)) return

        openGui(
            player,
            title,
            content,
            size,
            getGuiScreenHandlerType(size),
            object : GuiInteraction {
                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    if (cfg.closeButton?.slot == slot) player.closeHandledScreen()
                }
                override fun onGuiClose(player: ServerPlayerEntity) {}
            }
        )
    }
}