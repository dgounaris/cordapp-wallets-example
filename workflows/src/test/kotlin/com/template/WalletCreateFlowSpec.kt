package com.template

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isNullOrBlank
import com.template.flows.WalletCreateFlow
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WalletCreateFlowSpec: WalletFlowSpec() {

    @Test
    fun `creating a wallet should successfully happen if the amount is a positive number or zero`() {
        val flow = WalletCreateFlow("10 EUR")
        assertDoesNotThrow { partyA.startFlow(flow).getOrThrow() } // TODO make the flow return the tx to check it here
    }

    @Test
    fun `creating a wallet should throw if the amount is a negative number`() {
        val flow = WalletCreateFlow("-5 EUR")
        val ex = assertThrows<IllegalArgumentException> { partyA.startFlow(flow).getOrThrow() }
    }

    @Test
    fun `creating a wallet should throw if a wallet of this currency exists for node`() {
        val flow1 = WalletCreateFlow("10 EUR")
        assertDoesNotThrow { partyA.startFlow(flow1).getOrThrow() }
        val flow2 = WalletCreateFlow("5 USD")
        assertDoesNotThrow { partyA.startFlow(flow2).getOrThrow() }
        val flow3 = WalletCreateFlow("15 EUR")
        val ex = assertThrows<FlowException> { partyA.startFlow(flow3).getOrThrow() }
        assertEquals("There can be only one wallet per currency", ex.message)
    }

}