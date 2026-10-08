package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    private final List<AnyBlockValue.AnyBlockDatatype> everyBlockDatatype = List.of(
            BlockValue.TYPE, ParenValue.TYPE, HashValue.TYPE,
            PathValue.TYPE, SetPathValue.TYPE, GetPathValue.TYPE, LitPathValue.TYPE);

    private final List<AnyPathValue.AnyPathDatatype> everyPathDatatype = List.of(
            PathValue.TYPE, SetPathValue.TYPE, GetPathValue.TYPE, LitPathValue.TYPE);

    @Nested
    @DisplayName("building one from a datatype")
    class FromADatatype {

        @Test
        @DisplayName("each any-block! datatype builds a value that answers to that datatype")
        void eachBlockDatatypeBuildsItsOwnKind() {
            assertThat(everyBlockDatatype).allSatisfy(asked ->
                    assertThat(asked.holding(BlockStorage.of(), 1).datatype()).isSameAs(asked));
        }

        @Test
        @DisplayName("each any-path! datatype builds a path")
        void eachPathDatatypeBuildsAPath() {
            assertThat(everyPathDatatype).allSatisfy(asked ->
                    assertThat(asked.holding(List.of())).isInstanceOf(AnyPathValue.class));
        }

        @Test
        @DisplayName("the others are not paths")
        void theOthersAreNotPaths() {
            assertThat(List.of(BlockValue.TYPE, ParenValue.TYPE, HashValue.TYPE)).allSatisfy(asked ->
                    assertThat(asked.holding(List.of())).isNotInstanceOf(AnyPathValue.class));
        }

        @Test
        @DisplayName("a value outside any-block! cannot be seen as a block")
        void aValueOutsideTheFamilyIsRefused() {
            assertThat(List.<Value>of(StringValue.of("a"), WordValue.of("a"), MapValue.empty(),
                    new ObjectValue(Context.root()))).allSatisfy(outside ->
                    assertThatThrownBy(() -> BlockValue.TYPE.as(outside))
                            .isInstanceOfSatisfying(Raised.class, raised ->
                                    assertThat(raised.error().errorId()).isEqualTo("not-same-class")));
        }
    }

    @Nested
    @DisplayName("where it stands in its storage")
    class ItsPosition {

        @ParameterizedTest
        @ValueSource(ints = {1, 4})
        @DisplayName("from the head to one past the last item is a place to stand")
        void theHeadAndTheTailAreAllowed(int index) {
            assertThat(ParenValue.TYPE.holding(new BlockStorage(oneTwoThree), index).index())
                    .isEqualTo(index);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 5})
        @DisplayName("before the head or past the tail is refused")
        void outsideTheStorageIsRefused(int index) {
            assertThatThrownBy(() -> ParenValue.TYPE.holding(new BlockStorage(oneTwoThree), index))
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
            AnyBlockValue litPath = LitPathValue.TYPE.holding(oneTwoThree);

            assertThat(litPath.asPath()).isInstanceOf(PathValue.class);
            assertThat(litPath.asPath().sharesStorageWith(litPath)).isTrue();
        }

        @Test
        @DisplayName("holding other storage keeps the kind")
        void holdingKeepsTheKind() {
            AnyBlockValue setPath = SetPathValue.TYPE.holding(oneTwoThree);

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
            assertThat(GetPathValue.TYPE.holding(oneTwoThree).looksUpItsDeclaration()).isTrue();
        }

        @Test
        @DisplayName("no other kind looks up its declaration")
        void nothingElseLooksUpItsDeclaration() {
            assertThat(List.of(BlockValue.TYPE, ParenValue.TYPE, HashValue.TYPE, SetPathValue.TYPE,
                    LitPathValue.TYPE)).allSatisfy(kind ->
                    assertThat(kind.holding(List.of()).looksUpItsDeclaration()).isFalse());
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
            assertThat(PathValue.of(oneTwoThree)).isNotEqualTo(GetPathValue.TYPE.holding(oneTwoThree));
        }

        @Test
        @DisplayName("the same items in one kind are equal and hash alike")
        void theSameKindIsEqual() {
            assertThat(ParenValue.of(oneTwoThree)).isEqualTo(ParenValue.of(oneTwoThree));
            assertThat(ParenValue.of(oneTwoThree)).hasSameHashCodeAs(ParenValue.of(oneTwoThree));
        }
    }
}
