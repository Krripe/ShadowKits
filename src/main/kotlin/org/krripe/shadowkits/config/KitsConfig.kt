package org.krripe.shadowkits.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.text.MutableText
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.CustomColorParser.processAllFormats
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date

object KitsConfig {

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val configFile = Paths.get("config", "shadowkits", "config.json").toFile()
    val kitsFolder: File = Paths.get("config", "shadowkits", "kits").toFile()
    private val backupsKitsDir: File = Paths.get("config", "shadowkits", "backups", "kits").toFile()

    lateinit var config: Config
    val kits = mutableMapOf<String, KitConfig>()

    data class Config(
        val database: DatabaseConfig = DatabaseConfig(),
        val messages: Messages = Messages(),
        val economy: EconomyConfig = EconomyConfig()
    )

    data class EconomyConfig(
        val defaultProvider: String = "auto",
        val defaultCurrency: String = "dollars"
    )

    data class Messages(
        val kitClaimed: String = "&aYou have claimed the kit: &6%kit%",
        val noPermission: String = "&cYou do not have permission to use this kit!",
        val inCooldown: String = "&cYou must wait before claiming this kit again!",
        val kitNotFound: String = "&cKit &6%kit% &cdoes not exist!",
        val kitCreated: String = "&aKit &6%kit% &ahas been created!",
        val kitGiven: String = "&aKit &6%kit% &awas given to &6%player%!",
        val kitRemoved: String = "&cKit &6%kit% &chas been removed!",
        val playerNotFound: String = "&cPlayer not found!",
        val kitsReloaded: String = "&aKits configuration reloaded successfully!",
        val notEnoughMoney: String = "&cYou do not have enough money to claim this kit! Cost: &6%cost%",
        val kitPurchased: String = "&aYou have purchased the kit for &6%cost%!",
        val economyNotAvailable: String = "&cThe economy system is not available!",
        val kitMaxClaimsReached: String = "&cYou have reached the maximum number of claims (&6%max%&c) for the kit: &6%kit%!",
        val kitCooldownReset: String = "&aCooldown for kit &6%kit% &ahas been reset for player &6%player%&a!",
        val kitCooldownResetSelf: String = "&aYour cooldown for kit &6%kit% &ahas been reset!",
        val inventoryFull: String = "&cYour inventory is full! Make some space before claiming this kit.",
        val kitBlockSet: String = "&aYou've set the block (&7%x% %y% %z%&a) for kit &6%kit%&a.",
        val kitBlockUnset: String = "&aYou've unlinked the kit from the block (&7%x% %y% %z%&a).",
        val kitBlockLookAtBlock: String = "&cLook at a block within 6 blocks to use this command!",
        val cooldownLoreFormat: String = "&7Cooldown: &e%time%",
        val cooldownReadyText: String = "&aReady"
    )

    data class MysqlConfig(
        val host: String = "127.0.0.1",
        val port: Int = 3306,
        val database: String = "shadowkits",
        val username: String = "root",
        val password: String = "password"
    )

    data class DatabaseConfig(
        val enabled: Boolean = false,
        val type: String = "sqlite",
        val mysql: MysqlConfig = MysqlConfig()
    )

    data class KitConfig(
        val name: String,
        val onJoin: Boolean,
        val firstTimeJoin: Boolean = false,
        val cooldown: String,
        val claims: Boolean,
        val maxClaims: Int,
        val permission: String?,
        val economyProvider: String?,
        val currency: String?,
        val costMoney: Boolean,
        val cost: Int,
        val claimSound: String?,
        val cooldownSound: String?,
        val page: Int = 1,
        val slotOnPage: Int = 1,
        val displayItem: ItemConfig,
        val rewards: List<Reward>? = null,
        val items: List<ItemConfig> = emptyList(),
        val commands: List<CommandItemConfig> = emptyList()
    )

    data class CommandItemConfig(
        val command: String,
        val displayItem: ItemConfig
    )

    data class CommandDisplayItem(
        val material: String,
        val amount: Int = 1,
        val displayname: String,
        val lore: List<String> = emptyList(),
        val enchantEffect: Boolean = false,
        val hideTooltip: Boolean? = null,
        val customModelData: Int? = null
    )

