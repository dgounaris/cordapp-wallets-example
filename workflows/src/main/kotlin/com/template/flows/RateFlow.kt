package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.oracles.Rate
import com.template.oracles.RateOf
import net.corda.core.contracts.Command
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import org.bouncycastle.crypto.tls.ContentType.handshake
import java.util.function.Predicate

class RateFlow(
        private val tx: TransactionBuilder,
        private val oracle: Party,
        private val rateOf: RateOf
): FlowLogic<TransactionSignature>() {

    @Suspendable
    override fun call(): TransactionSignature {
        val fix = subFlow(RateQueryFlow(rateOf, oracle))
        tx.addCommand(fix, oracle.owningKey)
        val mtx = tx.toWireTransaction(serviceHub).buildFilteredTransaction(Predicate { filtering(it) })
        return subFlow(RateSignFlow(tx, oracle, mtx))
    }

    @Suspendable
    fun filtering(elem: Any): Boolean {
        return when (elem) {
            // Only expose Fix commands in which the oracle is on the list of requested signers
            // to the oracle node, to avoid leaking privacy
            is Command<*> -> oracle.owningKey in elem.signers && elem.value is Rate
            else -> false
        }
    }

    @CordaSerializable
    data class RateQuery(val query: RateOf)

    @CordaSerializable
    data class SignRequest(val ftx: FilteredTransaction)

    @InitiatingFlow
    class RateQueryFlow(val rateOf: RateOf, val oracle: Party): FlowLogic<Rate>() {
        @Suspendable
        override fun call(): Rate {
            val oracleSession = initiateFlow(oracle)
            val resp = oracleSession.sendAndReceive<Rate>(RateQuery(rateOf))
            return resp.unwrap {
                check(it.currencyFrom == rateOf.currencyFrom)
                check(it.currencyTo == rateOf.currencyTo)
                return@unwrap it
            }
        }
    }

    @InitiatingFlow
    class RateSignFlow(val txb: TransactionBuilder, val oracle: Party, val ftx: FilteredTransaction): FlowLogic<TransactionSignature>() {
        @Suspendable
        override fun call(): TransactionSignature {
            val oracleSession = initiateFlow(oracle)
            val resp = oracleSession.sendAndReceive<TransactionSignature>(SignRequest(ftx))
            return resp.unwrap { sig ->
                check(oracleSession.counterparty.owningKey.isFulfilledBy(listOf(sig.by)))
                txb.toWireTransaction(serviceHub).checkSignature(sig)
                return@unwrap sig
            }
        }
    }

}