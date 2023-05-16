/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.state.migration;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;

import java.util.Map;

public interface HederaAccount {
    long getNftsOwned();

    void setNftsOwned(long nftsOwned);

    int getNumTreasuryTitles();

    void setNumTreasuryTitles(int treasuryTitles);

    String getMemo();

    void setMemo(String memo);

    ByteString getAlias();

    void setAlias(ByteString alias);

    int getNumAssociations();

    void setNumAssociations(int numAssociations);

    int getNumPositiveBalances();

    void setNumPositiveBalances(int numPositiveBalances);

    long getBalance();

    void setBalance(long balance) throws NegativeAccountBalanceException;

    boolean isDeleted();

    void setDeleted(boolean deleted);

    long getExpiry();

    void setExpiry(long expiry);

    int getUsedAutoAssociations();

    void setUsedAutomaticAssociations(int usedAutoAssociations);

    Map<EntityNum, Long> getCryptoAllowancesUnsafe();

    void setCryptoAllowancesUnsafe(Map<EntityNum, Long> cryptoAllowances);

    Map<FcTokenAllowanceId, Long> getFungibleTokenAllowancesUnsafe();

    void setFungibleTokenAllowancesUnsafe(Map<FcTokenAllowanceId, Long> fungibleTokenAllowances);

    EntityId getAutoRenewAccount();

    void setAutoRenewAccount(EntityId autoRenewAccount);
}
