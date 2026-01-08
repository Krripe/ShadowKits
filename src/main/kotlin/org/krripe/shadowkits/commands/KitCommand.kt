package org.krripe.shadowkits.commands

import com.mojang.brigadier.suggestion.SuggestionProvider
import java.util.concurrent.CompletableFuture
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.database.KitsDatabaseManager
import org.krripe.shadowkits.gui.BlockPrivewGUIConfig
import org.krripe.shadowkits.gui.MainGUI
import org.krripe.shadowkits.gui.MainGUIConfig
import org.krripe.shadowkits.gui.PrivewGUI
import org.krripe.shadowkits.gui.PrivewGUIConfig
import org.krripe.shadowkits.kits.KitBlockManager
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.PermissionsSystem
import org.krripe.shadowkits.utils.SoundHelper
import java.io.File

object KitCommand {

    private val permissionsSystem = PermissionsSystem()

    private val kitSuggestionProvider = SuggestionProvider<ServerCommandSource> { _, builder ->
        CompletableFuture.supplyAsync {
            KitsConfig.kits.keys.forEach { builder.suggest(it) }
            builder.build()
        }
    }

    private val playerSuggestionProvider = SuggestionProvider<ServerCommandSource> { context, builder ->
        CompletableFuture.supplyAsync {
            context.source.server.playerManager.playerList.forEach { player -> builder.suggest(player.name.string) }
            builder.build()
        }
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("create")
                            .requires { permissionsSystem.hasPermission(it, "kit.create", 4) }
                            .then(
                                CommandManager.argument("kitName", StringArgumentType.string())
                                    .suggests(kitSuggestionProvider)
                                    .executes { ctx ->
                                        val player = ctx.source.player ?: return@executes 0
                                        val kitName = StringArgumentType.getString(ctx, "kitName")
                                        createKit(player, kitName)
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("claim")
                            .requires { permissionsSystem.hasPermission(it, "kit.claim", 0) }
                            .then(
                                CommandManager.argument("kitName", StringArgumentType.string())
                                    .suggests(kitSuggestionProvider)
                                    .executes { ctx ->
                                        val player = ctx.source.player ?: return@executes 0
                                        val kitName = StringArgumentType.getString(ctx, "kitName")
                                        claimKit(player, kitName)
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("give")
                            .requires { permissionsSystem.hasPermission(it, "kit.give", 4) }
                            .then(
                                CommandManager.argument("kitName", StringArgumentType.string())
                                    .suggests(kitSuggestionProvider)
                                    .then(
                                        CommandManager.argument("playerName", StringArgumentType.string())
                                            .suggests(playerSuggestionProvider)
                                            .executes { ctx ->
                                                val sender = ctx.source
                                                val kitName = StringArgumentType.getString(ctx, "kitName")
                                                val playerName = StringArgumentType.getString(ctx, "playerName")
                                                val target = sender.server.playerManager.getPlayer(playerName)

                                                if (target == null) {
                                                    sender.sendError(KitsConfig.getMessage("playerNotFound"))
                                                    return@executes 0
                                                }

                                                giveKitToPlayer(sender, kitName, target)
                                                Command.SINGLE_SUCCESS
                                            }
                                    )
                            )
                    )
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("remove")
                            .requires { permissionsSystem.hasPermission(it, "kit.remove", 4) }
                            .then(
                                CommandManager.argument("kitName", StringArgumentType.string())
                                    .suggests(kitSuggestionProvider)
                                    .executes { ctx ->
                                        val sender = ctx.source
                                        val kitName = StringArgumentType.getString(ctx, "kitName")
                                        removeKit(sender, kitName)
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("reload")
                            .requires { permissionsSystem.hasPermission(it, "kit.reload", 4) }
                            .executes { ctx ->
                                val sender = ctx.source

                                KitsConfig.loadConfig()
                                KitsDatabaseManager.shutdown()
                                KitsDatabaseManager.initialize()
                                MainGUI.shutdownAll()
                                MainGUIConfig.reload()
                                PrivewGUIConfig.resetCacheTemplate()
                                BlockPrivewGUIConfig.reload()
                                KitBlockManager.loadKitBlocks()

                                sender.sendFeedback(
                                    { KitsConfig.getMessage("kitsReloaded") },
                                    false
                                )
                                Command.SINGLE_SUCCESS
                            }
                    )
            )

            dispatcher.register(
                CommandManager.literal("kits")
                    .requires { permissionsSystem.hasPermission(it, "kits.view", 0) }
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        MainGUI.open(player)
                        Command.SINGLE_SUCCESS
                    }
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("preview")
                            .requires { permissionsSystem.hasPermission(it, "kit.preview", 0) }
                            .then(
                                CommandManager.argument("kitName", StringArgumentType.string())
                                    .suggests(kitSuggestionProvider)
                                    .executes { ctx ->
                                        val player = ctx.source.player ?: return@executes 0
                                        val kitName = StringArgumentType.getString(ctx, "kitName")
                                        val kit = KitManager.getKit(kitName)

                                        if (kit == null) {
                                            player.sendMessage(KitsConfig.getMessage("kitNotFound", "kit" to kitName), false)
                                            return@executes 0
                                        }

                                        PrivewGUI.open(player, kit)
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("reset")
                            .requires { permissionsSystem.hasPermission(it, "kit.reset", 4) }
                            .then(
                                CommandManager.argument("playerName", StringArgumentType.string())
                                    .suggests(playerSuggestionProvider)
                                    .then(
                                        CommandManager.argument("kitName", StringArgumentType.string())
                                            .suggests(kitSuggestionProvider)
                                            .executes { ctx ->
                                                val sender = ctx.source
                                                val playerName = StringArgumentType.getString(ctx, "playerName")
                                                val kitName = StringArgumentType.getString(ctx, "kitName")
                                                val target = sender.server.playerManager.getPlayer(playerName)

                                                if (target == null) {
                                                    sender.sendError(KitsConfig.getMessage("playerNotFound"))
                                                    return@executes 0
                                                }

                                                if (!KitManager.resetKitCooldown(target, kitName)) {
                                                    sender.sendError(KitsConfig.getMessage("kitNotFound", "kit" to kitName))
                                                    return@executes 0
                                                }

                                                sender.sendFeedback(
                                                    { KitsConfig.getMessage("kitCooldownReset", "kit" to kitName, "player" to playerName) },
                                                    false
                                                )

                                                Command.SINGLE_SUCCESS
                                            }
                                    )
                            )
                    )
            )

            dispatcher.register(
                CommandManager.literal("kit")
                    .then(
                        CommandManager.literal("block")
                            .then(
                                CommandManager.literal("set")
                                    .requires { permissionsSystem.hasPermission(it, "kit.block.set", 4) }
                                    .then(
                                        CommandManager.argument("kitName", StringArgumentType.string())
                                            .suggests(kitSuggestionProvider)
                                            .executes { ctx ->
                                                val player = ctx.source.player ?: return@executes 0
                                                val kitName = StringArgumentType.getString(ctx, "kitName")
                                                val kit = KitManager.getKit(kitName)
                                                if (kit == null) {
                                                    player.sendMessage(KitsConfig.getMessage("kitNotFound", "kit" to kitName), false)
                                                    return@executes 0
                                                }

                                                val hit = player.raycast(6.0, 0f, false)
                                                if (hit.type != HitResult.Type.BLOCK) {
                                                    player.sendMessage(KitsConfig.getMessage("kitBlockLookAtBlock"), false)
                                                    return@executes 0
                                                }

                                                val pos = (hit as BlockHitResult).blockPos
                                                KitBlockManager.setKitBlock(pos, kit.name, player.world)

                                                player.sendMessage(
                                                    KitsConfig.getMessage(
                                                        "kitBlockSet",
                                                        "kit" to kit.name,
                                                        "x" to pos.x.toString(),
                                                        "y" to pos.y.toString(),
                                                        "z" to pos.z.toString()
                                                    ),
                                                    false
                                                )
                                                Command.SINGLE_SUCCESS
                                            }
                                    )
                            )
                            .then(
                                CommandManager.literal("unset")
                                    .requires { permissionsSystem.hasPermission(it, "kit.block.unset", 4) }
                                    .executes { ctx ->
                                        val player = ctx.source.player ?: return@executes 0
                                        val hit = player.raycast(6.0, 0f, false)
                                        if (hit.type != HitResult.Type.BLOCK) {
                                            player.sendMessage(KitsConfig.getMessage("kitBlockLookAtBlock"), false)
                                            return@executes 0
                                        }

                                        val pos = (hit as BlockHitResult).blockPos
                                        KitBlockManager.unsetKitBlock(pos, player.world)

                                        player.sendMessage(
                                            KitsConfig.getMessage(
                                                "kitBlockUnset",
                                                "x" to pos.x.toString(),
                                                "y" to pos.y.toString(),
                                                "z" to pos.z.toString()
                                            ),
                                            false
                                        )
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
        }
    }

    private fun createKit(player: ServerPlayerEntity, name: String) {
        val kit = KitsConfig.KitConfig(
            name = name,
            onJoin = false,
            cooldown = "5m",
            claims = true,
            maxClaims = 99,
            permission = "kit.create",
            economyProvider = "auto",
            currency = "money",
            costMoney = false,
            cost = 0,
            claimSound = "minecraft:entity.player.levelup",
            cooldownSound = "minecraft:entity.villager.no",
            page = 1,
            slotOnPage = 20,
            displayItem = KitsConfig.ItemConfig("minecraft:paper"),
            rewards = emptyList(),
            items = emptyList(),
            commands = emptyList()
        )

        KitsConfig.kits[name.lowercase()] = kit
        KitsConfig.saveConfig()
        player.sendMessage(KitsConfig.getMessage("kitCreated", "kit" to name), false)
    }

    private fun claimKit(player: ServerPlayerEntity, name: String) {
        val kit = KitManager.getKit(name)
        if (kit == null) {
            player.sendMessage(KitsConfig.getMessage("kitNotFound", "kit" to name), false)
            return
        }
        KitManager.giveKit(player, kit)
    }

    private fun giveKitToPlayer(sender: ServerCommandSource, name: String, target: ServerPlayerEntity) {
        val kit = KitManager.getKit(name)
        if (kit == null) {
            sender.sendError(KitsConfig.getMessage("kitNotFound", "kit" to name))
            return
        }

        val ok = KitManager.givePlayerKit(target, kit)
        if (!ok) {
            sender.sendError(KitsConfig.getMessage("inventoryFull"))
            return
        }

        target.sendMessage(KitsConfig.getMessage("kitClaimed", "kit" to kit.name), false)

        SoundHelper.play(target, kit.claimSound)

        sender.sendFeedback(
            { KitsConfig.getMessage("kitGiven", "kit" to kit.name, "player" to target.name.string) },
            false
        )
    }

    private fun removeKit(sender: ServerCommandSource, name: String) {
        val kitFile = File(KitsConfig.kitsFolder, "$name.json")

        if (!KitsConfig.kits.containsKey(name.lowercase())) {
            sender.sendError(KitsConfig.getMessage("kitNotFound", "kit" to name))
            return
        }

        KitsConfig.kits.remove(name.lowercase())
        KitsConfig.saveConfig()

        if (kitFile.exists()) {
            kitFile.delete()
        }

        sender.sendFeedback({ KitsConfig.getMessage("kitRemoved", "kit" to name) }, false)
    }

}