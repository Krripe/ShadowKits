package org.krripe.shadowkits

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import org.krripe.shadowkits.commands.KitCommand
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.database.KitsDatabaseManager
import org.krripe.shadowkits.kits.KitBlockManager
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.krripe.shadowkits.gui.block.BlockPrivewGUIConfig
import org.krripe.shadowkits.gui.main.MainGUI
import org.krripe.shadowkits.gui.main.MainGUIConfig
import org.krripe.shadowkits.gui.privew.PrivewGUIConfig
import org.krripe.shadowkits.kits.KitManager

class Shadowkits : ModInitializer {
    override fun onInitialize() {
        println("[ShadowKits] initializing")

        KitsConfig.loadConfig()
        KitsDatabaseManager.initialize()
        KitCommand.register()
        KitBlockManager.loadKitBlocks()

        ServerPlayConnectionEvents.JOIN.register(ServerPlayConnectionEvents.Join { handler, _, _ ->
            val player = handler.player
            player.server.execute {
                val givenOnce = KitManager.giveFirstJoinKits(player)
                KitManager.giveOnJoinKits(player, skip = givenOnce)
            }
        })


        ServerLifecycleEvents.SERVER_STARTED.register {
            MainGUIConfig.reload()
            PrivewGUIConfig.resetCacheTemplate()
            BlockPrivewGUIConfig.reload()
            KitBlockManager.loadKitBlocks()
        }

        ServerPlayConnectionEvents.DISCONNECT.register(ServerPlayConnectionEvents.Disconnect { handler, _ ->
            val player = handler.player
            player.server.execute {
                MainGUI.shutdownPlayer(player.uuid)
            }
        })

        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hit ->
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return@UseBlockCallback ActionResult.PASS
            if (world.isClient || hit.type != net.minecraft.util.hit.HitResult.Type.BLOCK) return@UseBlockCallback ActionResult.PASS

            val pos = hit.blockPos
            if (KitBlockManager.getKitAt(pos, world) == null) return@UseBlockCallback ActionResult.PASS

            KitBlockManager.onBlockInteract(player as ServerPlayerEntity, pos, isRightClick = true)
            ActionResult.SUCCESS
        })

        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, world, _, pos, _ ->
            if (world.isClient) return@AttackBlockCallback ActionResult.PASS
            if (KitBlockManager.getKitAt(pos, world) == null) return@AttackBlockCallback ActionResult.PASS

            KitBlockManager.onBlockInteract(player as ServerPlayerEntity, pos, isRightClick = false)
            ActionResult.SUCCESS
        })

        ServerLifecycleEvents.SERVER_STOPPING.register {
            KitsDatabaseManager.shutdown()
            MainGUI.shutdownAll()
        }

        println("[ShadowKits] initialized Successful")
    }
}