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

package com.hedera.mirror.web3.evm.store.contract.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.BodyParams;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Named
public class AssociatePrecompile extends AbstractAssociatePrecompile {
    private static final Function ASSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)", INT);
    private static final Bytes ASSOCIATE_TOKEN_SELECTOR = Bytes.wrap(ASSOCIATE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> ASSOCIATE_TOKEN_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public AssociatePrecompile(
            final PrecompilePricingUtils pricingUtils, final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        super(pricingUtils, mirrorNodeEvmProperties);
    }

    public static Association decodeAssociation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKEN_SELECTOR, ASSOCIATE_TOKEN_DECODER);

        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(1));

        return Association.singleAssociation(accountID, tokenID);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        TokenID tokenId = null;
        Address callerAccountAddress;
        AccountID callerAccountID = null;

        if (bodyParams instanceof HrcParams hrcParams) {
            tokenId = hrcParams.token();
            callerAccountAddress = hrcParams.senderAddress();
            callerAccountID =
                    EntityIdUtils.accountIdFromEvmAddress(Objects.requireNonNull(callerAccountAddress.toArray()));
        }

        final var associateOp = tokenId == null
                ? decodeAssociation(input, aliasResolver)
                : Association.singleAssociation(Objects.requireNonNull(callerAccountID), tokenId);

        return createAssociate(associateOp);
    }

    // TODO fix pricing logic to include transactionBody into account
    @Override
    public long getGasRequirement(final long blockTimestamp, final StackedStateFrames<Object> stackedStateFrames) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, stackedStateFrames);
    }

    private TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }
}
