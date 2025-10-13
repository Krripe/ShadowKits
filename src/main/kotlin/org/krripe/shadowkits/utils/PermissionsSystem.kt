package org.krripe.shadowkits.utils

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.command.ServerCommandSource

class PermissionsSystem {
    fun hasPermission(player: ServerCommandSource, permission: String, level: Int): Boolean {
        return if (hasLuckPermsPermission(player, permission, level)) {
            true
        } else {
            player.hasPermissionLevel(level)
        }
    }

    private fun hasLuckPermsPermission(player: ServerCommandSource, permission: String, level: Int): Boolean {
        return try {
            Permissions.check(player, permission, level)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }
}