package com.template.oracles

import co.paralleluniverse.fibers.Suspendable
import com.template.flows.RateFlow
import com.template.flows.WalletTransferFromFlow
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.internal.ThreadBox
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap
import java.math.BigDecimal

@CordaService
class RateOracle(private val services: AppServiceHub) : SingletonSerializeAsToken() {
    private val mutex = ThreadBox(InnerState())

    init {
        addDefaultRates()
    }

    private class InnerState {
        val rates = HashSet<Rate>()
    }

    var knownRates: HashSet<Rate>
        set(value) {
            require(value.size > 0)
            mutex.locked {
                rates.clear()
                rates.addAll(value)
            }
        }
        get() = mutex.locked { rates.toHashSet() }

    private fun addDefaultRates() {
        knownRates = hashSetOf(
                Rate("EUR", "GBP", BigDecimal("0.8")),
                Rate("GBP", "EUR", BigDecimal("1.2")),
                Rate("EUR", "USD", BigDecimal("1.5")),
                Rate("USD", "EUR", BigDecimal("0.5"))
        )
    }

    @Suspendable
    fun query(rateOf: RateOf): Rate {
        return knownRates.first { it.currencyFrom == rateOf.currencyFrom && it.currencyTo == rateOf.currencyTo }
    }

    fun sign(ftx: FilteredTransaction): TransactionSignature {
        ftx.verify()

        fun commandValidator(elem: Command<*>): Boolean {
            require(services.myInfo.legalIdentities.first().owningKey in elem.signers && elem.value is Rate) {
                "Oracle received unknown command (not in signers or not Rate)"
            }
            val rate = elem.value as Rate
            return true
        }

        fun check(elem: Any): Boolean {
            return when (elem) {
                is Command<*> -> commandValidator(elem)
                else -> throw IllegalArgumentException("Oracle received data of different type than expected")
            }
        }

        require(ftx.checkWithFun { check(it) })
        ftx.checkCommandVisibility(services.myInfo.legalIdentities.first().owningKey)
        return services.createSignature(ftx, services.myInfo.legalIdentities.first().owningKey)
    }
}

@InitiatedBy(RateFlow.RateSignFlow::class)
class RateSignHandler(private val otherPartySession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherPartySession.receive<RateFlow.SignRequest>().unwrap { it }
        val oracle = serviceHub.cordaService(RateOracle::class.java)
        otherPartySession.send(oracle.sign(request.ftx))
    }
}

@InitiatedBy(RateFlow.RateQueryFlow::class)

class RateQueryHandler(private val otherPartySession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherPartySession.receive<RateFlow.RateQuery>().unwrap { it }
        val oracle = serviceHub.cordaService(RateOracle::class.java)
        val answer = oracle.query(request.query)
        otherPartySession.send(answer)
    }
}