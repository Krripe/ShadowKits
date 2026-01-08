package org.krripe.shadowkits.economy

import net.impactdev.impactor.api.economy.EconomyService
import net.impactdev.impactor.api.economy.currency.Currency
import net.kyori.adventure.key.Key
import java.math.BigDecimal
import java.util.UUID

object ImpactorEconomyHook : EconomyBridge {
    private fun service(): EconomyService? =
        runCatching { EconomyService.instance() }.getOrNull()

    override fun isAvailable(): Boolean = service() != null

    override fun charge(uuid: UUID, price: BigDecimal, currency: String): Boolean {
        val svc = service() ?: return false
        val provider = svc.currencies()
        val key = Key.key(if (':' in currency) currency else "impactor:$currency")
        val cur: Currency = provider.currency(key).orElse(provider.primary())
        val account = runCatching { svc.account(cur, uuid).join() }.getOrNull() ?: return false
        val tx = account.withdraw(price)
        return tx.successful()
    }
}
