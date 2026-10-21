package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeriesPositionTest {

    private static AnyBlockValue makeThreeItems() {
        return BlockValue.of(
                IntegerValue.of(1), IntegerValue.of(2), IntegerValue.of(3));
    }

    @Nested
    @DisplayName("index boundaries on a three-item block")
    class BlockIndexBoundaries {

        @Test
        @DisplayName("1 is the head: the ON point at the lower end")
        void startAtOneForTheHead() {
            assertThat(makeThreeItems().atIndex(1).atHead()).isTrue();
        }

        @Test
        @DisplayName("0 is one below the head: the OFF point")
        void zeroIsRejected() {
            assertThatThrownBy(() -> makeThreeItems().atIndex(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside");
        }

        @Test
        @DisplayName("-1 is further below and equally rejected")
        void rejectANegative() {
            assertThatThrownBy(() -> makeThreeItems().atIndex(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("3 is the last element: the IN point at the upper end")
        void endAtTheLengthForTheLastElement() {
            AnyBlockValue last = makeThreeItems().atIndex(3);
            assertThat(last.atTail()).isFalse();
            assertThat(last.pickFirst()).isEqualTo(IntegerValue.of(3));
        }

        @Test
        @DisplayName("4 is the tail: legal to hold, one past the last element")
        void placeTheTailOnePastTheLength() {
            AnyBlockValue tail = makeThreeItems().atIndex(4);
            assertThat(tail.atTail()).isTrue();
            assertThat(tail.lengthFromHere()).isZero();
        }

        @Test
        @DisplayName("5 is past the tail: the OFF point at the upper end")
        void rejectPastTheTail() {
            assertThatThrownBy(() -> makeThreeItems().atIndex(5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside");
        }

        @Test
        @DisplayName("reading at the tail fails, because it holds nothing")
        void failToReadAtTheTail() {
            assertThatThrownBy(() -> makeThreeItems().tail().pickFirst())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tail");
        }
    }

    @Nested
    @DisplayName("the degenerate case: an empty series")
    class EmptySeries {

        @Test
        @DisplayName("head and tail are the same position")
        void startAtTheTail() {
            AnyBlockValue empty = BlockValue.of();
            assertThat(empty.atHead()).isTrue();
            assertThat(empty.atTail()).isTrue();
            assertThat(empty.storageLength()).isZero();
            assertThat(empty.lengthFromHere()).isZero();
        }

        @Test
        void rejectPositionTwo() {
            assertThatThrownBy(() -> BlockValue.of().atIndex(2))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void includeAnEmptyString() {
            AnyStringValue empty = StringValue.of("");
            assertThat(empty.atHead()).isTrue();
            assertThat(empty.atTail()).isTrue();
            assertThat(empty.text()).isEmpty();
        }
    }

    @Nested
    @DisplayName("length from a position, not from the head")
    class LengthFromPosition {

        @ParameterizedTest(name = "from position {0}")
        @ValueSource(ints = {1, 2, 3, 4})
        void countsWhatRemains(int position) {
            assertThat(makeThreeItems().atIndex(position).lengthFromHere())
                    .isEqualTo(4 - position);
        }

        @Test
        @DisplayName("storage length ignores the position")
        void ignoresThePositionForTheStorageLength() {
            assertThat(makeThreeItems().atIndex(3).storageLength()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("aliasing: the property that makes series REBOL rather than Java")
    class Aliasing {

        @Test
        @DisplayName("a mutation through one value is visible through another")
        void mutationIsVisibleThroughEveryAlias() {
            AnyBlockValue atHead = makeThreeItems();
            AnyBlockValue atSecond = atHead.atIndex(2);

            atHead.storage().set(2, StringValue.of("changed"));

            assertThat(atSecond.pickFirst()).isEqualTo(StringValue.of("changed"));
        }

        @Test
        @DisplayName("appending through one alias lengthens the other")
        void showsAnAppendThroughEveryAlias() {
            AnyBlockValue atHead = makeThreeItems();
            AnyBlockValue atSecond = atHead.atIndex(2);

            atHead.storage().append(IntegerValue.of(4));

            assertThat(atSecond.storageLength()).isEqualTo(4);
            assertThat(atSecond.lengthFromHere()).isEqualTo(3);
        }

        @Test
        @DisplayName("same? is about storage, equal? is about contents")
        void separatesSharingStorageFromEquality() {
            AnyBlockValue original = makeThreeItems();
            AnyBlockValue repositioned = original.atIndex(2);
            AnyBlockValue separateButIdentical = makeThreeItems();

            assertThat(original.sharesStorageWith(repositioned)).isTrue();
            assertThat(original.sharesStorageWith(separateButIdentical)).isFalse();
        }

        @Test
        @DisplayName("a string and a block never share storage")
        void differentSeriesKindsNeverShare() {
            assertThat(BlockValue.of().sharesStorageWith(StringValue.of(""))).isFalse();
            assertThat(StringValue.of("").sharesStorageWith(BlockValue.of())).isFalse();
        }

        @Test
        @DisplayName("repositioning does not copy")
        void keepsTheStorageWhenRepositioned() {
            AnyBlockValue original = makeThreeItems();
            assertThat(original.atIndex(3).storage()).isSameAs(original.storage());
            assertThat(original.head().storage()).isSameAs(original.storage());
            assertThat(original.tail().storage()).isSameAs(original.storage());
        }
    }

    @Nested
    @DisplayName("strings are codepoints, not UTF-16 code units")
    class StringsAreCodepoints {

        @Test
        @DisplayName("an astral character counts as one, not two")
        void astralCharacterIsOneElement() {
            AnyStringValue withEmoji = StringValue.of("a😀b");

            assertThat(withEmoji.storageLength())
                    .as("three characters, even though Java's String.length() says four")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("the second character is the whole emoji, not half of it")
        void keepTheSecondCharacterWhole() {
            AnyStringValue withEmoji = StringValue.of("a😀b");

            assertThat(withEmoji.atIndex(2).pickFirst().codepoint()).isEqualTo(0x1F600);
        }

        @Test
        void textFromAPositionStartsThere() {
            assertThat(StringValue.of("hello").atIndex(3).text()).isEqualTo("llo");
        }
    }

    @Nested
    @DisplayName("wrong datatype for the representation")
    class WrongDatatype {

        @Test
        void hidesAStringFromABlockDatatype() {
            assertThatThrownBy(() -> BlockValue.TYPE.as(StringValue.of("")))
                    .isInstanceOfSatisfying(Raised.class, raised ->
                            assertThat(raised.error().errorId()).isEqualTo("not-same-class"));
        }

        @Test
        void hidesABlockFromAStringDatatype() {
            assertThatThrownBy(() -> StringValue.TYPE.as(BlockValue.of()))
                    .isInstanceOfSatisfying(Raised.class, raised ->
                            assertThat(raised.error().errorId()).isEqualTo("not-same-class"));
        }

        @Test
        void showsABlockButNotAStringToAPathDatatype() {
            assertThat(PathValue.TYPE.as(BlockValue.of())).isInstanceOf(PathValue.class);
            assertThatThrownBy(() -> PathValue.TYPE.as(StringValue.of("a")))
                    .isInstanceOf(Raised.class);
        }

        @Test
        void requiresStorage() {
            assertThatThrownBy(() -> BlockValue.TYPE.wrap(null, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("storage");
        }
    }
}
