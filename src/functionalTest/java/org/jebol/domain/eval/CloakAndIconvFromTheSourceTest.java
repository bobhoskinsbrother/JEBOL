package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloakAndIconvFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("ENCLOAK and DECLOAK")
    class TheScrambler {

        @Test
        @DisplayName("scrambling then unscrambling gives back what went in")
        void undoesItself() {
            assertThat(answerTo(
                    "b: #{00112233445566} was: copy b "
                    + "was = decloak encloak b \"key\" \"key\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the scrambled form is not the original")
        void actuallyScrambles() {
            assertThat(answerTo(
                    "b: #{00112233445566} was: copy b "
                    + "was <> encloak b \"key\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it changes the binary it was given, and answers it")
        void changesInPlace() {
            assertThat(answerTo(
                    "b: #{0011223344} was: copy b encloak b \"k\" b <> was"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a different key gives a different result")
        void dependsOnTheKey() {
            assertThat(answerTo(
                    "a: #{00112233} b: copy a "
                    + "(encloak a \"one\") <> encloak b \"two\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the wrong key does not give the data back")
        void failsToUnscrambleWithTheWrongKey() {
            assertThat(answerTo(
                    "b: #{00112233445566} was: copy b "
                    + "was <> decloak encloak b \"right\" \"wrong\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a one-byte key works, because the key is hashed to twenty")
        void worksWithAShortKey() {
            assertThat(answerTo(
                    "b: #{00112233} was: copy b "
                    + "was = decloak encloak b \"k\" \"k\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/WITH uses the key's own bytes rather than hashing them")
        void usesTheKeyAsItStandsWithWith() {
            assertThat(answerTo(
                    "a: #{00112233} b: copy a "
                    + "(encloak a \"key\") <> encloak/with b \"key\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /WITH still round-trips, with /WITH on both halves")
        void roundTripsWithWith() {
            assertThat(answerTo(
                    "b: #{00112233445566} was: copy b "
                    + "was = decloak/with encloak/with b \"key\" \"key\""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a binary key works as well as a string")
        void worksWithABinaryKey() {
            assertThat(answerTo(
                    "b: #{00112233} was: copy b "
                    + "was = decloak encloak b #{DEAD} #{DEAD}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an integer key is spelled in decimal, and always hashed")
        void alwaysHashesAnIntegerKey() {
            assertThat(answerTo(
                    "b: #{00112233} was: copy b "
                    + "was = decloak encloak b 1234 1234")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "a: #{00112233} b: copy a "
                    + "(encloak a 99) = encloak/with b 99")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it agrees with the same digits written as a string")
        void agreesForAnIntegerAndItsDigits() {
            assertThat(answerTo(
                    "a: #{00112233} b: copy a "
                    + "(encloak a 1234) = encloak b \"1234\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("empty data is left alone rather than refused")
        void leavesEmptyDataAlone() {
            assertThat(answerTo("#{} = encloak #{} \"key\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a one-byte binary scrambles, because the first byte is always touched")
        void stillScramblesOneByte() {
            assertThat(answerTo("#{00} <> encloak #{00} \"key\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an empty key is refused")
        void refusesAnEmptyKey() {
            assertThat(readTheErrorIdFrom("encloak #{0011} \"\"")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("encloak #{0011} #{}")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a protected binary is refused before anything is written")
        void refusesAProtectedBinary() {
            assertThat(readTheErrorIdFrom("b: protect #{00112233} encloak b \"key\""))
                    .isEqualTo("protected");
        }

        @Test
        @DisplayName("only a binary is taken, because text has no bytes to scramble")
        void onlyABinaryIsTaken() {
            assertThat(readTheErrorIdFrom("encloak \"text\" \"key\"")).isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("ICONV")
    class ChangingCharacterSet {

        @Test
        @DisplayName("reads a binary as text in a named character set")
        void readsText() {
            assertThat(answerTo("iconv #{616263} 'utf8")).isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("a set where the bytes mean something else gives different text")
        void dependsOnTheSet() {
            assertThat(answerTo("(iconv #{41E9} 'latin1) <> \"A\"")).isEqualTo(TRUE);
            assertThat(answerTo("2 = length? iconv #{41E9} 'latin1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/TO answers a binary, unless the target is UTF-8")
        void answersABinaryForTheToForm() {
            assertThat(answerTo("binary? iconv/to #{4142} 'utf8 'utf16le"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("string? iconv/to #{4142} 'utf8 'utf8"))
                    .as("this test asserted a binary here and was wrong. "
                            + "`if (tp == CP_UTF8) SET_STRING(D_RET, src_ser);` -- "
                            + "UTF-8 is how a REBOL string is already held, so there "
                            + "is nothing to convert and nothing to hand back as "
                            + "octets. Rebol's own series-test.r3 says the same in "
                            + "three assertions")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and transcoding widens a Latin-1 byte into two UTF-8 bytes")
        void widensWhenTranscoding() {
            assertThat(answerTo("to binary! iconv/to #{E9} 'latin1 'utf8"))
                    .as("the widening still happens; it is the answer's datatype "
                            + "that this test had wrong")
                    .isEqualTo("#{C3A9}");
        }

        @Test
        @DisplayName("a name is taken as a word, a string or a tag")
        void severalSpellings() {
            assertThat(answerTo("iconv #{616263} 'utf8")).isEqualTo("\"abc\"");
            assertThat(answerTo("iconv #{616263} \"utf8\"")).isEqualTo("\"abc\"");
            assertThat(answerTo("iconv #{616263} <utf8>")).isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("a character set the host has not got is refused")
        void refusesAnUnknownSet() {
            assertThat(readTheErrorIdFrom("iconv #{6162} 'invented-codepage"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("empty data answers empty")
        void keepsEmptyEmpty() {
            assertThat(answerTo("\"\" = iconv #{} 'utf8")).isEqualTo(TRUE);
        }
    }
}
