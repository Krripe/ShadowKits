package org.krripe.shadowkits.economy

import java.math.BigDecimal
import java.util.UUID

object EconomyRouter : EconomyBridge {

    private fun pickForced(key: String): EconomyBridge? = when (key.lowercase()) {
        "impactor" -> if (ImpactorEconomyHook.isAvailable()) ImpactorEconomyHook else null
        "beconomy" -> if (BEconomyHook.isAvailable()) BEconomyHook else null
        "auto"     -> pickAuto()
        else       -> null
    }

    private fun pickAuto(): EconomyBridge? = when {
        ImpactorEconomyHook.isAvailable() -> ImpactorEconomyHook
        BEconomyHook.isAvailable()        -> BEconomyHook
        else -> null
    }

    private fun pick(key: String?): EconomyBridge? =
        if (key.isNullOrBlank()) pickAuto() else pickForced(key)

    override fun isAvailable(): Boolean = pickAuto() != null

    override fun charge(uuid: UUID, price: BigDecimal, currency: String): Boolean {
        val hook = pickAuto() ?: return false
        return hook.charge(uuid, price, currency)
    }

    fun charge(uuid: UUID, price: BigDecimal, currency: String, provider: String?): Boolean {
        val hook = pick(provider) ?: return false
        return hook.charge(uuid, price, currency)
    }
}
