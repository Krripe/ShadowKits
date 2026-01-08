package org.krripe.shadowkits.kits

import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.component.type.AttributeModifierSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryKey
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.*
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.RawFilteredPair
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.ItemStackArgumentType
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.server.MinecraftServer
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.config.KitsConfig.KitConfig
import org.krripe.shadowkits.database.KitsDatabaseAdapter
import org.krripe.shadowkits.database.KitsDatabaseManager
import org.krripe.shadowkits.economy.EconomyRouter
import org.krripe.shadowkits.utils.CustomColorParser
import org.krripe.shadowkits.utils.PermissionsSystem
import org.krripe.shadowkits.config.KitsConfig.Reward
import org.krripe.shadowkits.config.KitsConfig.PrizePackEntry
import org.krripe.shadowkits.config.KitsConfig.WeightedRewardEntry
import org.krripe.shadowkits.utils.SoundHelper
import java.io.File
import java.math.BigDecimal
import java.nio.file.Paths
import java.time.Instant
import java.util.*

object KitManager {
    private val dataFolder = Paths.get("config", "shadowkits", "data", "player_data").toFile()
    private val playerCooldowns = mutableMapOf<UUID, MutableMap<String, Long>>()
    private val playerClaims = mutableMapOf<UUID, MutableMap<String, Int>>()
    private val firstJoinDonePlayers = mutableSetOf<UUID>()
    private val permissionsSystem = PermissionsSystem()
    private val db by lazy { KitsDatabaseManager.getAdapter() }

    init {
        loadAllPlayerData()
    }

    private fun loadAllPlayerData() {
        if (KitsDatabaseManager.isUsingDatabase()) {
            db?.allCooldownRows()?.forEach { (u, kit, exp) ->
                playerCooldowns.computeIfAbsent(u) { mutableMapOf() }[kit] = exp
            }
            db?.allClaimRows()?.forEach { (u, kit, c) ->
                playerClaims.computeIfAbsent(u) { mutableMapOf() }[kit] = c
            }
            db?.allFirstJoinRows()?.forEach { (u, done) ->
                if (done) firstJoinDonePlayers.add(u)
            }
            return
        }

        dataFolder.mkdirs()
        dataFolder.listFiles { it.extension == "json" }?.forEach { file ->
            val uuid = UUID.fromString(file.nameWithoutExtension)
            val data = KitsConfig.gson.fromJson(file.readText(), PlayerData::class.java)
            playerCooldowns[uuid] = data.cooldowns.toMutableMap()
            playerClaims[uuid]   = data.claims.toMutableMap()
            if (data.firstJoinDone) firstJoinDonePlayers.add(uuid)
        }
    }

    private fun savePlayerData(player: UUID) {
        if (KitsDatabaseManager.isUsingDatabase()) {
            playerCooldowns[player]?.forEach { (kit, exp) -> db?.saveCooldown(player, kit, exp) }
            playerClaims[player]?.forEach { (kit, c) -> db?.saveClaims(player, kit, c) }
            db?.setFirstJoinDone(player, firstJoinDonePlayers.contains(player))
            return
        }

        val file = File(dataFolder, "$player.json")
        val data = PlayerData(
            cooldowns = playerCooldowns[player] ?: mutableMapOf(),
            claims    = playerClaims[player] ?: mutableMapOf(),
            firstJoinDone = firstJoinDonePlayers.contains(player)
        )
        file.parentFile.mkdirs()
        file.writeText(KitsConfig.gson.toJson(data))
    }

    fun effectiveRewards(kit: KitConfig): List<Reward> {
        return kit.rewards ?: KitsConfig.migrateLegacy(kit).rewards.orEmpty()
    }

    fun givePlayerKit(target: ServerPlayerEntity, kit: KitConfig): Boolean {
        val rewards = kit.rewards ?: KitsConfig.migrateLegacy(kit).rewards.orEmpty()

        val needSlots = requiredSlotsTotal(rewards)
        val freeSlots = target.inventory.main.count { it.isEmpty }
        if (freeSlots < needSlots) return false

        rewards.forEach { giveReward(target, it) }
        return true
    }

    data class PlayerData(
        val cooldowns: Map<String, Long> = mutableMapOf(),
        val claims: Map<String, Int> = mutableMapOf(),
        val firstJoinDone: Boolean = false
    )

    fun getKit(name: String): KitConfig? {
        return KitsConfig.kits[name.lowercase()]
    }

