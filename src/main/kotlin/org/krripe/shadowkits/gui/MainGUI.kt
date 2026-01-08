package org.krripe.shadowkits.gui

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.*
import org.krripe.shadowkits.utils.SoundHelper
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

object MainGUI {

    private val liveRefresh = mutableMapOf<UUID, Timer>()

    @Volatile private var mainActive = mutableSetOf<UUID>()
    private fun setMainActive(player: ServerPlayerEntity, active: Boolean) {
        if (active) mainActive.add(player.uuid) else mainActive.remove(player.uuid)
    }

    private fun cancelRefresh(player: ServerPlayerEntity) {
        liveRefresh.remove(player.uuid)?.let { t ->
            runCatching { t.cancel() }; runCatching { t.purge() }
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
        val cfg   = MainGUIConfig.current()
        val size  = cfg.size
        val items = mutableListOf<GuiItem>()

        if (cfg.fillersEnabled) {
            cfg.fillers.forEach { f ->
                val slots = GuiUtils.parseSlots(f.slots, size)
                val stack = GuiUtils.makeFiller(f)
                slots.forEach { s -> if (s in 0 until size) items += GuiItem(stack.copy(), s) }
            }
        }

        val pageKits = kitsForPage(page)
        val contentSlots = GuiUtils.parseSlots(cfg.contentSlots, size)
        if (contentSlots.isNotEmpty()) {
            var i = 0
            pageKits.forEach { kit ->
                if (i >= contentSlots.size) return@forEach
                val display = KitManager.createItemStack(kit.displayItem, player, kit.name)
                items += GuiItem(display, contentSlots[i++])
            }
        } else {
            pageKits.forEach { kit ->
                val slot = kit.slotOnPage.takeIf { it in 0 until size } ?: return@forEach
                val display = KitManager.createItemStack(kit.displayItem, player, kit.name)
                items += GuiItem(display, slot)
            }
        }

        val mp = maxOf(1, KitsConfig.kits.values.maxOfOrNull { it.page } ?: 1)

        if (mp > 1) {
            cfg.prevPageButton?.takeIf { page > 1 }?.let { b ->
                items += GuiItem(GuiUtils.makeButton(b), b.slot)
            }
            cfg.nextPageButton?.takeIf { page < mp }?.let { b ->
                items += GuiItem(GuiUtils.makeButton(b), b.slot)
            }
        }
        cfg.closeButton?.let { b ->
            items += GuiItem(GuiUtils.makeButton(b), b.slot)
        }

        return items
    }

    private fun kitOnSlot(page: Int, slot: Int, cfg: PagedWithContent): KitsConfig.KitConfig? {
        val size = cfg.size
        val contentSlots = GuiUtils.parseSlots(cfg.contentSlots, size)
        return if (contentSlots.isNotEmpty()) {
            val idx = contentSlots.indexOf(slot)
            if (idx < 0) null else kitsForPage(page).getOrNull(idx)
        } else {
            kitsForPage(page).firstOrNull { it.slotOnPage == slot }
        }
    }

    fun open(player: ServerPlayerEntity, page: Int = 1) {
        val cfg      = MainGUIConfig.current()
        val guiSize  = cfg.size
        val maxPages = maxOf(1, KitsConfig.kits.values.maxOfOrNull { it.page } ?: 1)

        var currentPage = page.coerceIn(1, maxPages)
        val initial = buildItemsForPage(player, currentPage)

        openGui(
            player,
            CustomColorParser.processAllFormats(cfg.title).setStyle(Style.EMPTY.withItalic(false)),
            initial,
            guiSize,
            getGuiScreenHandlerType(guiSize),
            object : GuiInteraction {

                override fun onItemClick(slot: Int, player: ServerPlayerEntity, rightClick: Boolean) {
                    if (player.uuid !in mainActive) return

                    when (slot) {
                        cfg.closeButton?.slot -> {
                            cancelRefresh(player)
                            setMainActive(player, false)
                            player.closeHandledScreen()
                            return
                        }
                        cfg.prevPageButton?.slot -> {
                            if (currentPage > 1) {
                                currentPage -= 1
                                updateOpenGui(player, buildItemsForPage(player, currentPage), expectedSize = guiSize)
                            }
                            return
                        }
                        cfg.nextPageButton?.slot -> {
                            if (currentPage < maxPages) {
                                currentPage += 1
                                updateOpenGui(player, buildItemsForPage(player, currentPage), expectedSize = guiSize)
                            }
                            return
                        }
                    }

                    val clickedKit = kitOnSlot(currentPage, slot, cfg) ?: return

                    if (rightClick) {
                        cancelRefresh(player)
                        setMainActive(player, false)
                        PrivewGUI.open(player, clickedKit)
                        SoundHelper.play(player, cfg.sounds.preview.id, cfg.sounds.preview.volume, cfg.sounds.preview.pitch)
                    } else {
                        KitManager.giveKit(player, clickedKit)
                        updateOpenGui(player, buildItemsForPage(player, currentPage), expectedSize = guiSize)
                        SoundHelper.play(player, cfg.sounds.claim.id, cfg.sounds.claim.volume, cfg.sounds.claim.pitch)
                    }
                }

                override fun onGuiClose(player: ServerPlayerEntity) {
                    cancelRefresh(player)
                    setMainActive(player, false)
                    SoundHelper.play(player, cfg.sounds.close.id, cfg.sounds.close.volume, cfg.sounds.close.pitch)
                }
            }
        )

        setMainActive(player, true)
        SoundHelper.play(player, cfg.sounds.open.id, cfg.sounds.open.volume, cfg.sounds.open.pitch)

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
                    if (!ok) cancelRefresh(player)
                }
            }
        }, 1000L, 1000L)
    }
}
