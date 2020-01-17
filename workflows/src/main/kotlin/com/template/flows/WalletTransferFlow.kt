package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.WalletContract
import com.template.states.WalletState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.util.*

@Suppress("UNCHECKED_CAST")
@InitiatingFlow
@StartableByRPC
class WalletTransferFromFlow(
        private val otherParty: Party,
        private val amountGiven: String
): WalletIdentifiableFlow() {
    companion object {
        val START = ProgressTracker.Step("Starting")
        val PARSED_INPUTS = ProgressTracker.Step("Parsed inputs")
        val CREATED_KNOWN_INOUTS = ProgressTracker.Step("Generated known inputs and outputs")
        val CREATED_COUNTERPARTIES_INOUTS = ProgressTracker.Step("Generated external inputs and outputs")
        val CREATING_TX = ProgressTracker.Step("Creating and signing tx")
        val SIGNATURES_WAIT = ProgressTracker.Step("Waiting for counterparties to sign")
        val FINALIZING = ProgressTracker.Step("Finalizing")
    }
    override val progressTracker = ProgressTracker(
            START,
            PARSED_INPUTS,
            CREATED_KNOWN_INOUTS,
            CREATED_COUNTERPARTIES_INOUTS,
            CREATING_TX,
            SIGNATURES_WAIT,
            FINALIZING
    )

    @Suspendable
    override fun call() {
        progressTracker.currentStep = START
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        progressTracker.currentStep = PARSED_INPUTS
        val parsedAmountGiven = Amount.parseCurrency(amountGiven)
        val currencyCode = parsedAmountGiven.token.currencyCode

        progressTracker.currentStep = CREATED_KNOWN_INOUTS
        val inputState = getWalletByCurrency(currencyCode)
        val myFinalAmount = Amount(
                inputState.state.data.amount.quantity - parsedAmountGiven.quantity,
                Currency.getInstance(currencyCode)
        )
        val outputState = WalletState(ourIdentity, myFinalAmount)

        progressTracker.currentStep = CREATED_COUNTERPARTIES_INOUTS
        val counterPartySession = initiateFlow(otherParty)
        val counterPartyInput = retrieveCounterPartyWalletInfo(counterPartySession, parsedAmountGiven)
        val counterPartyOutput = WalletState(
                counterPartyInput.state.data.owner,
                Amount(
                        counterPartyInput.state.data.amount.quantity + parsedAmountGiven.quantity,
                        parsedAmountGiven.token
                )
        )

        progressTracker.currentStep = CREATING_TX
        val command = Command(
                WalletContract.WalletCommands.Transfer(),
                listOf(ourIdentity.owningKey, counterPartyInput.state.data.owner.owningKey)
        )
        val txBuilder = TransactionBuilder(notary)
                .addInputState(inputState)
                .addInputState(counterPartyInput)
                .addOutputState(outputState, WalletContract.ID)
                .addOutputState(counterPartyOutput, WalletContract.ID, notary)
                .addCommand(command)
        txBuilder.verify(serviceHub)

        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = SIGNATURES_WAIT
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(counterPartySession), CollectSignaturesFlow.tracker()))

        progressTracker.currentStep = FINALIZING
        subFlow(FinalityFlow(fullySignedTx, counterPartySession))
    }

    @Suspendable
    private fun retrieveCounterPartyWalletInfo(counterPartySession: FlowSession, parsedAmountGiven: Amount<Currency>): StateAndRef<WalletState> {
        counterPartySession.send(parsedAmountGiven)
        return subFlow(ReceiveStateAndRefFlow<WalletState>(counterPartySession)).single()
    }
}

@InitiatedBy(WalletTransferFromFlow::class)
class WalletTransferToFlow(
        private val otherPartySession: FlowSession
): WalletIdentifiableFlow() {
    @Suspendable
    override fun call() {
        val amountGivenWrapped: UntrustworthyData<Amount<Currency>> = otherPartySession.receive()
        val amountGivenUnwrapped: Amount<Currency> = amountGivenWrapped.unwrap { it }
        val myInputWallet = getWalletByCurrency(amountGivenUnwrapped.token.currencyCode)
        subFlow(SendStateAndRefFlow(otherPartySession, listOf(myInputWallet)))

        // TODO we don't need to see the other party's i/o, contract will validate all of that anyway
        //      see https://docs.corda.net/releases/release-M9.2/tutorial-building-transactions.html#partially-visible-transactions
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {
                requireThat {
                    val ledgerTransaction = stx.toLedgerTransaction(serviceHub, false)
                    val inputs = ledgerTransaction.inputsOfType<WalletState>()
                    val outputs = ledgerTransaction.outputsOfType<WalletState>()
                    "The receivers wallets have to have a bigger amount than before" using (
                            (inputs + outputs).filter { it.owner == ourIdentity }
                                    .groupBy { it.currency } // keeps the input-output ordered at indexes [0,1]
                                    .all { it.value[0].amount.quantity < it.value[1].amount.quantity }
                            )
                }
            }
        }

        val expectedTxId = subFlow(signTransactionFlow).id
        subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId))
    }
}