package org.krripe.shadowkits.gui

object GuiSlotParser {

    fun parse(exprs: List<String>, size: Int): List<Int> {
        val out = linkedSetOf<Int>()

        exprs.flatMap { it.split(',', ';', ' ') }.forEach { raw ->
            val expr = raw.trim()
            if (expr.isBlank()) return@forEach

            val separator = when {
                ".." in expr -> ".."
                "-" in expr -> "-"
                else -> null
            }

            if (separator != null) {
                val start = expr.substringBefore(separator).trim().toIntOrNull()
                val end = expr.substringAfter(separator).trim().toIntOrNull()

                if (start != null && end != null) {
                    for (slot in minOf(start, end)..maxOf(start, end)) {
                        if (slot in 0 until size) out.add(slot)
                    }
                }

                return@forEach
            }

            expr.toIntOrNull()?.takeIf { it in 0 until size }?.let(out::add)
        }

        return out.toList()
    }

    fun buttonSlots(button: GuiButtonCfg, size: Int): List<Int> {
        val fromExpr = parse(button.slots, size)
        if (fromExpr.isNotEmpty()) return fromExpr
        return if (button.slot in 0 until size) listOf(button.slot) else emptyList()
    }
}