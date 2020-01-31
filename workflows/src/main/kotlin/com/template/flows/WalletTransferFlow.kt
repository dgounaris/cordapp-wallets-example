package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.WalletContract
import com.template.oracles.RateOf
import com.template.oracles.RateOracle
import com.template.states.WalletState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*
import javax.swing.plaf.nimbus.State

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

        // fixme as a PoC for now we just get the rate between eur and usd
        val rate = subFlow(RateFlow.RateQueryFlow(
                RateOf("EUR", "USD"),
                serviceHub.identityService.wellKnownPartyFromX500Name(CordaX500Name.parse("O=RateOracle,L=New York,C=US"))!!
        ))

        progressTracker.currentStep = CREATING_TX
        val txBuilder = buildVerifyTransaction(
                notary,
                listOf(inputState, counterPartyInput),
                listOf(outputState, counterPartyOutput),
                listOf(ourIdentity.owningKey, counterPartyInput.state.data.owner.owningKey)
        )
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = SIGNATURES_WAIT
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(counterPartySession), CollectSignaturesFlow.tracker()))
        progressTracker.currentStep = FINALIZING
        subFlow(FinalityFlow(fullySignedTx, counterPartySession))
    }

    private fun buildVerifyTransaction(notary: Party, inputs: List<StateAndRef<WalletState>>, outputs: List<WalletState>, signingKeys: List<PublicKey>): TransactionBuilder {
        val command = Command(
                WalletContract.WalletCommands.Transfer(),
                signingKeys
        )
        val txBuilder = TransactionBuilder(notary)
                .withItems(*(inputs.toTypedArray()))
                .withItems(*(outputs.map { StateAndContract(it, WalletContract.ID) }.toTypedArray()))
                .addCommand(command)
        txBuilder.verify(serviceHub)
        return txBuilder
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