package org.krripe.shadowkits.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.*

object KitPreviewGui {

    private fun buildPreviewItems(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: KitsConfig.PreviewMenuConfig
    ): List<GuiItem> {
        val items = mutableListOf<GuiItem>()

        if (cfg.fillersEnabled) {
            cfg.fillerSlots.forEach { slot ->
                if (slot in 0 until cfg.size) {
                    val fillerItem = KitManager.createItemStack(
                        KitsConfig.ItemConfig(
                            material = cfg.fillItem,
                            displayname = cfg.fillerName,
                            customModelData = cfg.fillerCustomModelData
                        ),
                        player,
                        null
                    )
                    items += GuiItem(fillerItem, slot)
                }
            }
        }

        var i = 0
        kit.items.forEach { ic ->
            if (i >= cfg.previewSlots.size) return@forEach
            val stack = KitManager.createItemStack(ic, player, kit.name)
            items += GuiItem(stack, cfg.previewSlots[i++])
        }
        kit.commands.forEach { cmd ->
            if (i >= cfg.previewSlots.size) return@forEach
            val stack = KitManager.createItemStack(cmd.displayItem, player, kit.name)
            items += GuiItem(stack, cfg.previewSlots[i++])
        }

        val closeBtn = KitManager.createItemStack(
            KitsConfig.ItemConfig(
                material = cfg.closeButton.material,
                displayname = cfg.closeButton.name,
                customModelData = cfg.closeButton.customModelData
            ),
            player,
            null
        )
        items += GuiItem(closeBtn, cfg.closeButton.slot)

        return items
    }

    fun openKitPreview(player: ServerPlayerEntity, kit: KitsConfig.KitConfig) {
        val cfg = KitsConfig.menuPreviewConfig
        val size = cfg.size

        val items = buildPreviewItems(player, kit, cfg)

        if (updateOpenGui(player, items, expectedSize = size)) {
            return
        }

        openGui(
            player,
            CustomColorParser.processAllFormats(cfg.title.replace("%kit%", kit.name)).setStyle(Style.EMPTY.withItalic(false)),
            items,
            size,
            getGuiScreenHandlerType(size),
            object : GuiInteraction {
                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    if (slot == cfg.closeButton.slot) {
                        KitGui.openKitsMenu(player)
                        return
                    }
                }
                override fun onGuiClose(player: ServerPlayerEntity) {}
            }
        )
    }
}