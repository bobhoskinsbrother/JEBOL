package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A lexeme opening with a digit is a number, and a bad one is a failure rather than a
 * name. From {@code LEX_CLASS_NUMBER} in {@code rebol3-source/src/core/l-scan.c}.
 *
 * <p>A word cannot begin with a digit, so once every numeric reading has been tried
 * there is nothing left for `1d` to be. R3 never reaches a word from there and reports
 * a malformed integer.
 *
 * <p>The case label carries the warning that makes this work:
 *
 * <pre>
 * case LEX_CLASS_NUMBER:      /* order of tests is important *&#47;
 * num:
 *     if (HAS_LEX_FLAG(flags, LEX_SPECIAL_LESSER)) {   /* 1&lt;tag&gt; 1.1&lt;tag&gt; *&#47;
 *         scan_state-&gt;end = Skip_To_Char(cp, scan_state-&gt;end, '&lt;');
 *         flags = Prescan_Part(scan_state, scan_state-&gt;end - cp);
 *     }
 *     if (!flags) return TOKEN_INTEGER;
 * </pre>
 *
 * <p>The angle bracket is cut off <em>first</em>, so {@code 1<} is the number and the
 * word before the refusal can see it. Refuse first and the whole `<` family goes with
 * it -- which two earlier attempts here did, at about twenty assertions each.
 *
 * <p>And it needed a third thing that looks unrelated: `0:0.001` had to become a time.
 * It was falling through to a word, and refusing words meant `mezz-debug.reb` stopped
 * loading. See {@link TimeLiteralFromTheSourceTest}. Three changes, none of which
 * works alone.
 */
class MalformedNumberFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String refusedAsAnInteger(String source) {
        return answerTo("e: try [load " + source + "] "
                + "all [error? e e/id = 'invalid e/arg1 = \"integer\"]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a number run into letters is a bad number, not a word")
    class TheRefusal {

        @Test
        @DisplayName("which is the token Rebol's own suite uses as its canonical bad one")
        void theCanonicalBadToken() {
            assertThat(refusedAsAnInteger("""
                    {1d}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    error? try [load {1 1d}]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and any other letters after the digits")
        void otherLetters() {
            assertThat(refusedAsAnInteger("""
                    {1a}""")).isEqualTo(TRUE);
            assertThat(refusedAsAnInteger("""
                    {12abc}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but letters after a letter are an ordinary word")
        void aWordIsStillAWord() {
            assertThat(answerTo("""
                    (load {a1}) = to word! "a1\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {a1b2}) = to word! "a1b2\"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and every real number still reads, which is the hard part")
    class WhatMustSurvive {

        @Test
        @DisplayName("the plain numbers")
        void plainNumbers() {
            assertThat(answerTo("""
                    integer? load {1}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    decimal? load {1.5}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    1e3 = 1000.0""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and every other datatype that opens with a digit")
        void theOtherDigitLeadingDatatypes() {
            assertThat(answerTo("""
                    pair? load {1x2}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    tuple? load {1.2.3}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    time? load {12:34}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    date? load {19-Jan-2010}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    binary? load {2#{01}}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    1.#INF > 0""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the time shape that had to be fixed first")
        void theTimeThatWasAWord() {
            assertThat(answerTo("""
                    time? load {0:0.001}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a based binary is a number too, so a bad base is a bad number")
    class ABadBase {

        @Test
        @DisplayName("a base that is not 2, 16 or 64")
        void anUnsupportedBase() {
            assertThat(refusedAsAnInteger("""
                    {000016#{FF}}""")).isEqualTo(TRUE);
            assertThat(refusedAsAnInteger("""
                    {3#{01}}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a base may not carry a sign")
        void aSignedBase() {
            assertThat(refusedAsAnInteger("""
                    {+2#{}}""")).isEqualTo(TRUE);
            assertThat(refusedAsAnInteger("""
                    {-2#{01}}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a hash form other than a binary is not a base at all")
        void aHashThatIsNotABinary() {
            assertThat(refusedAsAnInteger("""
                    {2#"a"}""")).isEqualTo(TRUE);
            assertThat(refusedAsAnInteger("""
                    {1#(logic! 1)}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but the three bases that exist still read")
        void theThreeBases() {
            assertThat(answerTo("""
                    2#{01} = #{40}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    16#{FF} = #{FF}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    64#{TQ==} = #{4D}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and the angle bracket is settled before the refusal, not after")
    class TheOrderingThatMakesItWork {

        @Test
        @DisplayName("a number ends at the bracket and what follows is read afresh")
        void theBracketStillSplits() {
            assertThat(answerTo("""
                    mold load {1<}""")).isEqualTo("\"[1 <]\"");
            assertThat(answerTo("""
                    mold load {1.2<}""")).isEqualTo("\"[1.2 <]\"");
            assertThat(answerTo("""
                    mold load {1.0<a>}""")).isEqualTo("\"[1.0 <a>]\"");
            assertThat(answerTo("""
                    mold load {1.#INF<}""")).isEqualTo("\"[1.#INF <]\"");
            assertThat(answerTo("""
                    mold load {19-Jan-2010<}""")).isEqualTo("\"[19-Jan-2010 <]\"");
        }

        @Test
        @DisplayName("and what the bracket leaves behind still has to be a value")
        void whatIsLeftMustReadToo() {
            assertThat(answerTo("""
                    error? try [load {1<2}]""")).isEqualTo(TRUE);
        }
    }
}
