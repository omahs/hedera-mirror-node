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

package com.hedera.services.store.tokens;

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

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

/** Provides a managing store for arbitrary tokens. */
public class HederaTokenStore {
    static final TokenID NO_PENDING_ID = TokenID.getDefaultInstance();
    TokenID MISSING_TOKEN = TokenID.getDefaultInstance();

    private static final Predicate<Key> REMOVES_ADMIN_KEY = ImmutableKeyUtils::signalsKeyRemoval;

    private final UsageLimits usageLimits;
    private final OptionValidator validator;
    private final GlobalDynamicProperties properties;
    private final SideEffectsTracker sideEffectsTracker;
    private final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRelsLedger;
    private final BackingStore<TokenID, MerkleToken> backingTokens;

    TokenID pendingId = NO_PENDING_ID;
    MerkleToken pendingCreation;

    @Inject
    public HederaTokenStore(
            final EntityIdSource ids,
            final UsageLimits usageLimits,
            final OptionValidator validator,
            final SideEffectsTracker sideEffectsTracker,
            final GlobalDynamicProperties properties,
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRelsLedger,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final BackingStore<TokenID, MerkleToken> backingTokens) {
        super(ids);
        this.validator = validator;
        this.properties = properties;
        this.nftsLedger = nftsLedger;
        this.usageLimits = usageLimits;
        this.backingTokens = backingTokens;
        this.tokenRelsLedger = tokenRelsLedger;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    private ResponseCodeEnum sanityCheckedFungibleCommon(
            final AccountID aId, final TokenID tId, final Function<MerkleToken, ResponseCodeEnum> action) {
        return sanityChecked(true, aId, null, tId, action);
    }

    public ResponseCodeEnum adjustBalance(final AccountID aId, final TokenID tId, final long adjustment) {
        return sanityCheckedFungibleCommon(aId, tId, token -> tryAdjustment(aId, tId, adjustment));
    }

    public boolean matchesTokenDecimals(final TokenID tId, final int expectedDecimals) {
        return get(tId).decimals() == expectedDecimals;
    }

    private ResponseCodeEnum validateAndAutoAssociate(AccountID aId, TokenID tId) {
        if ((int) accountsLedger.get(aId, MAX_AUTOMATIC_ASSOCIATIONS) > 0) {
            return autoAssociate(aId, tId);
        }
        return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
    }

    protected ResponseCodeEnum checkAccountUsability(final AccountID aId) {
        var accountDoesNotExist = !accountsLedger.exists(aId);
        if (accountDoesNotExist) {
            return INVALID_ACCOUNT_ID;
        }

        var deleted = (boolean) accountsLedger.get(aId, IS_DELETED);
        if (deleted) {
            return ACCOUNT_DELETED;
        }
        return validator.expiryStatusGiven(accountsLedger, aId);
    }

    private ResponseCodeEnum sanityChecked(
            final boolean onlyFungibleCommon,
            final AccountID aId,
            final AccountID aCounterPartyId,
            final TokenID tId,
            final Function<MerkleToken, ResponseCodeEnum> action) {
        var validity = checkAccountUsability(aId);
        if (validity != OK) {
            return validity;
        }
        if (aCounterPartyId != null) {
            validity = checkAccountUsability(aCounterPartyId);
            if (validity != OK) {
                return validity;
            }
        }

        validity = checkTokenExistence(tId);
        if (validity != OK) {
            return validity;
        }

        final var token = get(tId);
        if (token.isDeleted()) {
            return TOKEN_WAS_DELETED;
        }
        if (token.isPaused()) {
            return TOKEN_IS_PAUSED;
        }
        if (onlyFungibleCommon && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
            return ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
        }

        var key = asTokenRel(aId, tId);
        /*
         * Instead of returning  TOKEN_NOT_ASSOCIATED_TO_ACCOUNT when a token is not associated,
         * we check if the account has any maxAutoAssociations set up, if they do check if we reached the limit and
         * auto associate. If not return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT
         */
        if (!tokenRelsLedger.exists(key)) {
            validity = validateAndAutoAssociate(aId, tId);
            if (validity != OK) {
                return validity;
            }
        }
        if (aCounterPartyId != null) {
            key = asTokenRel(aCounterPartyId, tId);
            if (!tokenRelsLedger.exists(key)) {
                validity = validateAndAutoAssociate(aCounterPartyId, tId);
                if (validity != OK) {
                    return validity;
                }
            }
        }

        return action.apply(token);
    }

    private ResponseCodeEnum checkRelFrozenAndKycProps(final AccountID aId, final TokenID tId) {
        final var relationship = asTokenRel(aId, tId);
        if ((boolean) tokenRelsLedger.get(relationship, IS_FROZEN)) {
            return ACCOUNT_FROZEN_FOR_TOKEN;
        }
        if (!(boolean) tokenRelsLedger.get(relationship, IS_KYC_GRANTED)) {
            return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
        }
        return OK;
    }

    public ResponseCodeEnum changeOwner(final NftId nftId, final AccountID from, final AccountID to) {
        final var tId = nftId.tokenId();
        return sanityChecked(false, from, to, tId, token -> {
            if (!nftsLedger.exists(nftId)) {
                return INVALID_NFT_ID;
            }

            final var fromFreezeAndKycValidity = checkRelFrozenAndKycProps(from, tId);
            if (fromFreezeAndKycValidity != OK) {
                return fromFreezeAndKycValidity;
            }
            final var toFreezeAndKycValidity = checkRelFrozenAndKycProps(to, tId);
            if (toFreezeAndKycValidity != OK) {
                return toFreezeAndKycValidity;
            }

            final var tid = nftId.tokenId();
            final var tokenTreasury = backingTokens.getImmutableRef(tid).treasury();
            var owner = (EntityId) nftsLedger.get(nftId, OWNER);
            if (owner.equals(EntityId.MISSING_ENTITY_ID)) {
                owner = tokenTreasury;
            }
            if (!owner.matches(from)) {
                return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            }

            updateLedgers(nftId, from, to, tokenTreasury.toGrpcAccountId());
            return OK;
        });
    }

    public boolean isCreationPending() {
        return pendingId != NO_PENDING_ID;
    }

    public boolean exists(final TokenID id) {
        return (isCreationPending() && pendingId.equals(id)) || backingTokens.contains(id);
    }

    public ResponseCodeEnum tryTokenChange(BalanceChange change) {
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

    TokenID resolve(TokenID id) {
        return exists(id) ? id : MISSING_TOKEN;
    }

    private ResponseCodeEnum checkTokenExistence(final TokenID tId) {
        return exists(tId) ? OK : INVALID_TOKEN_ID;
    }
}
