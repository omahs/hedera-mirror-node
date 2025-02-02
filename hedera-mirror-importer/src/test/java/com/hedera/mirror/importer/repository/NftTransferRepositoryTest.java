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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftTransferRepositoryTest extends AbstractRepositoryTest {

    private final NftTransferRepository nftTransferRepository;

    @Test
    void prune() {
        domainBuilder.nftTransfer().persist();
        var nftTransfer2 = domainBuilder.nftTransfer().persist();
        var nftTransfer3 = domainBuilder.nftTransfer().persist();

        nftTransferRepository.prune(nftTransfer2.getId().getConsensusTimestamp());

        assertThat(nftTransferRepository.findAll()).containsExactly(nftTransfer3);
    }

    @Test
    void save() {
        var nftTransfer = domainBuilder.nftTransfer().get();
        nftTransferRepository.save(nftTransfer);
        assertThat(nftTransferRepository.findById(nftTransfer.getId())).contains(nftTransfer);
    }
}
