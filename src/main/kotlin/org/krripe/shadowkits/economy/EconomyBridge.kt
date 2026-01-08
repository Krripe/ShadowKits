package org.krripe.shadowkits.economy

import java.math.BigDecimal
import java.util.UUID

interface EconomyBridge {
    fun isAvailable(): Boolean
    fun charge(uuid: UUID, price: BigDecimal, currency: String): Boolean
}