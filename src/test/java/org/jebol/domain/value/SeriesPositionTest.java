package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Series positions, at and either side of their boundaries.
 *
 * <p>Positions are 1-based and run from the head to one past the last element.
 * That last position is the tail: legal to hold, illegal to read from. Getting
 * either end wrong by one is the classic series bug, so both ends are tested
 * on the boundary, one inside it and one outside it.
 */
class SeriesPositionTest {

    private static BlockValue threeItems() {
        return BlockValue.block(
                IntegerValue.of(1), IntegerValue.of(2), IntegerValue.of(3));
    }

    @Nested
    @DisplayName("index boundaries on a three-item block")
    class BlockIndexBoundaries {

        @Test
        @DisplayName("1 is the head: the ON point at the lower end")
        void headIsOne() {
            assertThat(threeItems().atIndex(1).atHead()).isTrue();
        }

        @Test
        @DisplayName("0 is one below the head: the OFF point")
        void zeroIsRejected() {
            assertThatThrownBy(() -> threeItems().atIndex(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside");
        }

        @Test
        @DisplayName("-1 is further below and equally rejected")
        void negativeIsRejected() {
            assertThatThrownBy(() -> threeItems().atIndex(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("3 is the last element: the IN point at the upper end")
        void lastElementIsLength() {
            BlockValue last = threeItems().atIndex(3);
            assertThat(last.atTail()).isFalse();
            assertThat(last.first()).isEqualTo(IntegerValue.of(3));
        }

        @Test
        @DisplayName("4 is the tail: legal to hold, one past the last element")
        void tailIsLengthPlusOne() {
            BlockValue tail = threeItems().atIndex(4);
            assertThat(tail.atTail()).isTrue();
            assertThat(tail.lengthFromHere()).isZero();
        }

        @Test
        @DisplayName("5 is past the tail: the OFF point at the upper end")
        void pastTailIsRejected() {
            assertThatThrownBy(() -> threeItems().atIndex(5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside");
        }

        @Test
        @DisplayName("reading at the tail fails, because it holds nothing")
        void readingAtTailFails() {
            assertThatThrownBy(() -> threeItems().tail().first())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tail");
        }
    }

    @Nested
    @DisplayName("the degenerate case: an empty series")
    class EmptySeries {

        @Test
        @DisplayName("head and tail are the same position")
        void headIsAlsoTheTail() {
            BlockValue empty = BlockValue.block();
            assertThat(empty.atHead()).isTrue();
            assertThat(empty.atTail()).isTrue();
            assertThat(empty.storageLength()).isZero();
            assertThat(empty.lengthFromHere()).isZero();
        }

        @Test
        void position2IsRejected() {
            assertThatThrownBy(() -> BlockValue.block().atIndex(2))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void anEmptyStringBehavesTheSame() {
            StringValue empty = StringValue.of("");
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
            assertThat(threeItems().atIndex(position).lengthFromHere())
                    .isEqualTo(4 - position);
        }

        @Test
        @DisplayName("storage length ignores the position")
        void storageLengthIsPositionIndependent() {
            assertThat(threeItems().atIndex(3).storageLength()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("aliasing: the property that makes series REBOL rather than Java")
    class Aliasing {

        @Test
        @DisplayName("a mutation through one value is visible through another")
        void mutationIsVisibleThroughEveryAlias() {
            BlockValue atHead = threeItems();
            BlockValue atSecond = atHead.atIndex(2);

            atHead.storage().set(2, StringValue.of("changed"));

            assertThat(atSecond.first()).isEqualTo(StringValue.of("changed"));
        }

        @Test
        @DisplayName("appending through one alias lengthens the other")
        void appendingIsVisibleThroughEveryAlias() {
            BlockValue atHead = threeItems();
            BlockValue atSecond = atHead.atIndex(2);

            atHead.storage().append(IntegerValue.of(4));

            assertThat(atSecond.storageLength()).isEqualTo(4);
            assertThat(atSecond.lengthFromHere()).isEqualTo(3);
        }

        @Test
        @DisplayName("same? is about storage, equal? is about contents")
        void sharingStorageIsNotTheSameAsBeingEqual() {
            BlockValue original = threeItems();
            BlockValue repositioned = original.atIndex(2);
            BlockValue separateButIdentical = threeItems();

            assertThat(original.sharesStorageWith(repositioned)).isTrue();
            assertThat(original.sharesStorageWith(separateButIdentical)).isFalse();
        }

        @Test
        @DisplayName("a string and a block never share storage")
        void differentSeriesKindsNeverShare() {
            assertThat(BlockValue.block().sharesStorageWith(StringValue.of(""))).isFalse();
            assertThat(StringValue.of("").sharesStorageWith(BlockValue.block())).isFalse();
        }

        @Test
        @DisplayName("repositioning does not copy")
        void repositioningKeepsTheSameStorage() {
            BlockValue original = threeItems();
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
            StringValue withEmoji = StringValue.of("a😀b");

            assertThat(withEmoji.storageLength())
                    .as("three characters, even though Java's String.length() says four")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("the second character is the whole emoji, not half of it")
        void secondCharacterIsWhole() {
            StringValue withEmoji = StringValue.of("a😀b");

            assertThat(withEmoji.atIndex(2).first().codepoint()).isEqualTo(0x1F600);
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
        void aBlockValueRejectsAStringDatatype() {
            assertThatThrownBy(() -> new BlockValue(BlockStorage.of(), 1, Datatype.STRING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-block!");
        }

        @Test
        void aStringValueRejectsABlockDatatype() {
            assertThatThrownBy(() -> new StringValue(StringStorage.of(""), 1, Datatype.BLOCK))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-string!");
        }

        @Test
        void aPathFactoryRejectsANonPathDatatype() {
            assertThatThrownBy(() -> BlockValue.path(List.of(), Datatype.BLOCK))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-path!");
        }

        @Test
        void storageIsRequired() {
            assertThatThrownBy(() -> new BlockValue(null, 1, Datatype.BLOCK))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("storage");
        }
    }
}
