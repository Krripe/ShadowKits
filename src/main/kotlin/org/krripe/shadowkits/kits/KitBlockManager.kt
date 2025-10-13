package org.krripe.shadowkits.kits

import com.google.gson.reflect.TypeToken
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.krripe.shadowkits.config.BlockKitsConfig
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.gui.KitBlockPreviewGui
import org.krripe.shadowkits.utils.PermissionsSystem
import java.io.File
import java.nio.file.Paths

object KitBlockManager {

    private val permissions = PermissionsSystem()

    private val dataFolder = Paths.get("config", "shadowkits", "data").toFile()
    private val kitBlocksFile = File(dataFolder, "blockdata.json")

    private val kitBlocks = mutableMapOf<Pair<String, BlockPos>, String>()

    data class KitBlockLocation(
        val x: Int, val y: Int, val z: Int,
        val dimension: String,
        val kitName: String
    )

    fun loadKitBlocks() {
        kitBlocks.clear()
        dataFolder.mkdirs()
        if (!kitBlocksFile.exists()) return

        val type = object : TypeToken<List<KitBlockLocation>>() {}.type
        val list = runCatching {
            KitsConfig.gson.fromJson<List<KitBlockLocation>>(kitBlocksFile.readText(), type)
        }.getOrNull() ?: return

        list.forEach { loc ->
            val pos = BlockPos(loc.x, loc.y, loc.z)
            kitBlocks[Pair(loc.dimension, pos)] = loc.kitName.lowercase()
        }
    }

    private fun saveKitBlocks() {
        dataFolder.mkdirs()
        val list = kitBlocks.map { (key, kit) ->
            val (dimension, pos) = key
            KitBlockLocation(pos.x, pos.y, pos.z, dimension, kit)
        }
        kitBlocksFile.writeText(KitsConfig.gson.toJson(list))
    }

    fun setKitBlock(pos: BlockPos, kitName: String, world: World) {
        if (world.isClient) return
        val dim = (world as ServerWorld).registryKey.value.toString()
        kitBlocks[Pair(dim, pos)] = kitName.lowercase()
        saveKitBlocks()

        BlockKitsConfig.ensureKitTemplate(kitName)
    }

    fun unsetKitBlock(pos: BlockPos, world: World) {
        val dim = (world as ServerWorld).registryKey.value.toString()
        kitBlocks.remove(Pair(dim, pos))
        saveKitBlocks()
    }

    fun getKitAt(pos: BlockPos, world: World): String? {
        val dim = (world as ServerWorld).registryKey.value.toString()
        return kitBlocks[Pair(dim, pos)]
    }

    fun onBlockInteract(player: ServerPlayerEntity, pos: BlockPos, isRightClick: Boolean) {
        val kitName = getKitAt(pos, player.world) ?: return
        val kit = KitManager.getKit(kitName) ?: return

        kit.permission?.let { perm ->
            if (!permissions.hasPermission(player.commandSource, perm, 0)) {
                player.sendMessage(KitsConfig.getMessage("noPermission"), false)
                return
            }
        }

        val cfg = BlockKitsConfig.getFor(kit.name)
        when (BlockKitsConfig.actionForClick(cfg, isRightClick)) {
            BlockKitsConfig.ClickAction.PREVIEW -> {
                KitBlockPreviewGui.openOrUpdate(player, kit, cfg)
            }
            BlockKitsConfig.ClickAction.CLAIM -> {
                KitManager.giveKit(player, kit)
            }
            BlockKitsConfig.ClickAction.NONE -> {
            }
        }
    }
}