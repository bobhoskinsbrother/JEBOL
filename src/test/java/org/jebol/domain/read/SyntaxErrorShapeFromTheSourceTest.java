package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a syntax error carries, and the three names a lone underscore cannot
 * take.
 *
 * <p>A script catching a syntax error reads its fields rather than its message:
 * ARG1 names the kind of token the reader was building, and NEAR is the line
 * number and the source line, written as R3 writes it -- {@code (line 2) 1d}.
 * Rebol's own suite asserts on both, and every case here is checked against the
 * R3 binary.
 *
 * <p>The underscore cases are the reason the fields matter. {@code _} is how
 * none is written, so it is not a word and cannot take a sigil: {@code '_},
 * {@code :_} and {@code _:} are each a mistake, and the error says which of the
 * three was being read.
 *
 * <p>Specified in {@code spec/load.allium} as LibraryFileLoad and in
 * {@code spec/natives.allium} under TRANSCODE.
 */
class SyntaxErrorShapeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a lone underscore cannot take a sigil")
    class TheNoneWord {

        @Test
        @DisplayName("a quoted one is refused, and the error says which was read")
        void aQuotedUnderscore() {
            assertThat(answerTo("e: transcode/error/one \"'_\" "
                    + "reduce [error? e e/id e/arg1]"))
                    .isEqualTo("[#(true) invalid \"word-lit\"]");
        }

        @Test
        @DisplayName("so is a read one")
        void aGetUnderscore() {
            assertThat(answerTo("e: transcode/error/one \":_\" "
                    + "reduce [error? e e/id e/arg1]"))
                    .isEqualTo("[#(true) invalid \"word-get\"]");
        }

        @Test
        @DisplayName("and an assigned one")
        void aSetUnderscore() {
            assertThat(answerTo("e: transcode/error/one \"_:\" "
                    + "reduce [error? e e/id e/arg1]"))
                    .isEqualTo("[#(true) invalid \"word-set\"]");
        }

        @Test
        @DisplayName("while a bare underscore is none, which is the whole point")
        void aBareUnderscoreIsNone() {
            assertThat(answerTo("none? transcode/one \"_\"")).isEqualTo(TRUE);
            assertThat(answerTo("2 = length? transcode \"[_ _]\"")).isEqualTo("#(false)");
            assertThat(answerTo("b: transcode/one \"[_ _]\" "
                    + "reduce [2 = length? b none? first b]"))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("NEAR carries the line number and the line")
    class TheNearField {

        @Test
        @DisplayName("written the way R3 writes it")
        void theShapeOfNear() {
            // The whole line rather than the offending token, because that is
            // what a person reading the error needs in order to look.
            assertThat(answerTo("e: try [transcode \"#(\"] e/near"))
                    .isEqualTo("\"(line 1) #(\"");
        }

        @Test
        @DisplayName("and the line it names is the line the failure is on")
        void theLineIsTheFailuresLine() {
            assertThat(answerTo("e: try [transcode \"1^/#(\"] e/near"))
                    .isEqualTo("\"(line 2) #(\"");
        }
    }

    @Nested
    @DisplayName("TRANSCODE/ERROR hands the failure back as a value")
    class ErrorAsAValue {

        @Test
        @DisplayName("including a source with nothing left to read")
        void nothingLeftToRead() {
            // /ERROR is TRY built into the reader, and it covers this failure
            // as it covers the rest. It used to raise instead, so a caller who
            // had asked for errors as values still had to catch one.
            assertThat(answerTo("e: transcode/one/error \"\" reduce [error? e e/id]"))
                    .isEqualTo("[#(true) past-end]");
            assertThat(answerTo("e: transcode/next/error \"\" reduce [error? e e/id]"))
                    .isEqualTo("[#(true) past-end]");
        }

        @Test
        @DisplayName("and without it the same read raises")
        void withoutItTheReadRaises() {
            assertThat(answerTo("e: try [transcode/one \"\"] "
                    + "reduce [error? e e/id]")).isEqualTo("[#(true) past-end]");
        }
    }
}
