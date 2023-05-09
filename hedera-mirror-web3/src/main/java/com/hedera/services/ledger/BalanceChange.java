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

import static com.hedera.services.utils.EntityIdUtils.isAlias;

import com.google.protobuf.ByteString;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

public class BalanceChange {

    static final TokenID NO_TOKEN_FOR_HBAR_ADJUST = TokenID.getDefaultInstance();

    private long newBalance;
    private int expectedDecimals = -1;
    private Id token;
    private TokenID tokenId = null;
    private NftId nftId = null;
    private Account account;
    private AccountID accountId;
    private ByteString alias;
    private long allowanceUnits;
    private long aggregatedUnits;
    private ByteString counterPartyAlias;
    private AccountID payerID = null;
    private AccountID counterPartyAccountId = null;

    private ResponseCodeEnum codeForInsufficientBalance;

    public void setNewBalance(final long newBalance) {
        this.newBalance = newBalance;
    }

    public long getNewBalance() {
        return newBalance;
    }

    public void setExpectedDecimals(final int expectedDecimals) {
        this.expectedDecimals = expectedDecimals;
    }

    public boolean isForToken() {
        return isForFungibleToken() || isForNft();
    }

    public int getExpectedDecimals() {
        return expectedDecimals;
    }

    public Id getToken() {
        return token;
    }

    public Account account() {
        return account;
    }

    public boolean affectsAccount(final AccountID accountId) {
        return accountId.equals(this.account);
    }

    public boolean hasAlias() {
        return isAlias(accountId) || hasNonEmptyCounterPartyAlias();
    }

    public void replaceNonEmptyAliasWith(final EntityNum createdId) {
        if (isAlias(accountId)) {
            accountId = createdId.toGrpcAccountId();
            account = Id.fromGrpcAccount(accountId);
        } else if (hasNonEmptyCounterPartyAlias()) {
            counterPartyAccountId = createdId.toGrpcAccountId();
        }
    }

    public NftId nftId() {
        return nftId;
    }

    public TokenID tokenId() {
        return (tokenId != null) ? tokenId : NO_TOKEN_FOR_HBAR_ADJUST;
    }

    public AccountID accountId() {
        return accountId;
    }

    public AccountID counterPartyAccountId() {
        return counterPartyAccountId;
    }

    public long getAggregatedUnits() {
        return this.aggregatedUnits;
    }

    public ResponseCodeEnum codeForInsufficientBalance() {
        return codeForInsufficientBalance;
    }

    /**
     * allowanceUnits are always non-positive. If negative that accountId has some allowanceUnits to
     * be taken off from its allowanceMap with the respective payer. It will be -1 for nft ownership
     * changes.
     *
     * @return true if negative allowanceUnits
     */
    public boolean isApprovedAllowance() {
        return this.allowanceUnits < 0;
    }

    public long getAllowanceUnits() {
        return this.allowanceUnits;
    }

    /**
     * Since a change can have either an unknown alias or a counterPartyAlias (but not both),
     * returns any non-empty unknown alias in the change.
     *
     * @return non-empty alias
     */
    public ByteString getNonEmptyAliasIfPresent() {
        if (isAlias(accountId)) return alias;
        else if (hasNonEmptyCounterPartyAlias()) return counterPartyAlias;
        else return null;
    }

    public boolean hasNonEmptyCounterPartyAlias() {
        return counterPartyAccountId != null && isAlias(counterPartyAccountId);
    }

    public AccountID getPayerID() {
        return payerID;
    }

    public boolean hasExpectedDecimals() {
        return expectedDecimals != -1;
    }

    public boolean isForNft() {
        return token != null && counterPartyAccountId != null;
    }

    public boolean isForFungibleToken() {
        return token != null && counterPartyAccountId == null;
    }

    public boolean isForHbar() {
        return token == null;
    }
}
