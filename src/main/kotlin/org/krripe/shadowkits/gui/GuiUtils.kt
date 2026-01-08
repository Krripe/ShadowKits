package org.krripe.shadowkits.gui

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Style
import net.minecraft.util.Identifier
import net.minecraft.util.Unit
import org.krripe.shadowkits.utils.CustomColorParser

object GuiUtils {

    fun makeItem(
        material: String,
        name: String,
        lore: List<String> = emptyList(),
        modelData: Int? = null,
        glow: Boolean = false,
        hideAdditionalTooltip: Boolean = true,
        hideTooltip: Boolean = false
    ): ItemStack {
        val item = ItemStack(Registries.ITEM.get(Identifier.of(material)))

        item.set(
            DataComponentTypes.CUSTOM_NAME,
            CustomColorParser.processAllFormats(name).setStyle(Style.EMPTY.withItalic(false))
        )

        if (lore.isNotEmpty()) {
            val lines = lore.map {
                CustomColorParser.processAllFormats(it).setStyle(Style.EMPTY.withItalic(false))
            }
            item.set(DataComponentTypes.LORE, LoreComponent(lines))
        }

        modelData?.let { item.set(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent(it)) }
        if (glow) item.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        if (hideAdditionalTooltip) item.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
        if (hideTooltip) item.set(DataComponentTypes.HIDE_TOOLTIP, Unit.INSTANCE)

        return item
    }

    fun makeButton(b: Button) = makeItem(
        material = b.icon, name = b.name, lore = b.lore,
        modelData = b.modelData, glow = b.glow,
        hideAdditionalTooltip = b.hideAdditionalTooltip, hideTooltip = b.hideTooltip
    )

    fun makeFiller(f: Filler) = makeItem(
        material = f.icon, name = f.name, lore = f.lore,
        modelData = f.modelData, glow = f.glow,
        hideAdditionalTooltip = f.hideAdditionalTooltip, hideTooltip = f.hideTooltip
    )

    fun parseSlots(exprs: List<String>, size: Int): List<Int> {
        val out = linkedSetOf<Int>()
        exprs.forEach { token ->
            token.split(',', ';', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { part ->
                    val range = part.split("..", "-")
                    if (range.size == 2) {
                        val a = range[0].toIntOrNull()
                        val b = range[1].toIntOrNull()
                        if (a != null && b != null) {
                            val lo = maxOf(0, minOf(a, b))
                            val hi = minOf(size - 1, maxOf(a, b))
                            for (i in lo..hi) out.add(i)
                        }
                    } else {
                        val n = part.toIntOrNull()
                        if (n != null && n in 0 until size) out.add(n)
                    }
                }
        }
        return out.toList()
    }
}