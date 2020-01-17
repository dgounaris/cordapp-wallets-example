package com.template

import com.template.flows.WalletCreateFlow
import com.template.flows.WalletDeleteFlow
import com.template.flows.WalletTransferFromFlow
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class WalletTransferFlowSpec: WalletFlowSpec() {

    // TODO does not work (waits forever), no idea why
    /*@Test
    fun `transfering should happen if the amount is within wallet amount and the other party has a relevant wallet`() {
        val createFlowA = WalletCreateFlow("10 EUR")
        val createFlowB = WalletCreateFlow("0 EUR")
        partyA.startFlow(createFlowA).get()
        partyB.startFlow(createFlowB).get()
        val transferFlow = WalletTransferFromFlow(partyB.info.legalIdentities[0], "5 EUR")
        assertDoesNotThrow { partyA.startFlow(transferFlow).getOrThrow() }
    }*/
}