package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscodeOneFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("what follows the value is not read")
    class TheRestIsIgnored {

        @Test
        @DisplayName("a closing bracket that opens nothing is not noticed")
        void anExtraCloseIsNotNoticed() {
            assertThat(answerTo("""
                    '< = transcode/one/error {<]>}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    '< = transcode/one/error {<)>}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and neither is anything else that would fail")
        void otherRubbishIsNotNoticed() {
            assertThat(answerTo("""
                    1 = transcode/one {1]}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    1 = transcode/one {1 )}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    1 = transcode/one {1 "unterminated}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a second good value is left alone too")
        void aSecondValueIsLeft() {
            assertThat(answerTo("""
                    1 = transcode/one {1 2}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    'a = transcode/one {a b c}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /PART with a bracket inside the bound still answers the value")
        void withAPartBound() {
            assertThat(answerTo("""
                    123 == transcode/part/one {123]} 4""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    123 == transcode/part/one {123]} 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a whole block counts as one value")
        void aBlockIsOneValue() {
            assertThat(answerTo("""
                    (transcode/one {[1 2] 3}) = [1 2]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {(1 2) 3}) = quote (1 2)""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("but the value's own boundary is the reader's")
    class TheBoundaryIsTheReaders {

        @Test
        @DisplayName("a token that fails still fails, however well a shorter piece of it reads")
        void aFailingTokenStillFails() {
            assertThat(answerTo("""
                    e: try [transcode/one {'%/}] all [error? e e/arg1 = "path"]"""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("""
                    error? try [transcode/one {a/b<}]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an unfinished value is a failure rather than an answer of none")
        void anUnfinishedValue() {
            assertThat(answerTo("""
                    error? transcode/one/error {#(}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    error? transcode/one/error {[1 2}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an empty source fails rather than answering none")
        void anEmptySource() {
            assertThat(answerTo("""
                    error? transcode/one/error {}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("while a source that really holds none answers none")
        void aRealNone() {
            assertThat(answerTo("""
                    none? transcode/one {_}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    none? transcode/one {#(none)}""")).isEqualTo(TRUE);
        }
    }
}
