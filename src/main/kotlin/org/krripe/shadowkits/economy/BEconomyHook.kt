package org.krripe.shadowkits.economy

import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.api.EconomyAPI
import java.math.BigDecimal
import java.util.UUID

object BEconomyHook : EconomyBridge {
    @Volatile private var api: EconomyAPI? = null

    private fun ensureApi(): EconomyAPI? {
        if (api != null) return api
        if (!BEconomy.isInitialized()) {
            return runCatching { BEconomy.getAPI() }.onSuccess { api = it }.getOrNull()
        }
        api = BEconomy.getAPI()
        return api
    }

    override fun isAvailable(): Boolean = ensureApi() != null

    override fun charge(uuid: UUID, price: BigDecimal, currency: String): Boolean {
        val e = ensureApi() ?: return false
        val type = runCatching { e.getCurrencyTypeOrFallback(currency) }.getOrElse { currency }
        if (!e.hasEnoughFunds(uuid, price, type)) return false
        return runCatching { e.decreaseBalance(uuid, type, price) }.getOrElse {
            runCatching { e.subtractBalance(uuid, price, type) }.getOrDefault(false)
        }
    }
}