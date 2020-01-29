package com.template.oracles

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class RateOf(val currencyFrom: String, val currencyTo: String)