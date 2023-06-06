/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.syntheticlog.contractresult;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import lombok.Data;
import org.apache.tuweni.bytes.Bytes;

@Data
public abstract class AbstractSyntheticContractResult implements SyntheticContractResult {
    private final RecordItem recordItem;
    private final EntityId entityId;
    private final EntityId senderId;
    private final byte[] functionParameters;

    AbstractSyntheticContractResult(
            RecordItem recordItem, EntityId entityId, EntityId senderId, byte[] functionParameters) {
        this.recordItem = recordItem;
        this.entityId = entityId;
        this.senderId = senderId;
        this.functionParameters = functionParameters;
    }

    static final String TRANSFER_SIGNATURE = "a9059cbb";

    static final String APPROVE_FOR_ALL_SIGNATURE = "a22cb465";

    static final String APPROVE_SIGNATURE = "095ea7b3";

    static byte[] hexToBytes(String hex) {
        return Bytes.fromHexString(hex).toArrayUnsafe();
    }

    static String longToPaddedHex(long value) {
        return String.format("%064d", value);
    }
}
