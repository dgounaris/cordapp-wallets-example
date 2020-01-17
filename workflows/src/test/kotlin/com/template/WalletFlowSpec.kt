package com.template

import com.template.flows.WalletTransferToFlow
import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before

abstract class WalletFlowSpec {
    lateinit var network: MockNetwork
    lateinit var partyA: StartedMockNode
    lateinit var partyB: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.template.contracts"),
                        TestCordapp.findCordapp("com.template.flows")
                ),
                threadPerNode = true
        ))
        partyA = network.createPartyNode(CordaX500Name("partyA", "London", "GB"))
        partyB = network.createPartyNode(CordaX500Name("partyA", "London", "GB"))

        listOf(partyA,partyB).forEach {
            it.registerInitiatedFlow(WalletTransferToFlow::class.java)
        }
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }
}