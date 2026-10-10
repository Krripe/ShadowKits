package org.krripe.shadowkits.gui.main

import net.minecraft.server.network.ServerPlayerEntity
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.gui.GuiButtonCfg
import org.krripe.shadowkits.gui.GuiInteraction
import org.krripe.shadowkits.gui.GuiItem
import org.krripe.shadowkits.gui.GuiMenuUtils
import org.krripe.shadowkits.gui.GuiPlaceholders
import org.krripe.shadowkits.gui.GuiSlotParser
import org.krripe.shadowkits.gui.privew.PrivewGUI
import org.krripe.shadowkits.gui.getGuiScreenHandlerType
import org.krripe.shadowkits.gui.openGui
import org.krripe.shadowkits.gui.updateOpenGui
import org.krripe.shadowkits.kits.KitManager
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.collections.plusAssign

object MainGUI {
    private val liveRefresh = mutableMapOf<UUID, Timer>()
    private val mainActive = mutableSetOf<UUID>()

    private fun setMainActive(player: ServerPlayerEntity, active: Boolean) {
        if (active) mainActive.add(player.uuid) else mainActive.remove(player.uuid)
    }

    private fun cancelRefresh(player: ServerPlayerEntity) {
        liveRefresh.remove(player.uuid)?.let {
            runCatching { it.cancel() }
            runCatching { it.purge() }
        }
    }

    fun shutdownAll() {
        liveRefresh.values.forEach {
            runCatching { it.cancel() }
            runCatching { it.purge() }
        }
        liveRefresh.clear()
        mainActive.clear()
    }

    fun shutdownPlayer(uuid: UUID) {
        liveRefresh.remove(uuid)?.let {
            runCatching { it.cancel() }
            runCatching { it.purge() }
        }
        mainActive.remove(uuid)
    }

    private fun maxPages(): Int = maxOf(1, KitsConfig.kits.values.maxOfOrNull { it.page } ?: 1)

    private fun kitsForPage(page: Int) = KitsConfig.kits.values.filter { it.page == page }

    private fun placeholders(page: Int, pages: Int): Map<String, String> = mapOf(
        "page" to page.toString(),
        "pages" to pages.toString(),
        "kit_count" to KitsConfig.kits.size.toString()
    )

    private fun isButtonEnabled(player: ServerPlayerEntity, button: GuiButtonCfg, page: Int, pages: Int): Boolean {
        if (!GuiMenuUtils.hasPermission(player, button.requiresPermission)) return false

        return when (button.id.lowercase()) {
            "previous_page" -> page > 1
            "next_page" -> page < pages
            else -> true
        }
    }

    private fun buildItemsForPage(player: ServerPlayerEntity, page: Int): List<GuiItem> {
        val cfg = MainGUIConfig.current()
        val frame = cfg.frame
        val size = GuiMenuUtils.normalizeSize(frame.size)
        val pages = maxPages()
        val placeholders = placeholders(page, pages)
        val items = mutableListOf<GuiItem>()

        items += GuiMenuUtils.fillerItems(player, frame.fillers, size, placeholders)

        val pageKits = kitsForPage(page)
        val contentSlots = GuiSlotParser.parse(cfg.contentSlots, size)

        if (contentSlots.isNotEmpty()) {
            pageKits.forEachIndexed { index, kit ->
                val slot = contentSlots.getOrNull(index) ?: return@forEachIndexed
                val display = KitManager.createItemStack(kit.displayItem, player, kit.name)
                items += GuiItem(display, slot, "kit:${kit.name.lowercase()}")
            }
        } else {
            pageKits.forEach { kit ->
                val slot = kit.slotOnPage.takeIf { it in 0 until size } ?: return@forEach
                val display = KitManager.createItemStack(kit.displayItem, player, kit.name)
                items += GuiItem(display, slot, "kit:${kit.name.lowercase()}")
            }
        }

        frame.buttons.forEach { button ->
            val enabled = isButtonEnabled(player, button, page, pages)
            if (enabled || button.disabledItem != null) {
                items += GuiMenuUtils.buttonItems(player, button, size, placeholders, enabled)
            }
        }

        return items
    }

