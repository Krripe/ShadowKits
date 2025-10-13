package org.krripe.shadowkits.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.text.MutableText
import org.krripe.shadowkits.kits.KitManager
import org.krripe.shadowkits.utils.CustomColorParser.processAllFormats
import java.io.File
import java.nio.file.Paths

object KitsConfig {

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val configFile = Paths.get("config", "shadowkits", "config.json").toFile()
    private val menuFile = Paths.get("config", "shadowkits", "menu.json").toFile()
    private val menuPreviewFile = Paths.get("config", "shadowkits", "menu_preview.json").toFile()
    val kitsFolder: File = Paths.get("config", "shadowkits", "kits").toFile()

    lateinit var config: Config
    lateinit var menuConfig: MenuConfig
    lateinit var menuPreviewConfig: PreviewMenuConfig
    val kits = mutableMapOf<String, KitConfig>()

    data class Config(
        val database: DatabaseConfig = DatabaseConfig(),
        val messages: Messages = Messages()
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


    data class MenuConfig(
        val title: String = "&6Select a Kit",
        val size: Int = 54,
        val pages: Int = 3,
        val fillItem: String = "minecraft:black_stained_glass_pane",
        val fillerName: String = " ",
        val fillerCustomModelData: Int? = null,
        val fillersEnabled: Boolean = true,
        val fillerSlots: List<Int> = listOf(
            0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,46,47,48,50,51,52
        ),
        val nextPageButton: ButtonConfig = ButtonConfig(53, "minecraft:arrow", "&aNext Page"),
        val prevPageButton: ButtonConfig = ButtonConfig(45, "minecraft:arrow", "&aPrevious Page"),
        val closeButton: ButtonConfig = ButtonConfig(49, "minecraft:barrier", "&cClose")
    )

    data class PreviewMenuConfig(
        val title: String = "&6Kit Preview",
        val size: Int = 54,
        val fillItem: String = "minecraft:gray_stained_glass_pane",
        val fillerName: String = " ",
        val fillerCustomModelData: Int? = null,
        val fillersEnabled: Boolean = true,
        val fillerSlots: List<Int> = listOf(
            0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53
        ),
        val previewSlots: List<Int> = listOf(
            10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43
        ),
        val closeButton: ButtonConfig = ButtonConfig(49, "minecraft:barrier", "&cClose")
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

    data class ButtonConfig(
        val slot: Int,
        val material: String,
        val name: String,
        val customModelData: Int? = null
    )

    data class KitConfig(
        val name: String,
        val onJoin: Boolean,
        val firstTimeJoin: Boolean = false,
        val cooldown: String,
        val claims: Boolean,
        val maxClaims: Int,
        val permission: String?,
        val costMoney: Boolean,
        val cost: Int,
        val claimSound: String?,
        val cooldownSound: String?,
        val page: Int = 1,
        val slotOnPage: Int = 1,
        val displayItem: ItemConfig,
        val items: List<ItemConfig>,
        val commands: List<CommandItemConfig>
    )

    data class CommandItemConfig(
        val command: String,
        val displayItem: ItemConfig
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


    fun loadConfig() {
        kitsFolder.mkdirs()
        KitManager.updateCooldownsAfterReload()

        config = loadOrCreateJson(configFile, Config())
        menuConfig = loadOrCreateJson(menuFile, MenuConfig())
        menuPreviewConfig = loadOrCreateJson(menuPreviewFile, PreviewMenuConfig())

        kits.clear()
        if (kitsFolder.listFiles()?.isEmpty() != false) {
            createExampleKits()
        }

        kitsFolder.listFiles { file -> file.extension == "json" }?.forEach { file ->
            val kit = loadJson(file, KitConfig("", false,
                firstTimeJoin = false,
                cooldown = "0s",
                claims = false,
                maxClaims = 0,
                permission = null,
                costMoney = false,
                cost = 0,
                claimSound = null,
                cooldownSound = null,
                page = 1,
                slotOnPage = -1,
                displayItem = ItemConfig("minecraft:paper"),
                items = emptyList(),
                commands = emptyList()
            ))
            kits[kit.name.lowercase()] = kit
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
            permission = "kit.adventurestarter",
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
            items = listOf(
                ItemConfig(
                    material = "minecraft:diamond_sword",
                    amount = 1,
                    enchants = listOf(
                        EnchantmentConfig("minecraft:sharpness", 50),
                        EnchantmentConfig("minecraft:unbreaking", 10)
                    ),
                    displayname = "&bStarter Sword",
                    lore = listOf("&7A reliable sword for adventure!"),
                    enchantEffect = true,
                    damage = 50
                ),
                ItemConfig(
                    material = "minecraft:diamond_pickaxe",
                    amount = 1,
                    enchants = listOf(
                        EnchantmentConfig("minecraft:efficiency", 3),
                        EnchantmentConfig("minecraft:unbreaking", 2)
                    ),
                    displayname = "&bStarter Pickaxe",
                    lore = listOf("&7Mine faster with efficiency!")
                ),
                ItemConfig(
                    material = "minecraft:diamond_axe",
                    amount = 1,
                    enchants = listOf(
                        EnchantmentConfig("minecraft:efficiency", 3),
                        EnchantmentConfig("minecraft:unbreaking", 2)
                    ),
                    displayname = "&bStarter Axe",
                    lore = listOf("&7Chop trees with ease!")
                ),
                ItemConfig(
                    material = "minecraft:diamond_shovel",
                    amount = 1,
                    enchants = listOf(
                        EnchantmentConfig("minecraft:efficiency", 3),
                        EnchantmentConfig("minecraft:unbreaking", 2)
                    ),
                    displayname = "&bStarter Shovel",
                    lore = listOf("&7Dig through dirt effortlessly!")
                )
            ),
            commands = listOf(
                CommandItemConfig(
                    command = "give %player% minecraft:cooked_beef 32",
                    displayItem = ItemConfig(
                        material = "minecraft:cooked_beef",
                        amount = 32,
                        displayname = "&6Starter Food",
                        lore = listOf("&7Receive 32 cooked beef for your adventure!")
                    )
                )
            )
        )

        val starterFile = File(kitsFolder, "starter.json")
        starterFile.writeText(gson.toJson(starterKit))
        kits[starterKit.name.lowercase()] = starterKit
    }

    fun saveConfig() {
        saveJson(configFile, config)
        saveJson(menuFile, menuConfig)
        saveJson(menuPreviewFile, menuPreviewConfig)
        kits.forEach { (name, kit) ->
            saveJson(File(kitsFolder, "$name.json"), kit)
        }
    }

    private inline fun <reified T> loadJson(file: File, default: T): T {
        return try {
            if (file.exists()) gson.fromJson(file.readText(), object : TypeToken<T>() {}.type) else default
        } catch (_: Exception) {
            default
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