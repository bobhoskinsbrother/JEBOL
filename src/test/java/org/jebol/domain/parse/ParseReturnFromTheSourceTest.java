package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARSE's RETURN, which says what the whole PARSE answers instead of true or
 * false.
 *
 * <p>It is a prefix on the rule after it, like COPY with no word to put the
 * slice in: the rule has to match, and the span it matched becomes the answer.
 * Given a paren instead it answers whatever the paren evaluates to. Either way
 * it is a throw and not a result -- {@code Throw_Return_Series} in
 * {@code u-parse.c} -- so nothing after the rule runs and the value goes past
 * every enclosing block to the PARSE that started it.
 *
 * <p>JEBOL had it for a block and not for a string, and the two are separate
 * parsers. That stopped Rebol's own CSV codec at its first line and took the
 * whole of the csv file with it.
 */
class ParseReturnFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("parsing a string")
    class OverAString {

        @Test
        @DisplayName("the span the next rule matched is the answer")
        void theSpanIsTheAnswer() {
            assertThat(answerTo("""
                    reduce [
                        parse "abc" [return 2 skip]
                        parse "abc" [return skip]
                        parse "abc" [return to end]
                        parse "abc" [return 3 skip]
                    ]""")).isEqualTo("""
                    ["ab" "a" "abc" "abc"]""");
        }

        @Test
        @DisplayName("counted in characters, not in the bytes they take")
        void countedInCharacters() {
            assertThat(answerTo("""
                    reduce [
                        parse "ábč" [return 2 skip]
                        parse "ábč" [1 skip return to end]
                    ]""")).isEqualTo("""
                    ["áb" "bč"]""");
        }

        @Test
        @DisplayName("nothing after it runs, even the rest of the rule")
        void nothingAfterItRuns() {
            assertThat(answerTo("""
                    reduce [
                        parse "abc" ["a" return "b" "c"]
                        parse "abc" ["a" "b" "c" return to end]
                    ]""")).isEqualTo("""
                    ["b" ""]""");
        }

        @Test
        @DisplayName("and it climbs out of the block it is written in")
        void itClimbsOutOfItsBlock() {
            assertThat(answerTo("""
                    parse "abc" [some [return "b" | skip]]""")).isEqualTo("\"b\"");
        }

        @Test
        @DisplayName("a paren answers whatever it evaluates to")
        void aParenAnswersItsValue() {
            assertThat(answerTo("""
                    reduce [
                        parse "abc" [return (1 + 1)]
                        parse "abc" ["a" return ("done")]
                    ]""")).isEqualTo("""
                    [2 "done"]""");
        }

        @Test
        @DisplayName("a rule that matches nothing answers the empty span")
        void aRuleThatMatchesNothing() {
            assertThat(answerTo("""
                    reduce [
                        parse "abc" [return none]
                        parse "abc" [return opt "z"]
                    ]""")).isEqualTo("""
                    ["" ""]""");
        }

        @Test
        @DisplayName("and a rule that fails leaves PARSE to answer false as usual")
        void aRuleThatFails() {
            assertThat(answerTo("""
                    parse "abc" [return "x"]""")).isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("parsing a block, which already had it")
    class OverABlock {

        @Test
        @DisplayName("the answer is a block of what was matched")
        void theAnswerIsABlock() {
            assertThat(answerTo("""
                    reduce [
                        parse [1 2 3] [return 2 skip]
                        parse [1 2 3] [1 skip return to end]
                    ]""")).isEqualTo("[[1 2] [2 3]]");
        }

        @Test
        @DisplayName("a paren and a failing rule behave as they do over a string")
        void aParenAndAFailingRule() {
            assertThat(answerTo("""
                    reduce [parse [1 2 3] [return (9)] parse [1 2 3] [return 'x]]"""))
                    .isEqualTo("[9 #(false)]");
        }
    }
}
