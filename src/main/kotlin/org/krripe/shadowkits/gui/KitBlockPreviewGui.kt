package org.krripe.shadowkits.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import org.krripe.shadowkits.config.BlockKitsConfig
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.*

object KitBlockPreviewGui {

    private fun buildItems(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: BlockKitsConfig.BlockPreviewConfig
    ): List<GuiItem> {
        val items = mutableListOf<GuiItem>()

        if (cfg.fillersEnabled) {
            cfg.fillerSlots.forEach { slot ->
                val filler = KitManager.createItemStack(
                    KitsConfig.ItemConfig(
                        material = cfg.fillItem,
                        displayname = cfg.fillerName,
                        customModelData = cfg.fillerCustomModelData
                    ),
                    player,
                    null
                )
                items += GuiItem(filler, slot)
            }
        }

        var i = 0
        kit.items.forEach { ic ->
            if (i >= cfg.previewSlots.size) return@forEach
            items += GuiItem(KitManager.createItemStack(ic, player, kit.name), cfg.previewSlots[i++])
        }
        kit.commands.forEach { cmd ->
            if (i >= cfg.previewSlots.size) return@forEach
            items += GuiItem(KitManager.createItemStack(cmd.displayItem, player, kit.name), cfg.previewSlots[i++])
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

    fun openOrUpdate(
        player: ServerPlayerEntity,
        kit: KitsConfig.KitConfig,
        cfg: BlockKitsConfig.BlockPreviewConfig
    ) {
        val titleText = CustomColorParser
            .processAllFormats(cfg.title.replace("%kit%", kit.name))
            .setStyle(Style.EMPTY.withItalic(false))

        val content = buildItems(player, kit, cfg)

        val updated = updateOpenGui(player, content, expectedSize = cfg.size)
        if (!updated) {
            openGui(
                player,
                titleText,
                content,
                cfg.size,
                getGuiScreenHandlerType(cfg.size),
                object : GuiInteraction {
                    override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                        if (slot == cfg.closeButton.slot) {
                            player.closeHandledScreen()
                        }
                    }
                    override fun onGuiClose(player: ServerPlayerEntity) {}
                }
            )
        }
    }
}