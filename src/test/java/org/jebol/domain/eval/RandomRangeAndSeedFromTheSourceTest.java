package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RANDOM: the range it draws from, the element {@code /only} picks, and the
 * seed each datatype makes.
 *
 * <p>{@code Random_Range} in {@code f-random.c} throws a draw away and takes
 * another whenever the draw is above the last exact multiple of the limit. Skip
 * that and the answers lean low, badly: over a limit two thirds of the
 * generator's range, the bottom half of the answers arrive twice as often as
 * the top half. It also refuses a limit larger than that range rather than
 * drawing unevenly from it.
 *
 * <p>{@code /only} on a string picks a byte offset into the UTF-8 and steps
 * back to a character boundary, so a character written in more bytes is picked
 * more often. A binary shares that arm and therefore that step, which is why an
 * octet between {@code 80} and {@code BF} can be unreachable. Both were
 * measured on a real Rebol before being written down here.
 *
 * <p>Every {@code A_RANDOM} arm decides its own seed and no two agree: a
 * decimal seeds with its bit pattern rather than its value, a string and a
 * tuple with a checksum of their bytes, a date with its year and day packed
 * together, and a block or a vector has no arm for seeding at all.
 */
class RandomRangeAndSeedFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return withoutTheDelimitersAroundAText(
                interpreter.display(interpreter.run(source)));
    }

    private static String withoutTheDelimitersAroundAText(String shown) {
        return isWrappedIn(shown, '"', '"') || isWrappedIn(shown, '{', '}')
                ? shown.substring(1, shown.length() - 1)
                : shown;
    }

    private static boolean isWrappedIn(String shown, char opening, char closing) {
        return shown.length() >= 2
                && shown.charAt(0) == opening
                && shown.charAt(shown.length() - 1) == closing;
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['worked]");
    }

    @Nested
    @DisplayName("the range is drawn evenly, which takes rejecting some draws")
    class DrawingEvenly {

        @Test
        @DisplayName("half the answers land in the top half of an awkward limit")
        void halfLandInTheTopHalf() {
            assertThat(answerTo("""
                    limit: (to integer! #{7ffffffffffffffe}) / 3
                    halfway: (to integer! 2 ** 62) - limit
                    random/seed 0
                    above: 0
                    loop 10000 [if (random limit) <= halfway [above: above + 1]]
                    round/to (above / 10000) 0.1"""))
                    .as("taking the remainder without rejecting gives 0.7, not 0.5")
                    .isEqualTo("0.5");
        }

        @Test
        @DisplayName("a limit the generator's range divides evenly needs no rejection")
        void alimitThatDividesEvenly() {
            assertThat(answerTo("""
                    random/seed 0
                    random to integer! 2 ** 62"""))
                    .isEqualTo("965766428883745031");
        }

        @Test
        @DisplayName("a limit past that range is refused rather than drawn unevenly")
        void alimitPastTheRange() {
            assertThat(whatHappensTo("random 4611686018427387905")).isEqualTo("overflow");
        }

        @Test
        @DisplayName("and so is the largest integer there is")
        void thelargestInteger() {
            assertThat(whatHappensTo("random to integer! #{7ffffffffffffffe}"))
                    .isEqualTo("overflow");
        }

        @Test
        @DisplayName("a negative limit keeps its sign")
        void anegativeLimit() {
            assertThat(answerTo("random/seed 0 random -100")).isEqualTo("-31");
        }

        @Test
        @DisplayName("and nought answers nought")
        void nought() {
            assertThat(answerTo("random 0")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("/ONLY picks one element out of a series")
    class PickingOneElement {

        @Test
        @DisplayName("a character out of a string, repeats included")
        void acharacterOutOfAString() {
            assertThat(answerTo("""
                    random/seed 1
                    mold reduce [
                        random/only "0123456789"
                        random/only "0123456789"
                        random/only "0123456789"
                    ]"""))
                    .as("shuffling the string and answering the string instead "
                            + "gives three strings and never a character")
                    .isEqualTo("""
                            [#"8" #"9" #"9"]""");
        }

        @Test
        @DisplayName("every character of a ten-character string over a hundred picks")
        void everyCharacterOverAHundredPicks() {
            assertThat(answerTo("""
                    random/seed 1
                    digits: copy []
                    loop 100 [append digits random/only "0123456789"]
                    mold sort unique digits"""))
                    .isEqualTo("""
                            [#"0" #"1" #"2" #"3" #"4" #"5" #"6" #"7" #"8" #"9"]""");
        }

        @Test
        @DisplayName("only from the position onwards")
        void onlyFromThePositionOnwards() {
            assertThat(answerTo("""
                    random/seed 3
                    picked: copy []
                    loop 20 [append picked random/only next "0123456789"]
                    mold sort unique picked"""))
                    .as("the first digit is behind the position and cannot be picked")
                    .isEqualTo("""
                            [#"1" #"2" #"3" #"4" #"5" #"6" #"7" #"8"]""");
        }

        @Test
        @DisplayName("nothing at the tail")
        void nothingAtTheTail() {
            assertThat(answerTo("mold random/only tail {abc}")).isEqualTo("_");
        }

        @Test
        @DisplayName("an octet out of a binary")
        void anoctetOutOfABinary() {
            assertThat(answerTo("""
                    random/seed 1
                    mold reduce [
                        random/only #{0080ff41}
                        random/only #{0080ff41}
                        random/only #{0080ff41}
                        random/only #{0080ff41}
                    ]"""))
                    .isEqualTo("[255 65 0 0]");
        }

        @Test
        @DisplayName("and never an octet the character step can step back past")
        void neverAnOctetSteppedBackPast() {
            assertThat(answerTo("""
                    random/seed 7
                    picked: copy []
                    loop 50 [append picked random/only #{4180}]
                    mold unique picked"""))
                    .as("128 is a UTF-8 continuation byte, so the step back to a "
                            + "character boundary lands on the 65 in front of it")
                    .isEqualTo("[65]");
        }

        @Test
        @DisplayName("a two-byte character is picked twice as often as a one-byte one")
        void atwoByteCharacterIsPickedTwiceAsOften() {
            assertThat(answerTo("""
                    random/seed 7
                    twoByteOnes: 0
                    loop 4000 [
                        if 1 < length? to binary! to string! random/only "a^(E9)b" [
                            twoByteOnes: twoByteOnes + 1
                        ]
                    ]
                    all [twoByteOnes > 1700 twoByteOnes < 2300]"""))
                    .as("four bytes hold three characters, so the middle one owns two "
                            + "of the four offsets a pick can land on")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a vector has no arm for /ONLY")
        void avectorHasNoArmForOnly() {
            assertThat(whatHappensTo("random/only #(u8! [1 2 3])")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("while a block picks one of its values")
        void ablockPicksOneOfItsValues() {
            assertThat(answerTo("random/seed 1 random/only [a b c d e f g h i j]"))
                    .isEqualTo("i");
        }
    }

    @Nested
    @DisplayName("every datatype makes its seed differently")
    class MakingASeed {

        private static String firstNumberAfterSeeding(String seed) {
            return answerTo("random/seed " + seed + " random 1000000");
        }

        @Test
        @DisplayName("a decimal seeds with its bits, not its value")
        void adecimalSeedsWithItsBits() {
            assertThat(firstNumberAfterSeeding("1.5"))
                    .isNotEqualTo(firstNumberAfterSeeding("1"))
                    .isEqualTo(firstNumberAfterSeeding("to integer! #{3FF8000000000000}"));
        }

        @Test
        @DisplayName("a string seeds with a checksum of all its bytes")
        void astringSeedsWithAChecksumOfItsBytes() {
            assertThat(firstNumberAfterSeeding("{abc}"))
                    .isEqualTo(firstNumberAfterSeeding("checksum {abc} 'crc24"))
                    .isEqualTo(firstNumberAfterSeeding("#{616263}"));
        }

        @Test
        @DisplayName("including the bytes past the first character")
        void includingTheBytesPastTheFirstCharacter() {
            assertThat(firstNumberAfterSeeding("{a^(E9)}"))
                    .isEqualTo(firstNumberAfterSeeding("#{61C3A9}"))
                    .isNotEqualTo(firstNumberAfterSeeding("#{61C3}"));
        }

        @Test
        @DisplayName("a tuple seeds with a checksum of its octets")
        void atupleSeedsWithAChecksumOfItsOctets() {
            assertThat(firstNumberAfterSeeding("1.2.3"))
                    .isEqualTo(firstNumberAfterSeeding("#{010203}"));
        }

        @Test
        @DisplayName("a character seeds with its codepoint")
        void acharacterSeedsWithItsCodepoint() {
            assertThat(firstNumberAfterSeeding("first {a}"))
                    .isEqualTo(firstNumberAfterSeeding("97"));
        }

        @Test
        @DisplayName("a time seeds with its nanoseconds")
        void atimeSeedsWithItsNanoseconds() {
            assertThat(firstNumberAfterSeeding("0:00:01"))
                    .isEqualTo(firstNumberAfterSeeding("1000000000"));
        }

        @Test
        @DisplayName("a date packs its year, its day of the year and its time together")
        void adatePacksItsPartsTogether() {
            assertThat(firstNumberAfterSeeding("1-Feb-2000"))
                    .as("the year shifted up forty-eight bits and the thirty-second "
                            + "day of the year shifted up thirty-two")
                    .isEqualTo(firstNumberAfterSeeding("562950090860265472"));
        }

        @Test
        @DisplayName("and adds the time's nanoseconds when there is a time")
        void andaddsTheTimesNanoseconds() {
            assertThat(firstNumberAfterSeeding("1-Feb-2000/10:00"))
                    .isEqualTo(firstNumberAfterSeeding("562950090860265472 + 36000000000000"));
        }

        @Test
        @DisplayName("two dates a day apart seed differently")
        void twodatesADayApart() {
            assertThat(firstNumberAfterSeeding("1-Feb-2000"))
                    .isNotEqualTo(firstNumberAfterSeeding("2-Feb-2000"));
        }

        @Test
        @DisplayName("false seeds with one")
        void falseSeedsWithOne() {
            assertThat(firstNumberAfterSeeding("false"))
                    .isEqualTo(firstNumberAfterSeeding("1"));
        }

        @Test
        @DisplayName("a block has no arm for seeding")
        void ablockHasNoArmForSeeding() {
            assertThat(whatHappensTo("random/seed [1 2]")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("nor has a vector")
        void norhasAVector() {
            assertThat(whatHappensTo("random/seed #(u8! [1 2])")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("and a map is not something to make a seed out of")
        void amapIsNotSomethingToSeedWith() {
            assertThat(whatHappensTo("random/seed make map! [a 1]")).isEqualTo("cannot-use");
        }

        @Test
        @DisplayName("a seed sets the sequence, so the same seed replays it")
        void asameSeedReplaysTheSequence() {
            assertThat(answerTo("""
                    random/seed 42
                    first-run: collect [loop 5 [keep random 1000]]
                    random/seed 42
                    mold first-run = collect [loop 5 [keep random 1000]]"""))
                    .isEqualTo("#(true)");
        }
    }
}
