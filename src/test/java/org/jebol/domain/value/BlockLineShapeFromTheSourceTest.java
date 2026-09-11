package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * The mark belongs to the value rather than to the position it sits in.
     * {@code OPTS_LINE} is a bit in the value's own header -- "Line break
     * occurs before this value" -- so every copy of the value copies it.
     *
     * <p>Which is why a long list of operations all keep it without any of
     * them knowing about lines: they copy the value. JEBOL keeps the marks in
     * a set of positions on the storage instead, which is the right shape for
     * a language whose values are shared, and it means each of those
     * operations has to carry the marks itself. None of them did, so a block
     * laid out over five lines came back on one the moment anything copied it
     * -- and every block a script builds is copied.
     */
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

        /**
         * The marks move with the items they precede rather than staying
         * where they were, because they are not where they were: they are on
         * the values.
         */
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

        /**
         * The same rule read the other way. REDUCE pushes a literal as it
         * stands and keeps its mark; where the expression was a word or a
         * call, what it pushes is the answer, and the answer has no mark of
         * its own.
         */
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

        /**
         * A literal of any datatype keeps it and every form that evaluates to
         * something else loses it, which is one rule and not a list. A
         * refinement and a datatype value both evaluate to themselves and both
         * keep theirs; a lit-word evaluates to a word, which is a different
         * value, and does not.
         */
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

        /**
         * Real line feeds rather than {@code ^/}, because a caret outside a
         * string is not an escape: {@code reduce [0^/1^/]} is not three lines
         * and does not read at all.
         */
        private static String secondItemOfAReduceOver(String written) {
            return "x: 5 new-line? next reduce [0\n" + written + "\n]";
        }

        /**
         * The whole reason the marks have to survive a copy: Rebol's own
         * vector test molds a REDUCE of a block written over three lines and
         * compares the bytes.
         */
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
