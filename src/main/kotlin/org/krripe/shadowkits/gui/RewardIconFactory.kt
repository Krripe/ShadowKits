package org.krripe.shadowkits.gui

import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import org.krripe.shadowkits.config.KitsConfig
import org.krripe.shadowkits.kits.KitManager

object RewardIconFactory {

    fun iconFor(player: ServerPlayerEntity, r: KitsConfig.Reward): ItemStack {
        return when (r.type.lowercase()) {

            "item" -> KitManager.createItemStack(
                KitsConfig.ItemConfig(
                    material = r.material ?: "minecraft:paper",
                    amount = r.amount ?: 1,
                    customModelData = r.customModelData,
                    enchants = r.enchants,
                    displayname = r.displayname,
                    lore = r.lore,
                    enchantEffect = r.enchantEffect == true,
                    unbreakable = r.unbreakable == true,
                    attributes = r.attributes
                ),
                player, null
            )

            "command", "command_pack" -> {
                r.displayItem?.let { di ->
                    KitManager.createItemStack(
                        KitsConfig.ItemConfig(
                            material = di.material,
                            amount = di.amount,
                            customModelData = di.customModelData,
                            enchants = null,
                            displayname = di.displayname,
                            lore = di.lore,
                            enchantEffect = di.enchantEffect
                        ),
                        player, null
                    )
                } ?: makeFallbackPaper(r.displayname ?: "&eCommand")
            }

            "item_pack" -> {
                val first = r.pack?.firstOrNull { it.material != null }
                if (first != null) {
                    KitManager.createItemStack(
                        KitsConfig.ItemConfig(
                            material = first.material!!,
                            amount = first.amount ?: 1,
                            customModelData = first.customModelData,
                            enchants = first.enchants,
                            displayname = r.displayname ?: first.displayname,
                            lore = r.lore ?: first.lore,
                            enchantEffect = false,
                            unbreakable = first.unbreakable ?: false,
                            attributes = first.attributes
                        ),
                        player, null
                    )
                } else makeFallbackPaper(r.displayname ?: "&eItem Pack")
            }

            "item_random" -> {
                r.displayItem?.let { di ->
                    KitManager.createItemStack(
                        KitsConfig.ItemConfig(
                            material = di.material,
                            amount = di.amount,
                            customModelData = di.customModelData,
                            enchants = null,
                            displayname = di.displayname,
                            lore = di.lore,
                            enchantEffect = di.enchantEffect
                        ),
                        player, null
                    )
                } ?: run {
                    val firstMat = r.randomList?.firstOrNull { it.material != null }
                    if (firstMat != null) {
                        KitManager.createItemStack(
                            KitsConfig.ItemConfig(
                                material = firstMat.material!!,
                                amount = firstMat.amount ?: 1,
                                customModelData = firstMat.customModelData,
                                enchants = firstMat.enchants,
                                displayname = r.displayname ?: firstMat.displayname,
                                lore = r.lore ?: firstMat.lore,
                                enchantEffect = false,
                                unbreakable = firstMat.unbreakable ?: false,
                                attributes = firstMat.attributes
                            ),
                            player, null
                        )
                    } else makeFallbackPaper(r.displayname ?: "&eRandom Item")
                }
            }

            "command_random" -> {
                r.displayItem?.let { di ->
                    KitManager.createItemStack(
                        KitsConfig.ItemConfig(
                            material = di.material,
                            amount = di.amount,
                            customModelData = di.customModelData,
                            enchants = null,
                            displayname = di.displayname,
                            lore = di.lore,
                            enchantEffect = di.enchantEffect
                        ),
                        player, null
                    )
                } ?: makeFallbackPaper(r.displayname ?: "&eRandom Command")
            }

            else -> makeFallbackPaper(r.displayname ?: "&7Reward")
        }
    }

    private fun makeFallbackPaper(name: String): ItemStack =
        KitManager.createItemStack(
            KitsConfig.ItemConfig(
                material = "minecraft:paper",
                amount = 1,
                displayname = name,
                enchantEffect = false
            ),
            null,
            null
        )
}