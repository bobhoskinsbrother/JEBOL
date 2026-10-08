package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ABlockKnowsItsOwnKindTest {

    private final List<Value> oneTwoThree = List.of(IntegerValue.of(1), IntegerValue.of(2), IntegerValue.of(3));

    private final RandomDraw alwaysTheFirst = new RandomDraw() {
        @Override
        public long next() {
            return 0;
        }

        @Override
        public int below(int limit) {
            return 0;
        }

        @Override
        public int belowWithoutNarrowing(int limit) {
            return 0;
        }

        @Override
        public long upTo(long limit) {
            return 0;
        }

        @Override
        public double fraction() {
            return 0;
        }

        @Override
        public <T> void shuffle(List<T> items) {
        }
    };

    @Nested
    @DisplayName("building one from a datatype")
    class FromADatatype {

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"BLOCK", "PAREN", "HASH", "PATH", "SET_PATH", "GET_PATH", "LIT_PATH"})
        @DisplayName("each any-block! datatype builds a value that answers to that datatype")
        void eachBlockDatatypeBuildsItsOwnKind(Datatype asked) {
            assertThat(AnyBlockValue.ofTheDatatype(BlockStorage.of(), 1, asked).datatype()).isEqualTo(asked);
        }

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"PATH", "SET_PATH", "GET_PATH", "LIT_PATH"})
        @DisplayName("each any-path! datatype builds a path")
        void eachPathDatatypeBuildsAPath(Datatype asked) {
            assertThat(AnyBlockValue.ofTheDatatype(BlockStorage.of(), 1, asked)).isInstanceOf(AnyPathValue.class);
        }

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"BLOCK", "PAREN", "HASH"})
        @DisplayName("the others are not paths")
        void theOthersAreNotPaths(Datatype asked) {
            assertThat(AnyBlockValue.ofTheDatatype(BlockStorage.of(), 1, asked)).isNotInstanceOf(AnyPathValue.class);
        }

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"STRING", "WORD", "MAP", "OBJECT"})
        @DisplayName("a datatype outside any-block! is refused")
        void aDatatypeOutsideTheFamilyIsRefused(Datatype asked) {
            assertThatThrownBy(() -> AnyBlockValue.ofTheDatatype(BlockStorage.of(), 1, asked))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-block!");
        }

        @Test
        @DisplayName("a path asked for with a block datatype is refused")
        void aPathAskedForWithABlockDatatype() {
            assertThatThrownBy(() -> AnyPathValue.path(List.of(), Datatype.BLOCK))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-path!");
        }
    }

    @Nested
    @DisplayName("where it stands in its storage")
    class ItsPosition {

        @ParameterizedTest
        @ValueSource(ints = {1, 4})
        @DisplayName("from the head to one past the last item is a place to stand")
        void theHeadAndTheTailAreAllowed(int index) {
            assertThat(AnyBlockValue.ofTheDatatype(new BlockStorage(oneTwoThree), index, Datatype.PAREN).index())
                    .isEqualTo(index);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 5})
        @DisplayName("before the head or past the tail is refused")
        void outsideTheStorageIsRefused(int index) {
            assertThatThrownBy(() -> AnyBlockValue.ofTheDatatype(new BlockStorage(oneTwoThree), index, Datatype.PAREN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside 1..4");
        }

        @Test
        @DisplayName("moving along keeps the kind and shares the storage")
        void movingAlongKeepsTheKind() {
            AnyBlockValue paren = ParenValue.of(oneTwoThree);
            AnyBlockValue moved = paren.atIndex(2);

            assertThat(moved).isInstanceOf(ParenValue.class);
            assertThat(moved.remaining()).containsExactly(IntegerValue.of(2), IntegerValue.of(3));
            assertThat(moved.sharesStorageWith(paren)).isTrue();
        }
    }

    @Nested
    @DisplayName("seeing the same items as another kind")
    class AsAnotherKind {

        @Test
        @DisplayName("a paren seen as a block shares its items and its position")
        void aParenAsABlock() {
            AnyBlockValue paren = ParenValue.of(oneTwoThree).atIndex(2);
            BlockValue block = paren.asBlock();

            assertThat(block.index()).isEqualTo(2);
            assertThat(block.sharesStorageWith(paren)).isTrue();
        }

        @Test
        @DisplayName("a lit-path seen as a path is a plain path over the same segments")
        void aLitPathAsAPath() {
            AnyBlockValue litPath = AnyPathValue.path(oneTwoThree, Datatype.LIT_PATH);

            assertThat(litPath.asPath()).isInstanceOf(PathValue.class);
            assertThat(litPath.asPath().sharesStorageWith(litPath)).isTrue();
        }

        @Test
        @DisplayName("holding other storage keeps the kind")
        void holdingKeepsTheKind() {
            AnyBlockValue setPath = AnyPathValue.path(oneTwoThree, Datatype.SET_PATH);

            assertThat(setPath.holding(BlockStorage.of())).isInstanceOf(SetPathValue.class);
        }

        @Test
        @DisplayName("a copy keeps the kind")
        void aCopyKeepsTheKind() {
            assertThat(ParenValue.of(oneTwoThree).copied(false)).isInstanceOf(ParenValue.class);
        }
    }

    @Nested
    @DisplayName("what each kind means")
    class WhatItMeans {

        @Test
        @DisplayName("a path and a get-path look up their declaration")
        void aPathAndAGetPathLookUpTheirDeclaration() {
            assertThat(PathValue.of(oneTwoThree).looksUpItsDeclaration()).isTrue();
            assertThat(AnyPathValue.path(oneTwoThree, Datatype.GET_PATH).looksUpItsDeclaration()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"BLOCK", "PAREN", "HASH", "SET_PATH", "LIT_PATH"})
        @DisplayName("no other kind looks up its declaration")
        void nothingElseLooksUpItsDeclaration(Datatype kind) {
            assertThat(AnyBlockValue.ofTheDatatype(BlockStorage.of(), 1, kind).looksUpItsDeclaration()).isFalse();
        }

        @Test
        @DisplayName("a path runs its segments together with slashes and a block with nothing between")
        void runningTogether() {
            assertThat(PathValue.of(oneTwoThree).runTogether()).isEqualTo("1/2/3");
            assertThat(BlockValue.block(oneTwoThree).runTogether()).isEqualTo("123");
        }

        @Test
        @DisplayName("only a block can be shuffled or picked from at random")
        void onlyABlockIsRandom() {
            assertThat(BlockValue.block(oneTwoThree).pickedAtRandom(alwaysTheFirst)).isEqualTo(IntegerValue.of(1));
            assertThatThrownBy(() -> ParenValue.of(oneTwoThree).pickedAtRandom(alwaysTheFirst))
                    .isInstanceOf(Raised.class);
            assertThatThrownBy(() -> PathValue.of(oneTwoThree).randomised(alwaysTheFirst))
                    .isInstanceOf(Raised.class);
        }

        @Test
        @DisplayName("an empty block picked from at random gives none")
        void anEmptyBlockPicksNone() {
            assertThat(BlockValue.block().pickedAtRandom(alwaysTheFirst)).isEqualTo(NoneValue.none());
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("the same items in two kinds are two different values")
        void differentKindsDiffer() {
            assertThat(ParenValue.of(oneTwoThree)).isNotEqualTo(BlockValue.block(oneTwoThree));
            assertThat(PathValue.of(oneTwoThree)).isNotEqualTo(AnyPathValue.path(oneTwoThree, Datatype.GET_PATH));
        }

        @Test
        @DisplayName("the same items in one kind are equal and hash alike")
        void theSameKindIsEqual() {
            assertThat(ParenValue.of(oneTwoThree)).isEqualTo(ParenValue.of(oneTwoThree));
            assertThat(ParenValue.of(oneTwoThree)).hasSameHashCodeAs(ParenValue.of(oneTwoThree));
        }
    }
}
