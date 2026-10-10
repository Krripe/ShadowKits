package org.krripe.shadowkits.gui

object GuiPlaceholders {

    fun apply(input: String, placeholders: Map<String, String>): String {
        var out = input
        placeholders.forEach { (key, value) -> out = out.replace("%$key%", value) }
        return out
    }

    fun applyList(input: List<String>, placeholders: Map<String, String>): List<String> =
        input.map { apply(it, placeholders) }
}