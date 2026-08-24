package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ICONV's codepage numbers, and BREAK inside REMOVE-EACH.
 *
 * <p>Two unrelated gaps that Rebol's own {@code series-test.r3} covers and
 * nothing here did, because that file would not load until {@code vector!}
 * existed.
 */
class IconvAndRemoveEachFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String theTextOf(String source) {
        return answerTo(source).replace("\"", "");
    }

    @Nested
    @DisplayName("a character set named by its Windows codepage number")
    class Bynumber {

        @ParameterizedTest
        @CsvSource({
                "28592,  'ISO 8859-2'",
                "65001,  'UTF-8'",
                "1200,   'UTF-16 little endian'",
                "1201,   'UTF-16 big endian'",
                "1250,   'Windows Central European'",
        })
        @DisplayName("resolves, where the JVM knows only names")
        void anumberResolves(String codepage, String describedAs) {
            assertThat(answerTo("error? try [iconv #{41} " + codepage + "]"))
                    .as("%s is codepage %s in Rebol's own table", describedAs, codepage)
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and decodes the octets that number's encoding says")
        void anumberDecodes() {
            assertThat(theTextOf("iconv #{50F869686CE1736974} 28592"))
                    .isEqualTo("Přihlásit");
            assertThat(theTextOf("iconv #{C5A1} 65001")).isEqualTo("š");
            assertThat(theTextOf("iconv #{C5A1} 'CP65001"))
                    .as("the same table gives the CP-prefixed word spelling too")
                    .isEqualTo("š");
        }

        @Test
        @DisplayName("a number for an encoding the host has not got is still refused")
        void anencodingTheHostLacks() {
            assertThat(answerTo("either error? e: try [iconv #{41} 20905] [e/id] ['read]"))
                    .as("Rebol's table lists a hundred and thirty-five encodings no "
                            + "JVM ships, and guessing a near one would be worse than "
                            + "saying so")
                    .isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("ICONV/TO, which answers text or octets depending on the target")
    class Transcoding {

        @Test
        @DisplayName("a UTF-8 target gives back a string, not a binary")
        void toutf8GivesAString() {
            assertThat(answerTo("string? iconv/to #{9AE96D} 1250 65001"))
                    .as("`if (tp == CP_UTF8) SET_STRING(D_RET, src_ser);` -- UTF-8 is "
                            + "how a REBOL string is held, so there is nothing left "
                            + "to convert and no reason to hand back octets")
                    .isEqualTo("#(true)");
            assertThat(theTextOf("iconv/to #{9AE96D} 1250 65001")).isEqualTo("šém");
        }

        @Test
        @DisplayName("and any other target gives back the octets")
        void toanythingElseGivesOctets() {
            assertThat(answerTo("binary? iconv/to #{50F8} 28592 'UTF-16LE"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("iconv/to #{50F8} 28592 'UTF-16LE"))
                    .isEqualTo("#{50005901}");
            assertThat(answerTo("iconv/to #{50F8} 28592 'UTF-16BE"))
                    .isEqualTo("#{00500159}");
        }

        @Test
        @DisplayName("named however the caller likes")
        void thenamesMayBeWrittenFourWays() {
            assertThat(theTextOf("iconv/to #{9AE96D} 1250 'utf8")).isEqualTo("šém");
            assertThat(theTextOf("iconv/to #{9AE96D} {CP1250} {utf8}")).isEqualTo("šém");
            assertThat(theTextOf("iconv/to #{9AE96D} <CP1250> <UTF-8>")).isEqualTo("šém");
        }
    }

    @Nested
    @DisplayName("BREAK inside REMOVE-EACH")
    class BreakingOut {

        @Test
        @DisplayName("stops there and keeps everything from that item on")
        void breakKeepsTheRest() {
            assertThat(answerTo("""
                    mold remove-each n s: [1 2 3 4] [if n = 2 [break] true]""")
                    .replace("\"", ""))
                    .as("the removals already decided stand, and the item BREAK "
                            + "happened on is not one of them")
                    .isEqualTo("[2 3 4]");
            assertThat(answerTo("""
                    remove-each n s: [1 2 3 4] [if n = 2 [break] true]
                    mold s""").replace("\"", "")).isEqualTo("[2 3 4]");
        }

        @Test
        @DisplayName("/COUNT still counts what was removed before it stopped")
        void countStillCounts() {
            assertThat(answerTo(
                    "remove-each/count n s: [1 2 3 4] [if n = 2 [break] true]"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("and BREAK/RETURN answers its value instead")
        void breakReturnAnswersItsValue() {
            assertThat(answerTo(
                    "remove-each n s: [1 2 3 4] [if n = 2 [break/return 'x] true]"))
                    .isEqualTo("x");
            assertThat(answerTo(
                    "remove-each/count n s: [1 2 3 4] [if n = 2 [break/return 'x] true]"))
                    .as("even with /COUNT, because the value BREAK carried is what "
                            + "the caller asked for")
                    .isEqualTo("x");
            assertThat(answerTo("""
                    remove-each n s: [1 2 3 4] [if n = 2 [break/return 'x] true]
                    mold s""").replace("\"", "")).isEqualTo("[2 3 4]");
        }

        @Test
        @DisplayName("with nothing to break out of, it removes as it always did")
        void withoutABreak() {
            assertThat(answerTo("mold remove-each n s: [1 2 3 4] [n < 3]")
                    .replace("\"", "")).isEqualTo("[3 4]");
        }
    }

    @Nested
    @DisplayName("REMOVE-EACH over a vector")
    class Overavector {

        @Test
        @DisplayName("reads each number and drops the ones the body picks")
        void overavector() {
            assertThat(answerTo("mold to block! remove-each v #(u16! [3 1 2 3]) [v < 3]")
                    .replace("\"", "")).isEqualTo("[3 3]");
        }
    }
}
