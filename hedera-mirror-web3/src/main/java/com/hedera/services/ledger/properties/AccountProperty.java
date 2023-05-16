/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.ledger.properties;

import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implements a property family whose instances can provide the getter/setter pairs relevant to
 * themselves on a {@link MerkleAccount} object.
 */
@SuppressWarnings("unchecked")
public enum AccountProperty implements BeanProperty<HederaAccount> {
    BALANCE {
        @Override
        @SuppressWarnings("unchecked")
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, v) -> {
                try {
                    a.setBalance(((Number) v).longValue());
                } catch (ClassCastException cce) {
                    throw new IllegalArgumentException(
                            "Wrong argument type! Argument needs to be of type int or long. Actual" + " value: " + v,
                            cce);
                } catch (NegativeAccountBalanceException nabe) {
                    throw new IllegalArgumentException(
                            "Argument 'v=" + v + "' would cause account 'a=" + a + "' to have a negative balance!",
                            nabe);
                }
            };
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getBalance;
        }
    },
    NUM_NFTS_OWNED {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, n) -> a.setNftsOwned((long) n);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNftsOwned;
        }
    },
    USED_AUTOMATIC_ASSOCIATIONS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setUsedAutomaticAssociations((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getUsedAutoAssociations;
        }
    },
    CRYPTO_ALLOWANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setCryptoAllowancesUnsafe((Map<EntityNum, Long>) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getCryptoAllowancesUnsafe;
        }
    },
    FUNGIBLE_TOKEN_ALLOWANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setFungibleTokenAllowancesUnsafe((Map<FcTokenAllowanceId, Long>) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getFungibleTokenAllowancesUnsafe;
        }
    },
    NUM_ASSOCIATIONS {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setNumAssociations((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumAssociations;
        }
    },
    NUM_POSITIVE_BALANCES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setNumPositiveBalances((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumPositiveBalances;
        }
    },
    NUM_TREASURY_TITLES {
        @Override
        public BiConsumer<HederaAccount, Object> setter() {
            return (a, t) -> a.setNumTreasuryTitles((int) t);
        }

        @Override
        public Function<HederaAccount, Object> getter() {
            return HederaAccount::getNumTreasuryTitles;
        }
    },
}
