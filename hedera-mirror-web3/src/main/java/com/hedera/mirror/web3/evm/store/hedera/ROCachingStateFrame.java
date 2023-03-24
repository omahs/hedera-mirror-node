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

package com.hedera.mirror.web3.evm.store.hedera;

import static com.hedera.mirror.web3.utils.MiscUtilities.requireAllNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and disallows updates/deletes. */
public class ROCachingStateFrame<Address> extends CachingStateFrame<Address> {

    public ROCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<Address>> upstreamFrame,
            @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
    }

    @Override
    @NonNull
    public Optional<Object> getEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");

        final var entry = cache.get(address);
        return switch (entry.state()) {
            case NOT_YET_FETCHED -> upstreamFrame.flatMap(upstreamFrame -> {
                final var upstream = upstreamFrame.getEntity(klass, cache, address);
                cache.fill(address, upstream.orElse(null));
                return upstream;
            });
            case PRESENT, UPDATED -> Optional.of(entry.value());
            case MISSING, DELETED -> Optional.empty();
            case INVALID -> throw new IllegalArgumentException("trying to get entity when state is invalid");
        };
    }

    @Override
    public void setEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address,
            @NonNull final Object entity) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address", entity, "entity");
        throw new UnsupportedOperationException("cannot write entity to a R/O cache");
    }

    @Override
    public void deleteEntity(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<Address> cache,
            @NonNull final Address address) {
        requireAllNonNull(klass, "klass", cache, "cache", address, "address");
        throw new UnsupportedOperationException("cannot delete entity from a R/O cache");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<Address> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        throw new UnsupportedOperationException("cannot commit to a R/O cache");
    }
}
