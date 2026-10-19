package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PercentWordFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheRefusalOf(String source) {
        return answerTo("e: try [transcode/one " + source + "] "
                + "all [error? e e/id = 'invalid e/arg1 = \"path\"]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a run of percent signs is a word")
    class ThePercentWord {

        @Test
        @DisplayName("on its own, however long the run")
        void readsTheRunAlone() {
            assertThat(answerTo("""
                    (transcode/one {%}) = to word! "%\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {%%}) = to word! "%%\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it takes a sigil like any other word")
        void takesASigil() {
            assertThat(answerTo("""
                    (transcode/one {'%}) = to lit-word! "%\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {:%}) = to get-word! "%\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (transcode/one {'%%}) = to lit-word! "%%\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a percent that begins a name is a file, not a word")
        void givesWayToAFileWhenAPercentBeginsAName() {
            assertThat(answerTo("""
                    file? transcode/one {%a}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    file? transcode/one {%/}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a slash after a sigil'd one makes a path that cannot finish")
    class TheTrailingSlash {

        @Test
        @DisplayName("all four spellings Rebol's own test asserts")
        void refusesTheFourSpellings() {
            assertThat(readTheRefusalOf("""
                    {'%/}""")).isEqualTo(TRUE);
            assertThat(readTheRefusalOf("""
                    {:%/}""")).isEqualTo(TRUE);
            assertThat(readTheRefusalOf("""
                    {'%%/}""")).isEqualTo(TRUE);
            assertThat(readTheRefusalOf("""
                    {:%%/}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the error says path rather than word, because that is how far it got")
        void reportsPathAsTheKind() {
            assertThat(answerTo("""
                    e: try [transcode/one {'%/}] e/arg1""")).isEqualTo("\"path\"");
        }

        @Test
        @DisplayName("and every other missing segment reports the same type")
        void reportsTheSameKindForAnyMissingSegment() {
            assertThat(readTheRefusalOf("""
                    {a/}""")).isEqualTo(TRUE);
            assertThat(readTheRefusalOf("""
                    {a//b}""")).isEqualTo(TRUE);
            assertThat(readTheRefusalOf("""
                    {'a/}""")).isEqualTo(TRUE);
        }
    }
}
