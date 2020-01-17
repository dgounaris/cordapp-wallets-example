package com.template

import com.template.flows.WalletCreateFlow
import com.template.flows.WalletDeleteFlow
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class WalletDeleteFlowSpec: WalletFlowSpec() {

    @Test
    fun `deleting a wallet should happen successfully if there is a wallet with zero amount of this currency`() {
        val createFlow = WalletCreateFlow("0 EUR")
        partyA.startFlow(createFlow).get()
        val deleteFlow = WalletDeleteFlow("EUR")
        assertDoesNotThrow { partyA.startFlow(deleteFlow).getOrThrow() }
    }

    @Test
    fun `deleting a wallet should throw if there is a wallet with non zero amount of this currency`() {
        val createFlow = WalletCreateFlow("10 EUR")
        partyA.startFlow(createFlow).get()
        val deleteFlow = WalletDeleteFlow("EUR")
        assertThrows<FlowException> { partyA.startFlow(deleteFlow).getOrThrow() }
    }

}