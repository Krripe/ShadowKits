package org.krripe.shadowkits.gui

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.brigadier.StringReader
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.ItemStackArgumentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.util.Identifier
import net.minecraft.util.Unit
import org.krripe.shadowkits.utils.CustomColorParser
import java.util.UUID

object GuiItemFactory {

    fun fromCfg(player: ServerPlayerEntity, cfg: GuiItemCfg, placeholders: Map<String, String> = emptyMap()): ItemStack {
        val stack = createBaseStack(player.server, cfg.item, cfg.amount)
        val name = cfg.name?.let { GuiPlaceholders.apply(it, placeholders) }

        name?.let {
            stack.set(
                DataComponentTypes.CUSTOM_NAME,
                CustomColorParser.processAllFormats(it).setStyle(Style.EMPTY.withItalic(false))
            )
        }

        val lore = GuiPlaceholders.applyList(cfg.lore, placeholders)

        if (lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(lore.map { CustomColorParser.processAllFormats(it).setStyle(Style.EMPTY.withItalic(false)) })
            )
        }

        cfg.modelData?.let { stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(it)) }
        if (cfg.glow) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        if (cfg.hideAdditionalTooltip) stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
        if (cfg.hideTooltip) stack.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE)

        return stack
    }

    private fun createBaseStack(server: MinecraftServer, raw: String, amount: Int): ItemStack {
        val clean = raw.trim()

        if (clean.startsWith("head=", true) || clean.startsWith("head|", true)) {
            return createHead(clean, amount)
        }

        return parseVanilla(server, clean, amount)
            ?: simpleItem(clean, amount)
            ?: ItemStack(Items.PAPER, amount.coerceAtLeast(1))
    }

    private fun createHead(raw: String, amount: Int): ItemStack {
        val value = when {
            raw.startsWith("head=", true) -> raw.substringAfter("head=")
            raw.startsWith("head|", true) -> raw.substringAfter("head|")
            else -> raw
        }

        val split = value.split("|", limit = 2)
        val name = if (split.size == 2) split[0].take(16).ifBlank { "shadowkits_head" } else "shadowkits_head"
        val texture = if (split.size == 2) split[1] else split[0]

        val stack = ItemStack(Items.PLAYER_HEAD, amount.coerceAtLeast(1))
        val profile = GameProfile(UUID.randomUUID(), name)

        profile.properties.put("textures", Property("textures", texture))
        stack.set(DataComponentTypes.PROFILE, ProfileComponent(profile))

        return stack
    }

    private fun parseVanilla(server: MinecraftServer, material: String, amount: Int): ItemStack? {
        return try {
            val reader = StringReader(material)
            val access = CommandRegistryAccess.of(server.registryManager, server.saveProperties.enabledFeatures)
            val arg = ItemStackArgumentType.itemStack(access).parse(reader)
            arg.createStack(amount.coerceAtLeast(1), true)
        } catch (_: Throwable) {
            null
        }
    }

    private fun simpleItem(raw: String, amount: Int): ItemStack? {
        val id = Identifier.tryParse(raw.substringBefore("[").trim()) ?: return null
        val item = runCatching { Registries.ITEM.get(id) }.getOrNull() ?: return null
        if (item == Items.AIR) return null
        return ItemStack(item, amount.coerceAtLeast(1))
    }
}