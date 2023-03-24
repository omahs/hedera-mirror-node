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

import static com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState.EntityState;
import static com.hedera.mirror.web3.utils.MiscUtilities.requireAllNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState;
import com.hedera.mirror.web3.evm.store.hedera.impl.UpdatableReferenceCacheLineState.Entry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(SoftAssertionsExtension.class)
class UpdatableReferenceCacheTest {

    ///// Common types/values/methods for this test start here:

    enum ValueIs {
        MISSING,
        NULL,
        NON_NULL
    }

    enum ValueFrom {
        NOWHERE,
        ORIGINAL,
    CURRENT
  }

    static final String THIS_KEY = "THIS KEY";
    static final Long ORIGINAL_VALUE = 10L;
    static final Long UPDATED_VALUE = -25L;
    static final Long NEW_VALUE = -100L;

    void setInitialCacheLineState(@NonNull final ValueIs originalValueIs, @NonNull final ValueIs currentValueIs) {
        switch (originalValueIs) {
            case MISSING -> {}
            case NULL -> sut.addNullToOriginal(THIS_KEY);
            case NON_NULL -> sut.addToOriginal(THIS_KEY, ORIGINAL_VALUE);
        }
        switch (currentValueIs) {
            case MISSING -> {}
      case NULL -> sut.addNullToCurrent(THIS_KEY);
      case NON_NULL -> sut.addToCurrent(THIS_KEY, UPDATED_VALUE);
    }
    }

    ///// Per-test setup starts here:

    final UpdatableReferenceCacheSpy sut = new UpdatableReferenceCacheSpy(); // cache that maps String->Long

    @InjectSoftAssertions
    SoftAssertions softly;

    ///// Tests start here:

    @Test
    void emptyCacheTest() {
        assertThat(sut.getCounts()).isEqualTo(new UpdatableReferenceCacheSpy.Counts(0, 0, 0));
    }

    @ParameterizedTest(name = "original {0} ✕ current {1} ⟶ (state {2}, with value from {3})")
    @CsvSource({
        // original current exp. state      exp. value
        " MISSING, MISSING, NOT_YET_FETCHED,NOWHERE",
        " MISSING, NULL,    INVALID,        NOWHERE",
        " MISSING, NON_NULL,UPDATED,        CURRENT",
        " NULL,    MISSING, MISSING,        NOWHERE",
        " NULL,    NULL,    INVALID,        NOWHERE",
        " NULL,    NON_NULL,UPDATED,        CURRENT",
        " NON_NULL,MISSING, PRESENT,        ORIGINAL",
        " NON_NULL,NULL,    DELETED,        NOWHERE",
        " NON_NULL,NON_NULL,UPDATED,        CURRENT"
    })
    void getCurrentStateTestExhaustive(
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs,
            @NonNull final EntityState expectedState,
            @NonNull final ValueFrom expectedValueFrom) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

    final var expected =
        new UpdatableReferenceCacheLineState.Entry(
            expectedState,
            switch (expectedValueFrom) {
              case NOWHERE -> null;
              case ORIGINAL -> ORIGINAL_VALUE;
              case CURRENT -> UPDATED_VALUE;
            });

