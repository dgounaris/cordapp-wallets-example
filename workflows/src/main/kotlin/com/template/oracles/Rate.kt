package com.template.oracles

import net.corda.core.contracts.CommandData
import java.math.BigDecimal

data class Rate(val currencyFrom: String, val currencyTo: String, val rate: BigDecimal): CommandData