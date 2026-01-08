package org.krripe.shadowkits.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.utils.CustomColorParser
import org.krripe.shadowkits.utils.GuiInteraction
import org.krripe.shadowkits.utils.GuiItem
import org.krripe.shadowkits.utils.openGui
import org.krripe.shadowkits.utils.updateOpenGui

object PrivewGUI {

    private fun rewardsOf(kit: KitsConfig.KitConfig) =
        kit.rewards ?: KitsConfig.migrateLegacy(kit).rewards.orEmpty()

    private fun buildItems(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: PrivewGUIConfig.PerKitPreviewConfig
    ): List<GuiItem> {
        val out = mutableListOf<GuiItem>()

        if (cfg.fillersEnabled) {
            cfg.fillers.forEach { f ->
                val slots = GuiUtils.parseSlots(f.slots, cfg.size)
                val stack = GuiUtils.makeFiller(f)
                slots.forEach { s ->
                    if (s in 0 until cfg.size) out += GuiItem(stack.copy(), s)
                }
            }
        }

        val contentSlots = GuiUtils.parseSlots(cfg.contentSlots, cfg.size)
        val rewards = rewardsOf(kit)
        var i = 0
        while (i < rewards.size && i < contentSlots.size) {
            val slot = contentSlots[i]
            val icon = RewardIconFactory.iconFor(player, rewards[i])
            out += GuiItem(icon, slot)
            i++
        }

        cfg.closeButton?.let { b ->
            if (b.slot in 0 until cfg.size) {
                out += GuiItem(GuiUtils.makeButton(b), b.slot)
            }
        }

        return out
    }

    fun open(player: ServerPlayerEntity, kit: KitsConfig.KitConfig) {
        val cfg  = PrivewGUIConfig.getFor(kit.name)
        val size = cfg.size.coerceIn(9, 54).let { if (it % 9 == 0) it else 54 }
        val items = buildItems(player, kit, cfg)
        val title = CustomColorParser
            .processAllFormats(cfg.title.replace("%kit%", kit.name))
            .setStyle(Style.EMPTY.withItalic(false))

        openGui(
            player,
            title,
            items,
            size,
            GuiTypes.fromSize(size),
            object : GuiInteraction {
                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    val closeSlot = cfg.closeButton?.slot
                    if (closeSlot != null && slot == closeSlot) {
                        MainGUI.open(player)
                        return
                    }
                }
                override fun onGuiClose(player: ServerPlayerEntity) {}
            }
        )
    }
}