/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.ledger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;

import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TransferLogic {

    private final StackedStateFrames stackedStateFrames;
    private final TransactionContext txnCtx;

    public TransferLogic(StackedStateFrames stackedStateFrames, TransactionContext txnCtx) {
        this.stackedStateFrames = stackedStateFrames;
        this.txnCtx = txnCtx;
    }

    public void doZeroSum(final List<BalanceChange> changes) {
        final var topLevelPayer = txnCtx.activePayer();
        var validity = OK;
        var updatedPayerBalance = Long.MIN_VALUE;

        for (final var change : changes) {

            if (change.isForHbar()) {

            } else {
                if (validity == OK) {
                    validity = tryTokenChange(change);
                }
            }
        }

        if (validity == OK) {
            adjustBalancesAndAllowances(changes);
        } else {
            throw new InvalidTransactionException(validity);
        }
    }

    public ResponseCodeEnum validate() {}

    ResponseCodeEnum tryTokenChange(BalanceChange change) {
        var validity = OK;
        var tokenId = resolve(change.tokenId());
        if (tokenId == MISSING_TOKEN) {
            validity = INVALID_TOKEN_ID;
        }
        if (change.hasExpectedDecimals() && !matchesTokenDecimals(change.tokenId(), change.getExpectedDecimals())) {
            validity = UNEXPECTED_TOKEN_DECIMALS;
        }
        if (validity == OK) {
            if (change.isForNft()) {
                validity = changeOwner(change.nftId(), change.accountId(), change.counterPartyAccountId());
            } else {
                validity = adjustBalance(change.accountId(), tokenId, change.getAggregatedUnits());
                if (validity == INSUFFICIENT_TOKEN_BALANCE) {
                    validity = change.codeForInsufficientBalance();
                }
            }
        }
        return validity;
    }

    private void adjustBalancesAndAllowances(final List<BalanceChange> changes) {
        for (final var change : changes) {

            final var topFrame = stackedStateFrames.top();
            final var accountAccessor = topFrame.getAccessor(Account.class);
            final var tokenAccessor = topFrame.getAccessor(UniqueToken.class);

            final var account = change.account();
            if (change.isForHbar()) {
                final var newBalance = change.getNewBalance();
                var updatedAccount = account.setBalance(newBalance);
                accountAccessor.set(updatedAccount.getAccountAddress(), updatedAccount);
                if (change.isApprovedAllowance()) {
                    adjustCryptoAllowance(change, accountId);
                }
                topFrame.commit();
            } else if (change.isApprovedAllowance() && change.isForFungibleToken()) {
                adjustFungibleTokenAllowance(change, accountId);
            } else if (change.isForNft()) {
                var nftId = change.getToken();
                var nft = tokenAccessor.get(nftId);

                // wipe the allowance on this uniqueToken
                nftsLedger.set(change.nftId(), SPENDER, MISSING_ENTITY_ID);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void adjustCryptoAllowance(final BalanceChange change, final AccountID ownerID) {
        final var payerNum = EntityNum.fromAccountId(change.getPayerID());
        final var hbarAllowances = new TreeMap<>((Map<EntityNum, Long>) accountsLedger.get(ownerID, CRYPTO_ALLOWANCES));
        final var currentAllowance = hbarAllowances.get(payerNum);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance != 0) {
            hbarAllowances.put(payerNum, newAllowance);
        } else {
            hbarAllowances.remove(payerNum);
        }
        accountsLedger.set(ownerID, CRYPTO_ALLOWANCES, hbarAllowances);
    }

    @SuppressWarnings("unchecked")
    private void adjustFungibleTokenAllowance(final BalanceChange change, final AccountID ownerID) {
        final var topFrame = stackedStateFrames.top();
        final var allowanceAccessor = topFrame.getAccessor(FcTokenAllowanceId.class);

        final var allowanceId =
                FcTokenAllowanceId.from(change.getToken().asEntityNum(), EntityNum.fromAccountId(change.getPayerID()));
        final var fungibleAllowances = new TreeMap<>(
                (Map<FcTokenAllowanceId, Long>) allowanceAccessor.get(ownerID).get());
        final var currentAllowance = fungibleAllowances.get(allowanceId);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance == 0) {
            fungibleAllowances.remove(allowanceId);
        } else {
            fungibleAllowances.put(allowanceId, newAllowance);
        }
        accountsLedger.set(ownerID, FUNGIBLE_TOKEN_ALLOWANCES, fungibleAllowances);
    }
}
