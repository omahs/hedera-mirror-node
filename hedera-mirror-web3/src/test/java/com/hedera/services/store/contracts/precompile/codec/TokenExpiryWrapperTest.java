/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenExpiryWrapperTest {

    private TokenExpiryWrapper wrapper;
    private static final AccountID payer = asAccount("0.0.12345");
    private static final long MASK_INT_AS_UNSIGNED_LONG = (1L << 32) - 1;

    @BeforeEach
    void setup() {
        wrapper = createTokenExpiryWrapper();
    }

    @Test
    void autoRenewAccountIsCheckedAsExpected() {
        Assertions.assertEquals(payer, wrapper.autoRenewAccount());
        assertEquals(442L, wrapper.second());
        assertEquals(555L, wrapper.autoRenewPeriod());
        wrapper.setAutoRenewAccount(toGrpcAccountId(10));
        assertEquals(toGrpcAccountId(10), wrapper.autoRenewAccount());
    }

    @Test
    void objectContractWorks() {
        final var one = wrapper;
        final var two = createTokenExpiryWrapper();
        final var three = createTokenExpiryWrapper();
        three.setAutoRenewAccount(toGrpcAccountId(10));

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertEquals(one, two);
        assertNotEquals(one, three);

        assertNotEquals(one.hashCode(), three.hashCode());
        assertEquals(one.hashCode(), two.hashCode());

        assertEquals(
                "TokenExpiryWrapper{second=442, autoRenewAccount=accountNum: 12345\n" + ", autoRenewPeriod=555}",
                wrapper.toString());
    }

    static TokenExpiryWrapper createTokenExpiryWrapper() {
        return new TokenExpiryWrapper(442L, payer, 555L);
    }

    //copied from IdUtils
    static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    //copied from IdUtils
    static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }

    //copied from EntityId
    AccountID toGrpcAccountId(final int code) {
        return AccountID.newBuilder()
                .setShardNum(0L)
                .setRealmNum(0L)
                .setAccountNum(numFromCode(code))
                .build();
    }

    static long numFromCode(int code) {
        return code & MASK_INT_AS_UNSIGNED_LONG;
    }
}
