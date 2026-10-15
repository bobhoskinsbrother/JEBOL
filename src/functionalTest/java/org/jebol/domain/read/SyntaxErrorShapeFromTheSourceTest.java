package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
