package org.krripe.shadowkits.gui

import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.*
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

object KitGui {

    private val liveRefresh = mutableMapOf<UUID, Timer>()

    private fun cancelRefresh(player: ServerPlayerEntity) {
        liveRefresh.remove(player.uuid)?.let { t ->
            runCatching { t.cancel() }
            runCatching { t.purge() }
        }
    }

    fun shutdownAll() {
        liveRefresh.values.forEach { t ->
            runCatching { t.cancel() }
            runCatching { t.purge() }
        }
        liveRefresh.clear()
    }

    private fun kitsForPage(page: Int) =
        KitsConfig.kits.values.filter { it.page == page }

    private fun buildItemsForPage(player: ServerPlayerEntity, page: Int): List<GuiItem> {
        val config = KitsConfig.menuConfig
        val size   = config.size
        val items  = mutableListOf<GuiItem>()

        if (config.fillersEnabled) {
            config.fillerSlots.forEach { slot ->
                if (slot in 0 until size) {
                    val filler = KitManager.createItemStack(
                        KitsConfig.ItemConfig(
                            material = config.fillItem,
                            displayname = config.fillerName,
                            customModelData = config.fillerCustomModelData
                        ), player, null
                    )
                    items += GuiItem(filler, slot)
                }
            }
        }

        kitsForPage(page).forEach { kit ->
            val slot = kit.slotOnPage.takeIf { it in 0 until size } ?: return@forEach
            val display = KitManager.createItemStack(kit.displayItem, player, kit.name)
            items += GuiItem(display, slot)
        }

        if (page > 1) {
            val prev = KitManager.createItemStack(
                KitsConfig.ItemConfig(
                    material = config.prevPageButton.material,
                    displayname = config.prevPageButton.name,
                    customModelData = config.prevPageButton.customModelData
                ), player, null
            )
            items += GuiItem(prev, config.prevPageButton.slot)
        }
        if (page < config.pages) {
            val next = KitManager.createItemStack(
                KitsConfig.ItemConfig(
                    material = config.nextPageButton.material,
                    displayname = config.nextPageButton.name,
                    customModelData = config.nextPageButton.customModelData
                ), player, null
            )
            items += GuiItem(next, config.nextPageButton.slot)
        }

        val close = KitManager.createItemStack(
            KitsConfig.ItemConfig(
                material = config.closeButton.material,
                displayname = config.closeButton.name,
                customModelData = config.closeButton.customModelData
            ), player, null
        )
        items += GuiItem(close, config.closeButton.slot)

        return items
    }

    private fun kitOnSlot(page: Int, slot: Int): KitsConfig.KitConfig? =
        kitsForPage(page).firstOrNull { it.slotOnPage == slot }

    fun openKitsMenu(player: ServerPlayerEntity, page: Int = 1) {
        val config   = KitsConfig.menuConfig
        val guiSize  = config.size
        val maxPages = config.pages

        var currentPage = page.coerceIn(1, maxPages)
        val initial = buildItemsForPage(player, currentPage)

        openGui(
            player,
            CustomColorParser.processAllFormats(config.title),
            initial,
            guiSize,
            getGuiScreenHandlerType(guiSize),
            object : GuiInteraction {

                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    when (slot) {
                        config.closeButton.slot -> {
                            cancelRefresh(player)
                            player.closeHandledScreen()
                            return
                        }
                        config.prevPageButton.slot -> {
                            if (currentPage > 1) {
                                currentPage -= 1
                                updateOpenGui(player, buildItemsForPage(player, currentPage), expectedSize = guiSize)
                            }
                            return
                        }
                        config.nextPageButton.slot -> {
                            if (currentPage < maxPages) {
                                currentPage += 1
                                updateOpenGui(player, buildItemsForPage(player, currentPage), expectedSize = guiSize)
                            }
                            return
                        }
                    }

                    val clickedKit = kitOnSlot(currentPage, slot) ?: return

                    if (rightClick) {
                        cancelRefresh(player)
                        KitPreviewGui.openKitPreview(player, clickedKit)
                        player.world.playSound(
                            null, player.blockPos,
                            Registries.SOUND_EVENT.get(Identifier.of("minecraft:block.chest.open")),
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f
                        )
                    } else {
                        KitManager.giveKit(player, clickedKit)

                        updateOpenGui(player, buildItemsForPage(player, currentPage), expectedSize = guiSize)

                        player.world.playSound(
                            null, player.blockPos,
                            Registries.SOUND_EVENT.get(Identifier.of("minecraft:entity.experience_orb.pickup")),
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f
                        )
                    }
                }

                override fun onGuiClose(player: ServerPlayerEntity) {
                    cancelRefresh(player)
                    player.world.playSound(
                        null, player.blockPos,
                        Registries.SOUND_EVENT.get(Identifier.of("minecraft:block.chest.close")),
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f
                    )
                }
            }
        )

        cancelRefresh(player)
        val timer = Timer(true)
        liveRefresh[player.uuid] = timer
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                player.server.execute {
                    val ok = updateOpenGui(
                        player,
                        buildItemsForPage(player, currentPage),
                        expectedSize = guiSize
                    )
                    if (!ok) {
                        cancelRefresh(player)
                    }
                }
            }
        }, 1000L, 1000L)
    }
}