    private fun kitOnSlot(page: Int, slot: Int): KitsConfig.KitConfig? {
        val cfg = MainGUIConfig.current()
        val size = GuiMenuUtils.normalizeSize(cfg.frame.size)
        val contentSlots = GuiSlotParser.parse(cfg.contentSlots, size)

        if (contentSlots.isNotEmpty()) {
            val index = contentSlots.indexOf(slot)
            return if (index < 0) null else kitsForPage(page).getOrNull(index)
        }

        return kitsForPage(page).firstOrNull { it.slotOnPage == slot }
    }

    fun open(player: ServerPlayerEntity, page: Int = 1) {
        val cfg = MainGUIConfig.current()
        val frame = cfg.frame
        val size = GuiMenuUtils.normalizeSize(frame.size)
        val pages = maxPages()
        var currentPage = page.coerceIn(1, pages)

        GuiMenuUtils.playOpen(player, frame.sounds)

        openGui(
            player = player,
            title = GuiMenuUtils.title(GuiPlaceholders.apply(frame.title, placeholders(currentPage, pages))),
            guiItems = buildItemsForPage(player, currentPage),
            inventorySize = size,
            screenHandlerType = getGuiScreenHandlerType(size),
            interaction = object : GuiInteraction {
                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    if (player.uuid !in mainActive) return

                    val button = GuiMenuUtils.buttonBySlot(frame.buttons, size, slot)

                    if (button != null) {
                        val enabled = isButtonEnabled(player, button, currentPage, pages)

                        if (!enabled) {
                            if (button.disabledItem != null) {
                                GuiMenuUtils.playButtonDeny(player, frame.sounds, button)
                            }
                            return
                        }

                        GuiMenuUtils.playButtonClick(player, frame.sounds, button)

                        when (button.id.lowercase()) {
                            "previous_page" -> {
                                currentPage = (currentPage - 1).coerceAtLeast(1)
                                refresh(player, currentPage)
                                return
                            }

                            "next_page" -> {
                                currentPage = (currentPage + 1).coerceAtMost(pages)
                                refresh(player, currentPage)
                                return
                            }
                        }

                        GuiMenuUtils.executeActions(
                            player = player,
                            actions = button.actions,
                            placeholders = placeholders(currentPage, pages)
                        )

                        if (button.closeOnClick) player.closeHandledScreen()
                        return
                    }

                    val kit = kitOnSlot(currentPage, slot) ?: return

                    if (rightClick) {
                        cancelRefresh(player)
                        setMainActive(player, false)
                        GuiMenuUtils.playClick(player, frame.sounds, cfg.previewSound)
                        PrivewGUI.open(player, kit)
                        return
                    }

                    KitManager.giveKit(player, kit)
                    GuiMenuUtils.playClick(player, frame.sounds, cfg.claimSound)
                    refresh(player, currentPage)
                }

                override fun onGuiClose(player: ServerPlayerEntity) {
                    cancelRefresh(player)
                    setMainActive(player, false)
                    GuiMenuUtils.playClose(player, frame.sounds)
                }
            }
        )

        setMainActive(player, true)
        startRefresh(player) { currentPage }
    }

    private fun refresh(player: ServerPlayerEntity, page: Int) {
        val size = GuiMenuUtils.normalizeSize(MainGUIConfig.current().frame.size)

        if (!updateOpenGui(player, buildItemsForPage(player, page), expectedSize = size)) {
            cancelRefresh(player)
        }
    }

    private fun startRefresh(player: ServerPlayerEntity, page: () -> Int) {
        cancelRefresh(player)

        val timer = Timer(true)
        liveRefresh[player.uuid] = timer

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                player.server.execute {
                    if (player.uuid !in mainActive) {
                        cancelRefresh(player)
                        return@execute
                    }

                    refresh(player, page())
                }
            }
        }, 1000L, 1000L)
    }
}