    private fun canClaimKit(player: ServerPlayerEntity, kit: KitConfig): Boolean {
        val uuid = player.uuid

        if (kit.permission != null) {
            val hasPermission = permissionsSystem.hasPermission(player.commandSource, kit.permission, 4)

            if (!hasPermission) {
                player.sendMessage(KitsConfig.getMessage("noPermission"), false)
                return false
            }
        }

        val lastUsed = playerCooldowns[uuid]?.get(kit.name.lowercase()) ?: 0L
        if (Instant.now().epochSecond < lastUsed) {
            player.sendMessage(KitsConfig.getMessage("inCooldown", "kit" to kit.name), false)
            return false
        }

        if (kit.claims) {
            val claims = playerClaims[uuid]?.get(kit.name.lowercase()) ?: 0
            if (claims >= kit.maxClaims) {
                player.sendMessage(KitsConfig.getMessage("kitMaxClaimsReached", "kit" to kit.name, "max" to kit.maxClaims.toString()), false)
                return false
            }
        }

        return true
    }

    fun giveKit(player: ServerPlayerEntity, kit: KitConfig) {
        if (!canClaimKit(player, kit)) return

        val uuid = player.uuid
        val cooldownSeconds = parseCooldown(kit.cooldown)
        val kitKey = kit.name.lowercase()
        val rewards = effectiveRewards(kit)
        val needSlots = requiredSlotsTotal(rewards)
        val freeSlots = player.inventory.main.count { it.isEmpty }
        if (freeSlots < needSlots) {
            player.sendMessage(KitsConfig.getMessage("inventoryFull"), false)
            return
        }

        val provider = kit.economyProvider ?: KitsConfig.config.economy.defaultProvider
        val currency = kit.currency ?: KitsConfig.config.economy.defaultCurrency
        val amount   = BigDecimal(kit.cost)
        fun costLabel() = if (currency.isBlank()) amount.toPlainString() else "${amount.toPlainString()} $currency"

        if (KitsDatabaseManager.isUsingDatabase()) {
            when (val decision = db?.tryConsumeKit(uuid, kitKey, cooldownSeconds, kit.claims, kit.maxClaims)) {
                is KitsDatabaseAdapter.ClaimDecision.Allowed -> {
                    if (kit.costMoney) {
                        if (!EconomyRouter.isAvailable() || !EconomyRouter.charge(uuid, amount, currency, provider)) {
                            db?.clearCooldown(uuid, kitKey)
                            if (kit.claims) {
                                val cur = db?.getClaims(uuid, kitKey) ?: 0
                                val newVal = (cur - 1).coerceAtLeast(0)
                                if (newVal <= 0) db?.clearClaims(uuid, kitKey) else db?.saveClaims(uuid, kitKey, newVal)
                            }
                            player.sendMessage(KitsConfig.getMessage("notEnoughMoney", "cost" to costLabel()), false)
                            return
                        }
                        player.sendMessage(KitsConfig.getMessage("kitPurchased", "cost" to costLabel()), false)
                    }

                    playerCooldowns.computeIfAbsent(uuid){ mutableMapOf() }[kitKey] = decision.newExpiresAt
                    if (kit.claims) {
                        playerClaims.computeIfAbsent(uuid){ mutableMapOf() }.merge(kitKey, 1) { a, _ -> a + 1 }
                    }
                }
                is KitsDatabaseAdapter.ClaimDecision.Denied -> {
                    val msg = when (decision.reason) {
                        KitsDatabaseAdapter.ClaimDecision.Reason.COOLDOWN ->
                            KitsConfig.getMessage("inCooldown", "kit" to kit.name)
                        KitsDatabaseAdapter.ClaimDecision.Reason.MAX_CLAIMS ->
                            KitsConfig.getMessage("kitMaxClaimsReached", "kit" to kit.name, "max" to kit.maxClaims.toString())
                        else ->
                            KitsConfig.getMessage("inCooldown", "kit" to kit.name)
                    }
                    player.sendMessage(msg, false)
                    return
                }
                else -> return
            }
        } else {
            if (kit.costMoney) {
                if (!EconomyRouter.isAvailable() || !EconomyRouter.charge(uuid, amount, currency, provider)) {
                    player.sendMessage(KitsConfig.getMessage("notEnoughMoney", "cost" to costLabel()), false)
                    return
                }
                player.sendMessage(KitsConfig.getMessage("kitPurchased", "cost" to costLabel()), false)
            }

            playerCooldowns.computeIfAbsent(uuid){ mutableMapOf() }[kitKey] =
                Instant.now().epochSecond + cooldownSeconds
            if (kit.claims) {
                playerClaims.computeIfAbsent(uuid){ mutableMapOf() }.merge(kitKey, 1) { a, _ -> a + 1 }
            }
            savePlayerData(uuid)
        }

        rewards.forEach { giveReward(player, it) }

        player.sendMessage(KitsConfig.getMessage("kitClaimed", "kit" to kit.name), false)

        kit.cooldownSound?.let { id -> SoundHelper.play(player, id) }
        kit.claimSound?.let    { id -> SoundHelper.play(player, id) }
    }

