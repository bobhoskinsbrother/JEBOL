package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Nested
    @DisplayName("what the mark survives, which is every copy")
    class WhatTheMarkSurvives {

        private static final String LAID_OUT = """
                src: [0
                1
                2]
                """;

        private static String marksOf(String expression) {
            return answerTo(LAID_OUT + "b: " + expression
                    + " reduce [new-line? b new-line? next b new-line? next next b]");
        }

        private static final String ONLY_THE_LAST_TWO = "[#(false) #(true) #(true)]";

        @Test
        @DisplayName("COPY keeps them, whole or in part")
        void copyKeepsThem() {
            assertThat(marksOf("copy src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(answerTo(LAID_OUT
                    + "b: copy/part src 2 reduce [new-line? b new-line? next b]"))
                    .isEqualTo("[#(false) #(true)]");
        }

        @Test
        @DisplayName("so do TO BLOCK! and TO PAREN!")
        void theConversionsKeepThem() {
            assertThat(marksOf("to block! src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(marksOf("to paren! src")).isEqualTo(ONLY_THE_LAST_TWO);
        }

        @Test
        @DisplayName("and the three that modify, spreading a block into another")
        void theModifyingThreeKeepThem() {
            assertThat(marksOf("append copy [] src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(marksOf("head insert copy [] src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(marksOf("head change copy [a b c] src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(marksOf("join [] src")).isEqualTo(ONLY_THE_LAST_TWO);
        }

        @Test
        @DisplayName("REDUCE and COMPOSE keep them, and REDUCE/INTO too")
        void theEvaluatingTwoKeepThem() {
            assertThat(marksOf("reduce src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(marksOf("compose src")).isEqualTo(ONLY_THE_LAST_TWO);
            assertThat(marksOf("head reduce/into src copy []"))
                    .isEqualTo(ONLY_THE_LAST_TWO);
        }

        @Test
        @DisplayName("REVERSE turns them round with the items and SORT sorts them")
        void thereorderingTwoMoveThem() {
            assertThat(answerTo(LAID_OUT + "mold/flat reverse copy src"))
                    .isEqualTo("\"[2 1 0]\"");
            assertThat(answerTo(LAID_OUT + "b: reverse copy src "
                    + "reduce [new-line? b new-line? next b new-line? next next b]"))
                    .isEqualTo("[#(true) #(true) #(false)]");
            assertThat(marksOf("sort copy src")).isEqualTo(ONLY_THE_LAST_TWO);
        }

        @Test
        @DisplayName("but a value REDUCE worked out arrives without one")
        void acomputedValueArrivesBare() {
            assertThat(answerTo("""
                    x: 5 b: reduce [0
                    1
                    x
                    add 1 1
                    ]
                    reduce [new-line? b new-line? next b
                            new-line? next next b new-line? next next next b]"""))
                    .isEqualTo("[#(false) #(true) #(false) #(false)]");
        }

        @Test
        @DisplayName("and which forms count as literals is the same question")
        void whichFormsCountAsLiterals() {
            for (String literal : new String[] {"1", "{a}", "[1]", "#(none)", "/a",
                "#(integer!)"}) {
                assertThat(answerTo(secondItemOfAReduceOver(literal)))
                        .as(literal).isEqualTo("#(true)");
            }
            for (String worksItOut : new String[] {"x", "'a", ":x", "(1)", "add 1 1"}) {
                assertThat(answerTo(secondItemOfAReduceOver(worksItOut)))
                        .as(worksItOut).isEqualTo("#(false)");
            }
        }

        private static String secondItemOfAReduceOver(String written) {
            return "x: 5 new-line? next reduce [0\n" + written + "\n]";
        }

        @Test
        @DisplayName("which is what makes a molded REDUCE keep its author's shape")
        void amoldedReduceKeepsItsShape() {
            assertThat(answerTo("""
                    v: make vector! [integer! 8 20]
                    mold reduce [
                    1 2
                    v
                    3 4
                    ]""")).isEqualTo("""
                    {[
                        1 2 #(int8! [
                            0 0 0 0 0 0 0 0 0 0
                            0 0 0 0 0 0 0 0 0 0
                        ])
                        3 4
                    ]}""");
        }
    }
}
