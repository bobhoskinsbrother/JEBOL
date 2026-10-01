package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ABinaryReadsItselfAsStrictTextTest {

    private static final int LOWER_A = 0x61;
    private static final int LOWER_B = 0x62;
    private static final int LOWER_C = 0x63;
    private static final int E_ACUTE_LEAD = 0xC3;
    private static final int E_ACUTE_TRAIL = 0xA9;
    private static final int NEVER_IN_UTF8 = 0xFF;

    private static String errorIdOf(Throwable thrown) {
        return ((Raised) thrown).error().errorId();
    }

    private static BinaryValue abc() {
        return BinaryValue.of(LOWER_A, LOWER_B, LOWER_C);
    }

    @Nested
    @DisplayName("asStrictText reads from the index to the tail")
    class FromHereToTheTail {

        @Test
        @DisplayName("plain ASCII reads as itself")
        void ascii() {
            assertThat(abc().asStrictText()).isEqualTo("abc");
        }

        @Test
        @DisplayName("a two-byte character reads as one character")
        void aTwoByteCharacter() {
            assertThat(BinaryValue.of(E_ACUTE_LEAD, E_ACUTE_TRAIL).asStrictText())
                    .isEqualTo("é");
        }

        @Test
        @DisplayName("it starts at the index, not at the head")
        void startsAtTheIndex() {
            assertThat(abc().atIndex(2).asStrictText()).isEqualTo("bc");
        }

        @Test
        @DisplayName("at the tail it is empty text")
        void atTheTail() {
            assertThat(abc().tail().asStrictText()).isEmpty();
        }

        @Test
        @DisplayName("an empty binary is empty text")
        void empty() {
            assertThat(BinaryValue.of().asStrictText()).isEmpty();
        }

        @Test
        @DisplayName("a byte that is never UTF-8 is invalid-chars, not a replacement character")
        void aByteThatIsNeverUtf8() {
            assertThatThrownBy(() -> BinaryValue.of(LOWER_A, NEVER_IN_UTF8).asStrictText())
                    .isInstanceOf(Raised.class)
                    .extracting(ABinaryReadsItselfAsStrictTextTest::errorIdOf)
                    .isEqualTo("invalid-chars");
        }

        @Test
        @DisplayName("a character cut off half way is invalid-chars")
        void aCharacterCutOff() {
            assertThatThrownBy(() -> BinaryValue.of(LOWER_A, E_ACUTE_LEAD).asStrictText())
                    .isInstanceOf(Raised.class)
                    .extracting(ABinaryReadsItselfAsStrictTextTest::errorIdOf)
                    .isEqualTo("invalid-chars");
        }
    }

    @Nested
    @DisplayName("asStrictTextUpTo reads from the index up to, not including, another index")
    class FromHereUpToAnIndex {

        @Test
        @DisplayName("one short of the tail leaves the last byte out")
        void oneShortOfTheTail() {
            assertThat(abc().asStrictTextUpTo(3)).isEqualTo("ab");
        }

        @Test
        @DisplayName("up to the tail is everything from here")
        void upToTheTail() {
            assertThat(abc().asStrictTextUpTo(4)).isEqualTo("abc");
        }

        @Test
        @DisplayName("up to the index itself is empty text")
        void upToItself() {
            assertThat(abc().atIndex(2).asStrictTextUpTo(2)).isEmpty();
        }

        @Test
        @DisplayName("up to an index behind this one is empty text rather than a failure")
        void upToAnIndexBehind() {
            assertThat(abc().atIndex(3).asStrictTextUpTo(1)).isEmpty();
        }

        @Test
        @DisplayName("it starts at the index, not at the head")
        void startsAtTheIndex() {
            assertThat(abc().atIndex(2).asStrictTextUpTo(3)).isEqualTo("b");
        }

        @Test
        @DisplayName("a bad byte beyond the end is never read")
        void aBadByteBeyondTheEnd() {
            assertThat(BinaryValue.of(LOWER_A, LOWER_B, NEVER_IN_UTF8).asStrictTextUpTo(3))
                    .isEqualTo("ab");
        }

        @Test
        @DisplayName("a bad byte inside the span is invalid-chars")
        void aBadByteInside() {
            assertThatThrownBy(() -> BinaryValue.of(NEVER_IN_UTF8, LOWER_A).asStrictTextUpTo(3))
                    .isInstanceOf(Raised.class)
                    .extracting(ABinaryReadsItselfAsStrictTextTest::errorIdOf)
                    .isEqualTo("invalid-chars");
        }
    }
}