    data class PrizePackEntry(
        val material: String? = null,
        val amount: Int? = 1,
        val displayname: String? = null,
        val lore: List<String>? = null,
        val enchants: List<EnchantmentConfig>? = null,
        val attributes: List<AttributeConfig>? = null,
        val unbreakable: Boolean? = null,
        val hideTooltip: Boolean? = null,
        val customModelData: Int? = null,
        val command: String? = null
    )

    data class WeightedRewardEntry(
        val weight: Double = 1.0,
        val material: String? = null,
        val amount: Int? = 1,
        val displayname: String? = null,
        val lore: List<String>? = null,
        val enchants: List<EnchantmentConfig>? = null,
        val attributes: List<AttributeConfig>? = null,
        val unbreakable: Boolean? = null,
        val hideTooltip: Boolean? = null,
        val customModelData: Int? = null,
        val command: String? = null
    )

    data class Reward(
        val type: String,
        val material: String? = null,
        val amount: Int? = null,
        val displayname: String? = null,
        val lore: List<String>? = null,
        val enchants: List<EnchantmentConfig>? = null,
        val attributes: List<AttributeConfig>? = null,
        val enchantEffect: Boolean? = null,
        val unbreakable: Boolean? = null,
        val hideTooltip: Boolean? = null,
        val customModelData: Int? = null,
        val command: String? = null,
        val displayItem: CommandDisplayItem? = null,
        val pack: List<PrizePackEntry>? = null,
        val randomList: List<WeightedRewardEntry>? = null
    )

    data class ItemConfig(
        val material: String,
        val amount: Int = 1,
        val customModelData: Int? = null,
        val enchants: List<EnchantmentConfig>? = null,
        val displayname: String? = null,
        val lore: List<String>? = null,
        val cooldownLore: Boolean = false,
        val enchantEffect: Boolean = false,
        val unbreakable: Boolean = false,
        val damage: Int? = null,
        val attributes: List<AttributeConfig>? = null,
        val book: BookConfig? = null
    )

    data class BookConfig(
        val title: String?,
        val author: String?,
        val generation: Int = 0,
        val pages: List<PageConfig> = emptyList()
    )

    data class PageConfig(
        val page: List<String> = emptyList()
    )

    data class AttributeConfig(
        val name: String,
        val amount: Double,
        val operation: String,
        val slot: String
    )

    data class EnchantmentConfig(
        val name: String,
        val level: Int
    )

    private fun backupKitFile(original: File) {
        runCatching {
            if (!original.exists() || original.isDirectory) return
            backupsKitsDir.mkdirs()

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
            val backup = File(backupsKitsDir, "${original.nameWithoutExtension}_$stamp.json")

            original.copyTo(backup, overwrite = false)
        }
    }

    fun migrateLegacy(kit: KitConfig): KitConfig {
        val legacyItems: List<ItemConfig>? = kit.items
        val legacyCommands: List<CommandItemConfig>? = kit.commands

        if (kit.rewards != null) {
            return kit.copy(items = emptyList(), commands = emptyList())
        }

        val asRewards = buildList<Reward> {
            legacyItems.orEmpty().forEach { itc ->
                add(
                    Reward(
                        type = "item",
                        material = itc.material,
                        amount = itc.amount,
                        displayname = itc.displayname,
                        lore = itc.lore,
                        enchants = itc.enchants,
                        attributes = itc.attributes,
                        enchantEffect = itc.enchantEffect,
                        unbreakable = itc.unbreakable,
                        hideTooltip = null,
                        customModelData = itc.customModelData
                    )
                )
            }

            legacyCommands.orEmpty().forEach { cc ->
                add(
                    Reward(
                        type = "command",
                        command = cc.command,
                        displayItem = CommandDisplayItem(
                            material = cc.displayItem.material,
                            amount = cc.displayItem.amount,
                            displayname = cc.displayItem.displayname ?: "&eCommand",
                            lore = cc.displayItem.lore ?: emptyList(),
                            enchantEffect = cc.displayItem.enchantEffect,
                            customModelData = cc.displayItem.customModelData
                        )
                    )
                )
            }
        }
        return kit.copy(
            rewards = asRewards,
            items = emptyList(),
            commands = emptyList()
        )
    }

