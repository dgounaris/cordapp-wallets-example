package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.WalletContract
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class WalletDeleteFlow(
        private val currency: String
): WalletIdentifiableFlow() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val inputState = getWalletByCurrency(currency)
        val command = Command(WalletContract.WalletCommands.Delete(), ourIdentity.owningKey)
        val txBuilder = TransactionBuilder(notary)
                .addInputState(inputState)
                .addCommand(command)
        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        subFlow(FinalityFlow(signedTx, emptyList()))
    }
}