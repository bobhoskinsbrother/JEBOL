package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EnbaseAndDebaseFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String bytesOfLength(int count) {
        return "(b: copy #{} loop " + count + " [append b 255] b)";
    }

    @Nested
    @DisplayName("base 36 is a codec for one sixty-four bit number")
    class BaseThirtySix {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'#{}',                     ''",
                "'#{00}',                   '0'",
                "'#{0000}',                 '0'",
                "'#{FF}',                   '73'",
                "'#{FFFFFFFFFFFFFF}',       'JPIA9PM8JR3'",
                "'#{FFFFFFFFFFFFFFFF}',     '3W5E11264SGSF'",
        })
        @DisplayName("ENBASE writes the fewest digits that carry it")
        void enbaseWritesTheFewestDigits(String given, String expected) {
            assertThat(answerTo("enbase " + given + " 36"))
                    .isEqualTo('"' + expected + '"');
        }

        @Test
        @DisplayName("and refuses more than eight bytes, there being no such number")
        void moreThanEightBytesIsOutOfRange() {
            assertThat(errorIdFrom("enbase " + bytesOfLength(9) + " 36"))
                    .isEqualTo("out-of-range");
            assertThat(errorIdFrom("enbase " + bytesOfLength(8) + " 36"))
                    .isEqualTo("no-error");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'\"\"',              '#{}'",
                "'\"0\"',             '#{0000000000000000}'",
                "'\"1\"',             '#{0000000000000001}'",
                "'\"Z\"',             '#{0000000000000023}'",
                "'\"z\"',             '#{0000000000000023}'",
                "'\"1Z\"',            '#{0000000000000047}'",
                "'\"ZZZZZZZZZZZZ\"',  '#{41C21CB8E0FFFFFF}'",
                "'\"ZZZZZZZZZZZZZ\"', '#{3F4C09FFA3FFFFFF}'",
        })
        @DisplayName("DEBASE answers eight bytes whatever the digits were")
        void debaseAnswersEightBytes(String given, String expected) {
            assertThat(answerTo("mold debase " + given + " 36"))
                    .isEqualTo('"' + expected + '"');
        }

        @Test
        @DisplayName("and reads thirteen digits at most, and only alphanumeric ones")
        void thirteenDigitsAtMostAndNothingOutsideTheAlphabet() {
            assertThat(errorIdFrom("debase \"ZZZZZZZZZZZZZZ\" 36"))
                    .isEqualTo("invalid-data");
            assertThat(errorIdFrom("debase \"!!\" 36")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("debase \"ZZZZZZZZZZZZZ\" 36")).isEqualTo("no-error");
        }

        @Test
        @DisplayName("so the two are not inverses, which is the point of the shape")
        void theTwoAreNotInverses() {
            assertThat(answerTo("mold debase enbase #{01} 36 36"))
                    .isEqualTo("\"#{0000000000000001}\"");
        }
    }

    @Nested
    @DisplayName("base 85 is Ascii85")
    class BaseEightyFive {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'\"\"',             ''",
                "'\"h\"',            'BE'",
                "'\"he\"',           'BOq'",
                "'\"hel\"',          'BOtu'",
                "'\"hell\"',         'BOu!r'",
                "'\"hello\"',        'BOu!rDZ'",
                "'\"hello world!\"', 'BOu!rD]j7BEbo80'",
        })
        @DisplayName("four bytes become five characters, and a short group one more")
        void fourBytesBecomeFiveCharacters(String given, String expected) {
            assertThat(answerTo("enbase " + given + " 85"))
                    .isEqualTo('"' + expected + '"');
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "'#{00}',                 '!!'",
                "'#{0000}',               '!!!'",
                "'#{000000}',             '!!!!'",
                "'#{00000000}',           'z'",
                "'#{0000000000000000}',   'zz'",
                "'#{000000000000000000}', 'zz!!'",
                "'#{FF00000000}',         'rr<$!!!'",
                "'#{FFD8FFE0}',           's4IA0'",
                "'#{FFFFFFFF}',           's8W-!'",
        })
        @DisplayName("and a whole zero group is the letter z, a partial one is not")
        void aWholeZeroGroupIsTheLetterZed(String given, String expected) {
            assertThat(answerTo("enbase " + given + " 85"))
                    .isEqualTo('"' + expected + '"');
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"B E", "B^-E", "B^/E", "B^ME"})
        @DisplayName("whitespace between characters is skipped when reading")
        void whitespaceIsSkippedWhenReading(String spaced) {
            assertThat(answerTo("mold debase {" + spaced + "} 85"))
                    .isEqualTo("\"#{68}\"");
        }

        @Test
        @DisplayName("and what it will not read is refused rather than guessed at")
        void whatItWillNotReadIsRefused() {
            assertThat(errorIdFrom("debase \"abcx\" 85")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("debase \"~>\" 85")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("debase {s8W-\"} 85")).isEqualTo("invalid-data");
            assertThat(errorIdFrom("debase \"u\" 85")).isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("and the round trip holds, because a short group records its length")
        void theRoundTripHolds() {
            assertThat(answerTo("""
                    every-one: true
                    foreach n [0 1 2 3 4 5 6 7 8 9 16 17] [
                        b: copy #{}
                        repeat i n [append b (i * 37) // 256]
                        unless b = debase enbase b 85 85 [every-one: false]
                    ]
                    every-one""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the newline goes before each line, not after")
    class TheLineBreaking {

        @ParameterizedTest(name = "base {0}, {1} bytes")
        @CsvSource({
                "2,  8,  false",
                "2,  9,  true",
                "16, 31, false",
                "16, 32, true",
                "64, 45, false",
                "64, 48, true",
        })
        @DisplayName("a break appears only once the input is long enough, counted in bytes")
        void aBreakAppearsOnlyOnceTheInputIsLongEnough(
                int base, int count, boolean broken) {

            assertThat(answerTo(
                    "true? find enbase " + bytesOfLength(count) + " " + base + " newline"))
                    .as("base %d at %d bytes", base, count)
                    .isEqualTo(broken ? "#(true)" : "#(false)");
        }

        @ParameterizedTest(name = "base {0}")
        @ValueSource(ints = {2, 16, 64})
        @DisplayName("and when it does appear, the answer starts with it")
        void theAnswerStartsWithTheBreak(int base) {
            assertThat(answerTo(
                    "newline = first enbase " + bytesOfLength(64) + " " + base))
                    .as("base %d", base)
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an input that divides exactly ends with one too")
        void anInputThatDividesExactlyEndsWithOne() {
            assertThat(answerTo(
                    "newline = last enbase " + bytesOfLength(32) + " 16"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("except base 2 at exactly eight bytes, which the C takes back")
        void exceptBaseTwoAtExactlyEightBytes() {
            assertThat(answerTo(
                    "(enbase " + bytesOfLength(8) + " 2) = {" + "1".repeat(64) + "}"))
                    .isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "base {0}")
        @ValueSource(ints = {2, 16, 64})
        @DisplayName("and /FLAT has none of it")
        void flatHasNoneOfIt(int base) {
            assertThat(answerTo(
                    "true? find enbase/flat " + bytesOfLength(64) + " " + base
                            + " newline"))
                    .as("base %d", base)
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("/PART may name a position, and it may be behind where we stand")
    class ThePartThatReachesBack {

        @Test
        @DisplayName("a position ahead bounds the span forwards")
        void aPositionAheadBoundsForwards() {
            assertThat(answerTo("enbase/part s: \"abcd\" 64 skip s 2"))
                    .isEqualTo("\"YWI=\"");
            assertThat(answerTo("mold debase/part s: \"YWI=XXXX\" 64 find s #\"X\""))
                    .isEqualTo("\"#{6162}\"");
        }

        @Test
        @DisplayName("and a position behind names the run leading up to it")
        void aPositionBehindNamesTheRunLeadingUpToIt() {
            assertThat(answerTo("enbase/part s: tail \"abcd\" 64 skip s -2"))
                    .isEqualTo("\"Y2Q=\"");
            assertThat(answerTo("""
                    mold debase/part s: tail "XXXYWI=" 64 find/tail/reverse s #"X\""""))
                    .isEqualTo("\"#{6162}\"");
        }

        @Test
        @DisplayName("a negative count says the same thing the shorter way")
        void aNegativeCountSaysTheSameThing() {
            assertThat(answerTo("enbase/part s: tail \"abcd\" 64 -2"))
                    .isEqualTo("\"Y2Q=\"");
            assertThat(answerTo("mold debase/part s: tail \"XXXYWI=\" 64 -4"))
                    .isEqualTo("\"#{6162}\"");
        }

        @Test
        @DisplayName("and it is clamped to what is actually behind, which at the head is none")
        void itIsClampedToWhatIsActuallyBehind() {
            assertThat(answerTo("enbase/part s: \"abcd\" 64 -2")).isEqualTo("\"\"");
            assertThat(answerTo("enbase/part s: skip \"abcd\" 1 64 -9"))
                    .isEqualTo("\"YQ==\"");
        }
    }

    @Nested
    @DisplayName("/URL swaps two characters and drops the padding")
    class TheUrlVariant {

        @Test
        @DisplayName("where the plain alphabet pads, the URL one stops")
        void theUrlVariantDoesNotPad() {
            assertThat(answerTo("enbase \"a\" 64")).isEqualTo("\"YQ==\"");
            assertThat(answerTo("enbase \"aa\" 64")).isEqualTo("\"YWE=\"");
            assertThat(answerTo("enbase/url \"a\" 64")).isEqualTo("\"YQ\"");
            assertThat(answerTo("enbase/url \"aa\" 64")).isEqualTo("\"YWE\"");
        }

        @Test
        @DisplayName("and a full group needs no padding either way, so the two agree")
        void aFullGroupAgrees() {
            assertThat(answerTo("(enbase \"abc\" 64) = (enbase/url \"abc\" 64)"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the decoder reads its own output back, padded or not")
        void theDecoderReadsItsOwnOutputBack() {
            assertThat(answerTo("""
                    key: "qL8R4QIcQ_ZsRqOAbeRfcZhilN_MksRtDaErMA"
                    bin: debase/url key 64
                    reduce [binary? bin  key = enbase/url bin 64]"""))
                    .isEqualTo("[#(true) #(true)]");
        }
    }
}
