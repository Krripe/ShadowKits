package org.krripe.shadowkits.utils

import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

object SoundHelper {
    private const val DEFAULT_GAP_MS = 180L
    private val lastByPlayerAndId = mutableMapOf<UUID, MutableMap<String, Long>>()

    @JvmOverloads
    fun play(
        player: ServerPlayerEntity,
        id: String?,
        volume: Float = 1f,
        pitch: Float = 1f,
        minGapMs: Long = DEFAULT_GAP_MS
    ) {
        if (id.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        val map = lastByPlayerAndId.getOrPut(player.uuid) { mutableMapOf() }
        val last = map[id] ?: 0L
        if (now - last < minGapMs) return
        map[id] = now

        val key = Identifier.of(id)
        val ev = Registries.SOUND_EVENT.get(key) ?: return
        player.playSoundToPlayer(ev, net.minecraft.sound.SoundCategory.PLAYERS, volume, pitch)
    }

    fun resetPlayer(uuid: UUID) {
        lastByPlayerAndId.remove(uuid)
    }
}
