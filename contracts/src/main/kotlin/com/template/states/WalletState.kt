package com.template.states

import com.template.contracts.WalletContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import java.util.*

@BelongsToContract(WalletContract::class)
class WalletState(val owner: Party,
                  val amount: Amount<Currency>,
                  val currency: String = amount.token.currencyCode) : ContractState {
    override val participants get() = listOf(owner)
}