    fun loadConfig() {
        kitsFolder.mkdirs()
        KitManager.updateCooldownsAfterReload()

        config = loadOrCreateJson(configFile, Config())

        kits.clear()
        if (kitsFolder.listFiles()?.isEmpty() != false) {
            createExampleKits()
        }

        kitsFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
            val loaded = loadJson(
                file,
                KitConfig(
                    "",
                    false,
                    firstTimeJoin = false,
                    cooldown = "0s",
                    claims = false,
                    maxClaims = 0,
                    permission = null,
                    economyProvider = null,
                    currency = null,
                    costMoney = false,
                    cost = 0,
                    claimSound = null,
                    cooldownSound = null,
                    page = 1,
                    slotOnPage = -1,
                    displayItem = ItemConfig("minecraft:paper"),
                    items = emptyList(),
                    commands = emptyList()
                )
            )
            val migrated = migrateLegacy(loaded)
            kits[migrated.name.lowercase()] = migrated
        }

        saveConfig()
    }

    private inline fun <reified T> loadOrCreateJson(file: File, default: T): T {
        return try {
            if (!file.exists()) {
                file.parentFile.mkdirs()
                file.writeText(gson.toJson(default))
                default
            } else {
                val loaded: T = gson.fromJson(file.readText(), object : TypeToken<T>() {}.type)
                file.writeText(gson.toJson(loaded))
                loaded
            }
        } catch (_: Exception) {
            default
        }
    }

    private fun createExampleKits() {
        val starterKit = KitConfig(
            name = "Starter",
            onJoin = false,
            firstTimeJoin = false,
            cooldown = "1h",
            claims = true,
            maxClaims = 5,
            permission = "kit.starter",
            economyProvider = "auto",
            currency = "money",
            costMoney = false,
            cost = 100,
            claimSound = "minecraft:entity.player.levelup",
            cooldownSound = "minecraft:entity.villager.no",
            page = -1,
            slotOnPage = -1,
            displayItem = ItemConfig(
                material = "minecraft:diamond_sword",
                amount = 1,
                displayname = "&bKit Starter",
                lore = listOf(
                    "&7A powerful kit to begin your journey!",
                    "&aIncludes enchanted tools and a food!"
                ),
                cooldownLore = true,
                enchantEffect = true
            ),
            rewards = listOf(
                Reward(
                    type = "item",
                    material = "minecraft:iron_pickaxe",
                    amount = 1,
                    displayname = "&bIron Pickaxe",
                    lore = listOf("&7Handy starter tool"),
                    enchants = listOf(
                        EnchantmentConfig("minecraft:unbreaking", 2)
                    ),
                    enchantEffect = true
                ),

                Reward(
                    type = "command",
                    command = "give %player% minecraft:cooked_beef 16",
                    displayItem = CommandDisplayItem(
                        material = "minecraft:cooked_beef",
                        amount = 16,
                        displayname = "&6Food Voucher",
                        lore = listOf("&7Runs a give-food command")
                    )
                ),

                Reward(
                    type = "item_pack",
                    displayname = "&aStarter Supplies",
                    lore = listOf("&7A small pack of basics"),
                    pack = listOf(
                        PrizePackEntry(material = "minecraft:oak_log", amount = 32, displayname = "&eOak Logs"),
                        PrizePackEntry(material = "minecraft:torch", amount = 16, displayname = "&eTorches")
                    )
                ),

                Reward(
                    type = "command_pack",
                    displayItem = CommandDisplayItem(
                        material = "minecraft:paper",
                        amount = 1,
                        displayname = "&dCommand Pack",
                        lore = listOf("&7Executes multiple commands")
                    ),
                    pack = listOf(
                        PrizePackEntry(command = "effect give %player% minecraft:regeneration 5 1 true"),
                        PrizePackEntry(command = "say ShadowKits: Command pack reward for %player%")
                    )
                ),

                Reward(
                    type = "item_random",
                    displayname = "&eRandom Item",
                    lore = listOf("&7One random item from the list"),
                    randomList = listOf(
                        WeightedRewardEntry(weight = 1.0, material = "minecraft:diamond", amount = 1, displayname = "&bDiamond"),
                        WeightedRewardEntry(weight = 3.0, material = "minecraft:gold_ingot", amount = 3, displayname = "&6Gold Ingots"),
                        WeightedRewardEntry(weight = 6.0, material = "minecraft:iron_ingot", amount = 5, displayname = "&fIron Ingots")
                    )
                ),

                Reward(
                    type = "command_random",
                    displayItem = CommandDisplayItem(
                        material = "minecraft:tripwire_hook",
                        amount = 1,
                        displayname = "&eRandom Command",
                        lore = listOf("&7Executes one random command")
                    ),
                    randomList = listOf(
                        WeightedRewardEntry(weight = 1.0, command = "effect give %player% minecraft:strength 60 1 true"),
                        WeightedRewardEntry(weight = 2.0, command = "effect give %player% minecraft:speed 60 1 true"),
                        WeightedRewardEntry(weight = 3.0, command = "say ShadowKits: Good luck, %player%!")
                    )
                )
            ),
            items = emptyList(),
            commands = emptyList()
        )

        val starterFile = File(kitsFolder, "starter.json")
        saveKitJson(starterFile, starterKit)
        kits[starterKit.name.lowercase()] = starterKit
    }

    fun saveConfig() {
        saveJson(configFile, config)
        kits.forEach { (name, kit) ->
            saveKitJson(File(kitsFolder, "$name.json"), kit)
        }
    }

    private inline fun <reified T> loadJson(file: File, default: T): T {
        return try {
            if (file.exists()) gson.fromJson(file.readText(), object : TypeToken<T>() {}.type) else default
        } catch (_: Exception) {
            default
        }
    }

    private fun saveKitJson(file: File, kit: KitConfig) {
        file.parentFile.mkdirs()
        val jsonObj = gson.toJsonTree(kit).asJsonObject
        if (jsonObj.has("rewards")) {
            jsonObj.remove("items")
            jsonObj.remove("commands")
        }
        val newText = gson.toJson(jsonObj)
        val oldText = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

        fun norm(s: String) = s.trim().replace("\r\n", "\n")

        val changed = oldText == null || norm(oldText) != norm(newText)
        if (changed && file.exists()) {
            backupKitFile(file)
        }

        if (changed) {
            file.writeText(newText)
        }
    }

    private fun saveJson(file: File, data: Any) {
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(data))
    }

    fun getMessage(key: String, vararg args: Pair<String, String>): MutableText {
        val message = when (key) {
            "kitClaimed" -> config.messages.kitClaimed
            "noPermission" -> config.messages.noPermission
            "inCooldown" -> config.messages.inCooldown
            "kitNotFound" -> config.messages.kitNotFound
            "kitCreated" -> config.messages.kitCreated
            "kitGiven" -> config.messages.kitGiven
            "kitRemoved" -> config.messages.kitRemoved
            "playerNotFound" -> config.messages.playerNotFound
            "kitsReloaded" -> config.messages.kitsReloaded
            "notEnoughMoney" -> config.messages.notEnoughMoney
            "kitPurchased" -> config.messages.kitPurchased
            "economyNotAvailable" -> config.messages.economyNotAvailable
            "kitMaxClaimsReached" -> config.messages.kitMaxClaimsReached
            "kitCooldownReset" -> config.messages.kitCooldownReset
            "kitCooldownResetSelf" -> config.messages.kitCooldownResetSelf
            "inventoryFull" -> config.messages.inventoryFull
            "kitBlockSet" -> config.messages.kitBlockSet
            "kitBlockUnset" -> config.messages.kitBlockUnset
            "kitBlockLookAtBlock" -> config.messages.kitBlockLookAtBlock
            "cooldownLoreFormat" -> config.messages.cooldownLoreFormat
            "cooldownReadyText" -> config.messages.cooldownReadyText
            else -> ""
        }

        var formattedMessage = message
        args.forEach { (placeholder, value) ->
            formattedMessage = formattedMessage.replace("%$placeholder%", value)
        }

        return processAllFormats(formattedMessage)
    }
}