package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.WalletContract
import com.template.states.WalletState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class WalletCreateFlow(
        val amount: String
): WalletIdentifiableFlow() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val parsedAmount = Amount.parseCurrency(amount)
        // the below is not thread safe due to toctou, not a concern for now though
        // TODO possibly we can initialize all available currencies for all nodes, with a "null" state
        //      then the notary would be responsible to take care of duplicates
        if (currencyWalletExists(parsedAmount.token.currencyCode)) {
            throw FlowException("There can be only one wallet per currency")
        }
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val outputState = WalletState(ourIdentity, parsedAmount)
        val command = Command(WalletContract.WalletCommands.Create(), ourIdentity.owningKey)
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(outputState, WalletContract.ID)
                .addCommand(command)
        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        subFlow(FinalityFlow(signedTx, emptyList()))
    }

    private fun currencyWalletExists(currency: String): Boolean {
        try {
            getWalletByCurrency(currency)
        } catch (ex: FlowException) {
            return false
        }
        return true
    }
}