    private fun tryParseItemString(
        input: String,
        amount: Int,
        server: MinecraftServer?
    ): ItemStack? {
        if (server == null) return null
        val registryAccess: CommandRegistryAccess =
            CommandRegistryAccess.of(server.registryManager, FeatureFlags.VANILLA_FEATURES)

        val reader = StringReader(input)
        val argType = ItemStackArgumentType.itemStack(registryAccess)

        return try {
            val parsed = argType.parse(reader)
            if (reader.canRead()) null
            else parsed.createStack(amount, false)
        } catch (_: CommandSyntaxException) {
            null
        }
    }

    fun createItemStack(config: KitsConfig.ItemConfig, player: ServerPlayerEntity?, kitName: String?): ItemStack {

        val base: ItemStack? =
            if ('{' in config.material)
                tryParseItemString(
                    input  = config.material,
                    amount = config.amount,
                    server = player?.server
                )
            else null

        val stack = base ?: run {
            val item = Registries.ITEM.get(Identifier.of(config.material))
            ItemStack(item, config.amount)
        }

        config.customModelData?.let {
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(it))
        }

        if (config.unbreakable) {
            stack.set(DataComponentTypes.UNBREAKABLE, UnbreakableComponent(true))
        }

        if (config.damage != null) {
            stack.set(DataComponentTypes.DAMAGE, config.damage)
        }

        val loreList = mutableListOf<String>()
        config.lore?.let(loreList::addAll)
        if (config.cooldownLore && player != null && kitName != null) {
            val cd = getCooldownTimeLeft(player, kitName)
            val line = KitsConfig.config.messages.cooldownLoreFormat
                .replace("%time%", cd)
            loreList.add(line)
        }

        config.displayname?.let {
            stack.set(
                DataComponentTypes.CUSTOM_NAME,
                CustomColorParser.processAllFormats(it).setStyle(Style.EMPTY.withItalic(false))
            )
        }
        stack.set(
            DataComponentTypes.LORE,
            LoreComponent(loreList.map {
                CustomColorParser.processAllFormats(it).setStyle(Style.EMPTY.withItalic(false))
            })
        )