        final var actual = sut.getCacheLineState(THIS_KEY);
        softly.assertThat(actual).isEqualTo(expected);
        final var actualGet = sut.get(THIS_KEY);
        softly.assertThat(actualGet).isEqualTo(expected);
    }

    @Test
    void cannotGetWithANullKeyTest() {
        assertThatNullPointerException().isThrownBy(() -> sut.get(null));
    }

    @Test
    void fillWhileNotYetFetchedIsOkTest() {
        sut.fill(THIS_KEY, ORIGINAL_VALUE);
        assertThat(sut.getOriginal()).containsOnlyKeys(THIS_KEY).containsEntry(THIS_KEY, ORIGINAL_VALUE);
    assertThat(sut.getCurrent()).isEmpty();
    }

    @ParameterizedTest(name = "state {0} (original {1} x current {2})")
    @CsvSource({
        // state via: original current
        "MISSING, NULL,     MISSING",
        "PRESENT, NON_NULL, MISSING",
        "UPDATED, NON_NULL, NON_NULL",
        "DELETED, NON_NULL, NULL",
        "INVALID, MISSING,  NULL"
    })
    void fillWhileInBadStateTest(
            @NonNull final EntityState state,
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

        assertThatIllegalArgumentException().isThrownBy(() -> sut.fill(THIS_KEY, NEW_VALUE));
    }

    @Test
    void cannotUpdateWithANullKeyTest() {
        assertThatNullPointerException().isThrownBy(() -> sut.update(null, "foo"));
    }

    @Test
    void cannotUpdateWithANullValueTest() {
        assertThatNullPointerException().isThrownBy(() -> sut.update("foo", null));
    }

    @ParameterizedTest(name = "state {0} (original {1} x current {2})")
    @CsvSource({
        // state via: original current
        "NOT_YET_FETCHED, MISSING,  MISSING",
        "MISSING,         NULL,     MISSING",
        "PRESENT,         NON_NULL, MISSING",
        "UPDATED,         NULL,     NON_NULL",
        "UPDATED,         NON_NULL, NON_NULL",
        "DELETED,         NON_NULL, NULL"
    })
    void updateInAllowableScenariosTest(
            @NonNull final EntityState state,
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

        sut.update(THIS_KEY, NEW_VALUE);
        softly.assertThat(sut.get(THIS_KEY))
                .isEqualTo(new UpdatableReferenceCacheLineState.Entry(EntityState.UPDATED, NEW_VALUE));
    softly
        .assertThat(sut.getCurrent())
        .containsOnlyKeys(THIS_KEY)
        .containsEntry(THIS_KEY, NEW_VALUE);
    }

    @Test
    void updateDisallowsCertainScenariosTest() {

    // invalid state is rejected
    sut.clearOriginal().clearCurrent().addNullToCurrent(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.update(THIS_KEY, 10L))
                .as("INVALID state")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");

    // present with same value is rejected (that's not an update at all, it's probably a user error
    // where the value
    // - a reference type - was modified)
    sut.clearOriginal().clearCurrent().addToOriginal(THIS_KEY, ORIGINAL_VALUE);
        softly.assertThatThrownBy(() -> sut.update(THIS_KEY, ORIGINAL_VALUE))
                .as("PRESENT, overwriting with same value")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trying to update");
        softly.assertThat(sut.getOriginal())
                .as("PRESENT, overwriting with same value didn't change k/v")
                .containsOnlyKeys(THIS_KEY)
                .containsEntry(THIS_KEY, ORIGINAL_VALUE);
    }

    @Test
    void cannotDeleteWithANullKeyTest() {
        assertThatNullPointerException().isThrownBy(() -> sut.delete(null));
    }

    @ParameterizedTest(name = "state {0} (original {1} x current {2}) ⟶ result (state {3}, key exists? {4})")
    @CsvSource({
        // state via: original current   result  key exists
        "  PRESENT,   NON_NULL,MISSING, DELETED,true",
        "  UPDATED,   NULL,    NON_NULL,MISSING,false"
    })
    void deleteInAllowableScenariosTest(
            @NonNull final EntityState state,
            @NonNull final ValueIs originalValueIs,
            @NonNull final ValueIs currentValueIs,
            @NonNull final EntityState endState,
            final boolean keyInCurrentAtEnd) {

        setInitialCacheLineState(originalValueIs, currentValueIs);

        sut.delete(THIS_KEY);
        softly.assertThat(sut.get(THIS_KEY)).isEqualTo(new Entry(endState, null));
    if (keyInCurrentAtEnd)
      softly.assertThat(sut.getCurrent()).containsOnlyKeys(THIS_KEY).containsEntry(THIS_KEY, null);
    else softly.assertThat(sut.getCurrent()).isEmpty();
    }

    @Test
    void deleteDisallowsCertainScenariosTest() {

    // invalid state is rejected
    sut.clearOriginal().clearCurrent().addNullToCurrent(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("INVALID state")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");

    // not-yet-fetched state is rejected
    sut.clearOriginal().clearCurrent();
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("NOT_YET_FETCHED")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fetched");

    // missing state is rejected
    sut.clearOriginal().clearCurrent().addNullToOriginal(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("MISSING")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");

    // deleted state is rejected
    sut.clearOriginal()
        .clearCurrent()
        .addToOriginal(THIS_KEY, ORIGINAL_VALUE)
        .addNullToOriginal(THIS_KEY);
        softly.assertThatThrownBy(() -> sut.delete(THIS_KEY))
                .as("DELETED")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deleted");
    }

    @Test
    void coalesceFromTest() {

        final var src = new UpdatableReferenceCacheSpy(); // maps String->Long

    src.addToOriginal("SRC NOT NULL VALUE O", 10L)
        .addToOriginal("BOTH NOT NULL VALUE O", 20L)
        .addNullToOriginal("SRC NULL VALUE O")
        .addNullToOriginal("BOTH NULL VALUE O")
        .addToCurrent("SRC NOT NULL VALUE AD", 30L)
        .addToCurrent("BOTH NOT NULL VALUE AD", 40L)
        .addNullToCurrent("SRC NULL VALUE AD")
        .addNullToCurrent("BOTH NULL VALUE AD");

    sut.addToOriginal("SUT NOT NULL VALUE O", -10L)
        .addToOriginal("BOTH NOT NULL VALUE O", -20L)
        .addNullToOriginal("SUT NULL VALUE O")
        .addNullToOriginal("BOTH NULL VALUE O")
        .addToCurrent("SUT NOT NULL VALUE AD", -30L)
        .addToCurrent("BOTH NOT NULL VALUE AD", -40L)
        .addNullToCurrent("SUT NULL VALUE AD")
        .addNullToCurrent("BOTH NULL VALUE AD");

        final var expectedOriginal = new HashMap<>(sut.getOriginal());
        final var expectedCurrent = makeMapOf(
                String.class,
                Object.class,
                "SUT NOT NULL VALUE AD",
                -30L,
                "BOTH NOT NULL VALUE AD",
                40L,
                "SUT NULL VALUE AD",
                null,
                "BOTH NULL VALUE AD",
                null,
                "SRC NOT NULL VALUE AD",
                30L,
                "SRC NULL VALUE AD",
                null);

        sut.coalesceFrom(src);

        softly.assertThat(sut.getOriginal())
                .as("original map is not touched by coalesce")
                .isEqualTo(expectedOriginal);

    softly
        .assertThat(sut.getCurrent())
        .as("current map is merged by coalesce")
        .isEqualTo(expectedCurrent);
    }

    ///// Utility methods beyond this point:

    /** Like `Map.of` but doesn't barf on `null` values */
    // TODO: Move to utils class
    @NonNull
    <K, V> Map<K, V> makeMapOf(
            @NonNull final Class<K> klassK, @NonNull final Class<V> klassV, final Object... kvPairs) {
        requireAllNonNull(klassK, "klassK", klassV, "klassV");

        if (0 != kvPairs.length % 2) throw new IllegalArgumentException("must have even #arguments");
        final var r = new HashMap<K, V>(kvPairs.length / 2);
        for (int i = 0; i < kvPairs.length; i += 2) {

            K key = verifyIsCorrectTypeOrNull(klassK, kvPairs[i], "key argument at index %d".formatted(i));
            V value = verifyIsCorrectTypeOrNull(klassV, kvPairs[i + 1], "value argument at index %d".formatted(i + 1));

            r.put(key, value);
        }
        return r;
    }

    /** Verify that some object is either null or of some specific type, returning that object cast appropriately (or
     * else throwing IllegalArgumentException. */
    // TODO: Move to utils class
    @SuppressWarnings("unchecked") // OK because unchecked cast is immediately preceded by type check
    <T> T verifyIsCorrectTypeOrNull(
            @NonNull final Class<T> klass, @Nullable final Object obj, @NonNull final String customMessage) {
        requireAllNonNull(klass, "klass", customMessage, "customMessage");

        return null == obj
                ? null
                : klass.isInstance(obj)
                        ? (T) obj
                        : throwException(
                                IllegalArgumentException.class,
                                "%s is not a %s (it's a %s)"
                                        .formatted(
                                                customMessage,
                                                klass.getTypeName(),
                                                obj.getClass().getTypeName()));
    }

    /** Throw an exception (with message) in the context of an _expression_ (where a `throw` statement by itself is
     * not acceptable).
     */
    // TODO: Move to utils class
    <T> T throwException(@NonNull final Class<?> exceptionKlass, @NonNull final String message) {
        requireAllNonNull(exceptionKlass, "exceptionKlass", message, "message");

        if (!(RuntimeException.class.isAssignableFrom(exceptionKlass)))
            throw new IllegalArgumentException(
                    "%s is not a RuntimeException type".formatted(exceptionKlass.getTypeName()));

        try {
            throw (RuntimeException)
                    exceptionKlass.getDeclaredConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
