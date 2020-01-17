package com.template.flows

import com.template.states.WalletState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.*

abstract class WalletIdentifiableFlow: FlowLogic<Unit>() {
    protected fun getWalletByCurrency(currency: String): StateAndRef<WalletState> {
        val query = QueryCriteria.VaultQueryCriteria(
                participants = listOf(ourIdentity)
        )
        // NOTE: supports only one unconsumed (output) wallet per currency per person
        return serviceHub.vaultService
                .queryBy<WalletState>(query).states.firstOrNull {
                    stateAndRef -> stateAndRef.state.data.currency == currency
                }
                ?: throw FlowException("Personal wallet with currency $currency does not exist")
    }
}