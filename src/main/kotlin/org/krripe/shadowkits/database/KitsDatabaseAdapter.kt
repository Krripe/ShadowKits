package org.krripe.shadowkits.database

import java.util.UUID

interface KitsDatabaseAdapter {
    fun connect(): Boolean
    fun disconnect(): Boolean

    fun saveCooldown(playerId: UUID, kitName: String, expiresAt: Long)
    fun getCooldown(playerId: UUID, kitName: String): Long
    fun clearCooldown(playerId: UUID, kitName: String)

    fun saveClaims(playerId: UUID, kitName: String, claims: Int)
    fun getClaims(playerId: UUID, kitName: String): Int
    fun clearClaims(playerId: UUID, kitName: String)

    fun allCooldownRows(): List<Triple<UUID, String, Long>>
    fun allClaimRows(): List<Triple<UUID, String, Int>>

    fun setFirstJoinDone(playerId: UUID, done: Boolean)
    fun isFirstJoinDone(playerId: UUID): Boolean
    fun allFirstJoinRows(): List<Pair<UUID, Boolean>>

    sealed class ClaimDecision {
        data class Allowed(val newExpiresAt: Long): ClaimDecision()
        data class Denied(val reason: Reason, val message: String? = null): ClaimDecision()
        enum class Reason { COOLDOWN, MAX_CLAIMS, ERROR }
        companion object {
            fun allowed(newExp: Long) = Allowed(newExp)
            fun denied(reason: Reason, msg: String? = null) = Denied(reason, msg)
        }
    }

    fun tryConsumeKit(
        playerId: UUID,
        kitName: String,
        cooldownSeconds: Long,
        useClaims: Boolean,
        maxClaims: Int
    ): ClaimDecision
}
