package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * INTO, read from {@code case SYM_INTO} in {@code u-parse.c}.
 *
 * <p>Four lines of C and three of them were missing here. It resolves the
 * rule after it with {@code Get_Parse_Value}, so a word naming a block is a
 * rule and a rule can therefore name itself and recurse. It steps into
 * {@code ANY_BINSTR(val) || ANY_BLOCK(val)}, so a string and a binary are as
 * good as a block. And a rule that is not a block once resolved is
 * {@code goto bad_rule}, an error rather than a failure to match.
 *
 * <p>Rebol's COMBINE is the thing that showed it. Its whole treatment of a
 * nested block is {@code block-rule: [ahead block! into rule]}, one line, and
 * without the word being resolved that line matched nothing: the delimiter
 * went between the top-level values and nowhere else, so
 * {@code combine/with [a [b c]] "--"} came out {@code "a--bc"} instead of
 * {@code "a--b--c"}.
 */
class IntoTakesAWordFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['worked]");
    }

    private static String textOf(String source) {
        String shown = answerTo(source);
        return isWrappedIn(shown, '"', '"') || isWrappedIn(shown, '{', '}')
                ? shown.substring(1, shown.length() - 1)
                : shown;
    }

    private static boolean isWrappedIn(String shown, char opening, char closing) {
        return shown.length() >= 2
                && shown.charAt(0) == opening
                && shown.charAt(shown.length() - 1) == closing;
    }

    @Nested
    @DisplayName("the rule after INTO is resolved before it is judged")
    class ResolvingTheRule {

        @Test
        @DisplayName("a word naming a block is the rule INTO applies")
        void awordNamingABlock() {
            assertThat(answerTo("""
                    inner: [some word!]
                    parse [[a b]] [into inner]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("so a rule can name itself and walk a tree of blocks")
        void aruleCanNameItself() {
            assertThat(textOf("""
                    seen: copy []
                    parse [a [b [c d]] e] walk: [
                        any [ahead block! into walk | set found word! (append seen found)]
                    ]
                    mold seen"""))
                    .isEqualTo("[a b c d e]");
        }

        @Test
        @DisplayName("a word holding something that is not a block is a bad rule")
        void awordHoldingSomethingElse() {
            assertThat(whatHappensTo("""
                    inner: 3
                    parse [[a]] [into inner]"""))
                    .isEqualTo("parse-rule");
        }

        @Test
        @DisplayName("and so is a literal that is not a block")
        void aliteralThatIsNotABlock() {
            assertThat(whatHappensTo("parse [[a]] [into 3]")).isEqualTo("parse-rule");
        }
    }

    @Nested
    @DisplayName("INTO steps into any string or any block, not a block alone")
    class WhatItStepsInto {

        @Test
        @DisplayName("a string at this position")
        void astring() {
            assertThat(answerTo("""
                    parse ["ab"] [into ["ab"]]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the inner rule has to reach that string's tail")
        void theInnerRuleReachesTheTail() {
            assertThat(answerTo("""
                    parse ["ab"] [into ["a"]]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a binary at this position")
        void abinary() {
            assertThat(answerTo("parse [#{0102}] [into [#{0102}]]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a paren, which is an any-block like the rest")
        void aparen() {
            assertThat(answerTo("parse [(a b)] [into [word! word!]]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nothing at all at the tail")
        void nothingAtTheTail() {
            assertThat(answerTo("parse [] [into [word!]]")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and an integer is not something to step into")
        void anintegerIsNotSteppedInto() {
            assertThat(answerTo("parse [3] [into [word!]]")).isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("COMBINE puts its delimiter between the values of a nested block too")
    class CombineOverANestedBlock {

        @Test
        @DisplayName("because the nested block is parsed by the same rule")
        void thenestedBlockIsParsedByTheSameRule() {
            assertThat(textOf("combine/with [a [b c]] {--}")).isEqualTo("a--b--c");
        }

        @Test
        @DisplayName("however deep the nesting goes")
        void howeverDeepTheNestingGoes() {
            assertThat(textOf("combine/with [a [b [c d]] e] {-}")).isEqualTo("a-b-c-d-e");
        }

        @Test
        @DisplayName("and /INTO writes the same run of values into the target")
        void intoWritesTheSameRun() {
            assertThat(textOf("mold combine/into/with [a [b c]] copy [x] {--}"))
                    .isEqualTo("""
                            [x "--" a "--" b "--" c]""");
        }

        @Test
        @DisplayName("while /ONLY keeps the nested block as one value")
        void onlyKeepsTheNestedBlockWhole() {
            assertThat(textOf("combine/only/with [a [b c]] {--}")).isEqualTo("a--[b c]");
        }

        @Test
        @DisplayName("and no delimiter still joins everything")
        void nodelimiterStillJoins() {
            assertThat(textOf("combine [a [b c]]")).isEqualTo("abc");
        }
    }
}