        val enchantmentsBuilder = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)

        config.enchants?.forEach { enchantConfig ->
            val enchantmentRegistry = player?.server?.registryManager?.get(RegistryKeys.ENCHANTMENT)
            val enchantmentKey: RegistryKey<Enchantment> = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(enchantConfig.name))

            enchantmentRegistry?.getEntry(enchantmentKey)?.ifPresent { enchantmentEntry ->
                enchantmentsBuilder.add(enchantmentEntry, enchantConfig.level)
            }
        }

        stack.set(DataComponentTypes.ENCHANTMENTS, enchantmentsBuilder.build())

        if (config.enchantEffect) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

        if (!config.attributes.isNullOrEmpty()) {
            val builder = AttributeModifiersComponent.builder()

            config.attributes.forEach { attributeConfig ->
                val attributeKey = RegistryKey.of(RegistryKeys.ATTRIBUTE, Identifier.of(attributeConfig.name))
                val attributeEntry = player?.server?.registryManager?.get(RegistryKeys.ATTRIBUTE)?.getEntry(attributeKey)?.orElse(null)

                if (attributeEntry != null) {
                    val operation = when (attributeConfig.operation.lowercase()) {
                        "add" -> EntityAttributeModifier.Operation.ADD_VALUE
                        "multiply_base" -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                        "multiply_total" -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                        else -> EntityAttributeModifier.Operation.ADD_VALUE
                    }

                    val slot = when (attributeConfig.slot.lowercase()) {
                        "mainhand" -> AttributeModifierSlot.MAINHAND
                        "offhand" -> AttributeModifierSlot.OFFHAND
                        "head" -> AttributeModifierSlot.HEAD
                        "chest" -> AttributeModifierSlot.CHEST
                        "legs" -> AttributeModifierSlot.LEGS
                        "feet" -> AttributeModifierSlot.FEET
                        "any" -> AttributeModifierSlot.ANY
                        else -> AttributeModifierSlot.ANY
                    }

                    builder.add(
                        attributeEntry,
                        EntityAttributeModifier(Identifier.of(attributeConfig.name), attributeConfig.amount, operation),
                        slot
                    )
                }
                stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build())
            }
            return stack
        }

        if (config.material == "minecraft:written_book") {
            config.book?.let { book ->
                val formattedPages = book.pages.map { pageConfig ->
                    val pageText = pageConfig.page.joinToString("\n")
                    RawFilteredPair.of(CustomColorParser.processAllFormats(pageText) as Text)
                }

                val bookComponent = WrittenBookContentComponent(
                    RawFilteredPair.of(CustomColorParser.processAllFormats(book.title ?: "Untitled").string),
                    book.author ?: "Unknown",
                    book.generation,
                    formattedPages,
                    true
                )
                stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookComponent)
            }
        } else if (config.material == "minecraft:writable_book") {
            config.book?.let { book ->
                val formattedPages = book.pages.map { pageConfig ->
                    val pageText = pageConfig.page.joinToString("\n")
                    RawFilteredPair.of(CustomColorParser.processAllFormats(pageText).string)
                }
                val bookComponent = WritableBookContentComponent(formattedPages)
                stack.set(DataComponentTypes.WRITABLE_BOOK_CONTENT, bookComponent)
            }
        }

        return stack
    }

    private fun isFirstJoinDone(uuid: UUID): Boolean {
        if (firstJoinDonePlayers.contains(uuid)) return true

        if (KitsDatabaseManager.isUsingDatabase()) {
            val done = db?.isFirstJoinDone(uuid) ?: false
            if (done) firstJoinDonePlayers.add(uuid)
            return done
        }

        val file = File(dataFolder, "$uuid.json")
        if (!file.exists()) return false
        return try {
            val data = KitsConfig.gson.fromJson(file.readText(), PlayerData::class.java)
            if (data.firstJoinDone) {
                firstJoinDonePlayers.add(uuid)
                true
            } else false
        } catch (_: Exception) { false }
    }


    private fun markFirstJoinDone(uuid: UUID) {
        firstJoinDonePlayers.add(uuid)
        if (KitsDatabaseManager.isUsingDatabase()) {
            db?.setFirstJoinDone(uuid, true)
        }
        savePlayerData(uuid)
    }


    fun giveFirstJoinKits(player: ServerPlayerEntity): Set<String> {
        val uuid = player.uuid
        if (isFirstJoinDone(uuid)) return emptySet()

        val given = mutableSetOf<String>()
        KitsConfig.kits.values
            .filter { it.firstTimeJoin }
            .forEach { kit ->
                given += kit.name.lowercase()
                giveKit(player, kit)
            }
        markFirstJoinDone(uuid)
        return given
    }

    fun giveOnJoinKits(player: ServerPlayerEntity, skip: Set<String> = emptySet()) {
        KitsConfig.kits.values
            .filter { it.onJoin && it.name.lowercase() !in skip }
            .forEach { kit ->
                val uuid = player.uuid
                if (kit.claims) {
                    val claims = playerClaims[uuid]?.get(kit.name.lowercase()) ?: 0
                    if (claims >= kit.maxClaims) return@forEach
                }
                giveKit(player, kit)
            }
    }

    private fun parseCooldown(cooldown: String): Long {
        val regex = "(\\d+)([dhms])".toRegex()
        val matches = regex.findAll(cooldown)

        return matches.sumOf { match ->
            val value = match.groupValues[1].toLong()
            when (match.groupValues[2]) {
                "d" -> value * 86400
                "h" -> value * 3600
                "m" -> value * 60
                "s" -> value
                else -> 0
            }
        }
    }

    private fun getCooldownTimeLeft(player: ServerPlayerEntity, kitName: String): String {
        val uuid = player.uuid

        val expiresAt: Long = if (KitsDatabaseManager.isUsingDatabase()) {
            (db?.getCooldown(uuid, kitName.lowercase()) ?: 0L)
                .takeIf { it > 0L }
                ?: (playerCooldowns[uuid]?.get(kitName.lowercase()) ?: 0L)
        } else {
            playerCooldowns[uuid]?.get(kitName.lowercase()) ?: 0L
        }

        val remaining = expiresAt - Instant.now().epochSecond
        if (remaining <= 0) return KitsConfig.config.messages.cooldownReadyText

        val hours   = remaining / 3600
        val minutes = (remaining % 3600) / 60
        val seconds = remaining % 60

        return buildString {
            if (hours > 0) append("$hours h ")
            if (minutes > 0) append("$minutes m ")
            append("$seconds s")
        }.trim()
    }

    fun updateCooldownsAfterReload() {
        playerCooldowns.forEach { (uuid, cooldowns) ->
            cooldowns.keys.forEach { kitName ->
                val kit = KitsConfig.kits[kitName]
                if (kit != null) {
                    val lastUsed = playerCooldowns[uuid]?.get(kitName) ?: 0L
                    val timeLeft = lastUsed - Instant.now().epochSecond

                    if (timeLeft > 0) {
                        playerCooldowns[uuid]?.set(kitName, Instant.now().epochSecond + timeLeft)
                    }
                }
            }
        }

        playerCooldowns.keys.forEach { savePlayerData(it) }
    }


    fun resetKitCooldown(player: ServerPlayerEntity, kitName: String): Boolean {
        val uuid = player.uuid
        val kit = KitsConfig.kits[kitName.lowercase()] ?: return false

        playerCooldowns[uuid]?.remove(kitName.lowercase())
        db?.clearCooldown(uuid, kitName.lowercase())
        savePlayerData(uuid)

        player.sendMessage(KitsConfig.getMessage("kitCooldownResetSelf", "kit" to kit.name), false)

        return true
    }

    private fun requiredSlotsFor(reward: Reward): Int = when (reward.type.lowercase()) {
        "item"          -> 1
        "item_pack"     -> reward.pack?.count { it.material != null } ?: 0
        "item_random"   -> 1
        "command", "command_pack", "command_random" -> 0
        else -> 0
    }

    private fun requiredSlotsTotal(rewards: List<Reward>): Int =
        rewards.sumOf { requiredSlotsFor(it) }

    private fun selectWeighted(list: List<WeightedRewardEntry>): WeightedRewardEntry? {
        val valid = list.filter { it.weight > 0.0 }
        if (valid.isEmpty()) return null
        val total = valid.sumOf { it.weight }
        val r = kotlin.random.Random.nextDouble(0.0, total)
        var acc = 0.0
        for (e in valid) {
            acc += e.weight
            if (r <= acc) return e
        }
        return valid.last()
    }

    private fun createStack(entry: PrizePackEntry, player: ServerPlayerEntity): ItemStack {
        val material = entry.material ?: "minecraft:paper"
        val amount   = entry.amount ?: 1
        val cfg = KitsConfig.ItemConfig(
            material = material,
            amount = amount,
            customModelData = entry.customModelData,
            enchants = entry.enchants,
            displayname = entry.displayname,
            lore = entry.lore,
            enchantEffect = false,
            unbreakable = entry.unbreakable ?: false,
            attributes = entry.attributes
        )
        return createItemStack(cfg, player, null)
    }

    private fun runCommand(cmd: String?, player: ServerPlayerEntity) {
        if (cmd.isNullOrBlank()) return
        val c = cmd.replace("%player%", player.name.string)
        player.server.commandManager.executeWithPrefix(player.server.commandSource, c)
    }

    private fun giveReward(player: ServerPlayerEntity, reward: Reward) {
        when (reward.type.lowercase()) {

            "item" -> {
                val cfg = KitsConfig.ItemConfig(
                    material = reward.material ?: "minecraft:paper",
                    amount = reward.amount ?: 1,
                    customModelData = reward.customModelData,
                    enchants = reward.enchants,
                    displayname = reward.displayname,
                    lore = reward.lore,
                    enchantEffect = reward.enchantEffect == true,
                    unbreakable = reward.unbreakable ?: false,
                    attributes = reward.attributes
                )
                player.inventory.insertStack(createItemStack(cfg, player, null))
            }

            "command" -> runCommand(reward.command, player)

            "item_pack" -> reward.pack?.forEach { entry ->
                if (entry.material != null) {
                    player.inventory.insertStack(createStack(entry, player))
                }
            }

            "command_pack" -> reward.pack?.forEach { entry ->
                runCommand(entry.command, player)
            }

            "item_random" -> {
                val chosen = selectWeighted(reward.randomList ?: emptyList())
                if (chosen == null || chosen.material == null) return
                val entry = PrizePackEntry(
                    material = chosen.material,
                    amount = chosen.amount,
                    displayname = chosen.displayname,
                    lore = chosen.lore,
                    enchants = chosen.enchants,
                    attributes = chosen.attributes,
                    unbreakable = chosen.unbreakable,
                    hideTooltip = chosen.hideTooltip,
                    customModelData = chosen.customModelData
                )
                player.inventory.insertStack(createStack(entry, player))
            }

            "command_random" -> {
                val chosen = selectWeighted(reward.randomList ?: emptyList())
                runCommand(chosen?.command, player)
            }
        }
    }
}