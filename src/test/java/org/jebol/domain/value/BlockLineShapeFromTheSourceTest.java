package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A block molds back the shape its author laid it out in.
 *
 * <p>The scanner marks every value that follows a line feed and MOLD writes a
 * break before each marked one, so source that ran over five lines comes back
 * over five lines. JEBOL recorded none of it: every loaded block molded on one
 * line, which is a different program every time anybody saved one.
 *
 * <p>Three rules decide where the breaks go, and none of them is the obvious
 * one. The break comes *before* the value, so the mark means "this begins a
 * line". The indent goes up once and only for a mark on the first value, so a
 * block laid out over ten lines is indented by one level rather than ten. And
 * a line feed with nothing after it is forgotten -- the C sets the flag on the
 * last value it read and then copies the block without it.
 */
class BlockLineShapeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("what the reader marks")
    class TheMarks {

        @Test
        @DisplayName("a value after a line feed begins a line")
        void aValueAfterALineFeedBeginsALine() {
            assertThat(answerTo("""
                    b: load {[1^/2^/3]}
                    reduce [new-line? b new-line? next b new-line? next next b]"""))
                    .isEqualTo("[#(false) #(true) #(true)]");
        }

        @Test
        @DisplayName("and it survives being written into a script and run")
        void itSurvivesTheInterpreter() {
            assertThat(answerTo("""
                    b: [1
                    2]
                    new-line? next b""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("several line feeds in a row mark once, not once each")
        void severalLineFeedsMarkOnce() {
            assertThat(answerTo("""
                    mold load {[1^/^/^/2]}""")).isEqualTo("\"[1^/2]\"");
        }

        @Test
        @DisplayName("a comment's own newline marks the value after it")
        void aCommentMarksWhatFollows() {
            assertThat(answerTo("""
                    mold load {[1 ; note^/2]}""")).isEqualTo("\"[1^/2]\"");
        }

        @Test
        @DisplayName("each block keeps its own marks, a nested one saying nothing")
        void eachBlockKeepsItsOwn() {
            assertThat(answerTo("""
                    mold load {[[1^/2]^/[3]]}"""))
                    .isEqualTo("\"[[1^/2]^/[3]]\"");
        }
    }

    @Nested
    @DisplayName("where the breaks go")
    class TheBreaks {

        @Test
        @DisplayName("a break in the middle is bare, with no indent")
        void aBreakInTheMiddleIsBare() {
            assertThat(answerTo("""
                    mold load {[1^/2]}""")).isEqualTo("\"[1^/2]\"");
        }

        @Test
        @DisplayName("a break before the first item steps in, and back out for the bracket")
        void aBreakBeforeTheFirstStepsIn() {
            assertThat(answerTo("""
                    mold load {[^/1 2]}""")).isEqualTo("""
                    "[^/    1 2^/]\"""");
        }

        @Test
        @DisplayName("a line feed with nothing after it is forgotten")
        void aTrailingLineFeedIsForgotten() {
            assertThat(answerTo("""
                    reduce [mold load {[1 2^/]} mold load {[^/]}]"""))
                    .isEqualTo("[\"[1 2]\" \"[]\"]");
        }

        @Test
        @DisplayName("the indent is one level whatever the nesting of breaks")
        void theIndentIsOneLevel() {
            assertThat(answerTo("""
                    mold load {[[[^/1^/]]]}""")).isEqualTo("""
                    "[[[^/    1^/]]]\"""");
        }

        @Test
        @DisplayName("MOLD/FLAT writes none of them")
        void flatWritesNone() {
            assertThat(answerTo("""
                    mold/flat load {[^/1^/2^/]}""")).isEqualTo("\"[1 2]\"");
        }

        @Test
        @DisplayName("MOLD/ONLY has no bracket to break against, so the first is bare")
        void onlyHasNoBracketToBreakAgainst() {
            assertThat(answerTo("""
                    reduce [mold/only load {[1^/2^/3]} mold/only load {[^/1 2]}]"""))
                    .isEqualTo("[\"1^/2^/3\" \"1 2\"]");
        }
    }

    @Nested
    @DisplayName("what carries the marks, and what would have dropped them")
    class WhatCarriesThem {

        @Test
        @DisplayName("a paren keeps them as a block does")
        void aParenKeepsThem() {
            assertThat(answerTo("""
                    mold load {[(1^/2)]}""")).isEqualTo("\"[(1^/2)]\"");
        }

        @Test
        @DisplayName("NEW-LINE sets one and NEW-LINE/ALL sets every one")
        void newLineSetsThem() {
            assertThat(answerTo("""
                    b: copy [1 2 3]
                    new-line/all b true
                    mold b""")).isEqualTo("""
                    {[
                        1
                        2
                        3
                    ]}""");
        }

        @Test
        @DisplayName("an object's fields step in, and a block inside one steps in again")
        void anObjectStepsIn() {
            assertThat(answerTo("""
                    m: make map! [
                        a: 1
                        c: [
                            3 4
                        ]
                    ]
                    mold m""")).isEqualTo("""
                    {#[
                        a: 1
                        c: [
                            3 4
                        ]
                    ]}""");
        }

        @Test
        @DisplayName("and BODY-OF an object writes each field on its own line")
        void bodyOfWritesEachFieldOnItsOwnLine() {
            assertThat(answerTo("""
                    mold make object! [a: 1 b: [2 3]]""")).isEqualTo("""
                    {make object! [
                        a: 1
                        b: [2 3]
                    ]}""");
        }
    }
}
