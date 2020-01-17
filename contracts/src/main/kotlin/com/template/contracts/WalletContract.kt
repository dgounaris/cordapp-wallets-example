package com.template.contracts

import com.template.states.WalletState
import net.corda.core.contracts.*
import net.corda.core.internal.sumByLong
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.util.*

class WalletContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.WalletContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<WalletCommands>()
        when (command.value) {
            is WalletCommands.Create -> validateCreate(tx, command)
            is WalletCommands.Transfer -> validateTransfer(tx, command)
            is WalletCommands.Delete -> validateDelete(tx, command)
        }
    }

    private fun validateCreate(tx: LedgerTransaction, command: CommandWithParties<WalletCommands>) {
        requireThat {
            "There is no input" using tx.inputs.isEmpty()
            "There is one output" using (tx.outputs.size == 1)

            val output = tx.outputsOfType<WalletState>().single()
            "The output's amount should be 0 or bigger" using (output.amount.quantity >= 0L)

            val expectedSigners = setOf(output.owner.owningKey)
            validateSignatures(command.signers, expectedSigners)
        }
    }

    private fun validateTransfer(tx: LedgerTransaction, command: CommandWithParties<WalletCommands>) {
        requireThat {
            "There must only be WalletState states in this transaction" using (
                        tx.inputsOfType<WalletState>().size == tx.inputs.size &&
                        tx.outputsOfType<WalletState>().size == tx.outputs.size
                    )

            val groups = tx.groupStates<WalletState, Currency> { it.amount.token }
            var expectedSigners = emptySet<PublicKey>()

            for ((inputs, outputs, currency) in groups) {
                "There can't be empty inputs for an existing tx currency" using (inputs.isNotEmpty())
                "There can't be empty inputs for an existing tx currency" using (outputs.isNotEmpty())

                "The input total amounts have to be equal with the output total amounts for currency $currency" using
                        (inputs.sumByLong { it.amount.quantity } == outputs.sumByLong { it.amount.quantity })

                "Every number in the input and the output must be positive" using (
                            (inputs + outputs).all { it.amount.quantity >= 0L }
                        )

                "The input wallet owners must be the same as the output wallet owners for currency $currency" using
                        (
                            inputs.map { it.owner }.containsAll(outputs.map { it.owner }) &&
                            outputs.map { it.owner }.containsAll(inputs.map { it.owner })
                        )

                expectedSigners = expectedSigners.plus(
                        (inputs + outputs).map { it.owner.owningKey }
                )
            }
        }
    }

    private fun validateDelete(tx: LedgerTransaction, command: CommandWithParties<WalletCommands>) {
        requireThat {
            "There is one input" using (tx.inputs.size == 1)
            "There is no output" using (tx.outputs.isEmpty())

            val input = tx.inputsOfType<WalletState>().single()
            "There must be no amount in this wallet to be deleted" using (input.amount.quantity == 0L)

            val expectedSigners = setOf(input.owner.owningKey)
            validateSignatures(command.signers, expectedSigners)
        }
    }

    private fun validateSignatures(signers: List<PublicKey>, expectedSigners: Set<PublicKey>) {
        requireThat {
            "Must be signed by ${expectedSigners.size} parties" using (signers.size == expectedSigners.size)
            "Must be signed by the expected parties" using (signers.containsAll(expectedSigners))
        }
    }

    // Used to indicate the transaction's intent.
    interface WalletCommands : CommandData {
        class Create : WalletCommands
        class Transfer : WalletCommands
        class Delete : WalletCommands
    }
}