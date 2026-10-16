package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LookingForNothingFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("an empty pattern is found nowhere")
    class AnEmptyPattern {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "find {Keyboard} {}",
                "find/match {Keyboard} {}",
                "find/any {Keyboard} {}",
                "find/match/any {Keyboard} {}",
                "find/last {abc} {}",
                "find/reverse tail {abc} {}",
                "find/tail {abc} {}",
                "find/case {abc} {}",
                "find/skip {abc} {} 1",
                "find/part {abc} {} 2",
                "select {abc} {}",
                "find %a/b {}",
                "find <tag> {}",
                "find {abc} #{}",
                "find #{0102} {}",
                "find #{0102} #{}",
                "find [a []] []",
                "find [a b] []",
                "select [a b] []",
                "find/last [a b] []",
                "find/tail [a b] []",
                "find quote (a b) []",
                "find {} {}",
                "find [] []",
        })
        void answersNone(String expression) {
            assertThat(answerTo(expression)).isEqualTo("_");
        }

        @Test
        @DisplayName("but /ONLY looks for the empty block as one value, and finds it where it is")
        void onlyFindsTheEmptyBlock() {
            assertThat(answerTo("find/only [a []] []")).isEqualTo("[[]]");
        }

        @Test
        @DisplayName("and /ONLY finds no empty block where there is none")
        void onlyFindsNoneWhereThereIsNone() {
            assertThat(answerTo("find/only [a b] []")).isEqualTo("_");
        }

        @Test
        @DisplayName("/ONLY on a string still finds no empty string")
        void onlyOnAString() {
            assertThat(answerTo("find/only {abc} {}")).isEqualTo("_");
        }

        @Test
        @DisplayName("an empty paren in a block is a run unless /ONLY makes it a value")
        void anEmptyParen() {
            assertThat(answerTo("reduce [find [a ()] quote () find/only [a ()] quote ()]"))
                    .isEqualTo("[_ [()]]");
        }
    }

    @Nested
    @DisplayName("in a block, any kind of block is a run of values unless /ONLY")
    class ABlockLikeRun {

        @Test
        @DisplayName("a paren is matched as its values")
        void aParenIsARun() {
            assertThat(answerTo("find [a b c] quote (b c)")).isEqualTo("[b c]");
        }

        @Test
        @DisplayName("so a paren held as a value is not found by its values")
        void aHeldParenIsNotFound() {
            assertThat(answerTo("find [a (b c)] quote (b c)")).isEqualTo("_");
        }

        @Test
        @DisplayName("but /ONLY finds the paren held as a value")
        void onlyFindsTheHeldParen() {
            assertThat(answerTo("find/only [a (b c)] quote (b c)")).isEqualTo("[(b c)]");
        }

        @Test
        @DisplayName("a path is matched as its values")
        void aPathIsARun() {
            assertThat(answerTo("find [a b c] first [b/c]")).isEqualTo("[b c]");
        }

        @Test
        @DisplayName("/TAIL answers after the whole run")
        void tailAfterTheRun() {
            assertThat(answerTo("find/tail [a b c] quote (a b)")).isEqualTo("[c]");
        }

        @Test
        @DisplayName("/LAST finds the last run")
        void lastRun() {
            assertThat(answerTo("index? find/last [a b a b] quote (a b)")).isEqualTo("3");
        }

        @Test
        @DisplayName("and SELECT answers the value after the run")
        void selectAfterTheRun() {
            assertThat(answerTo("select [a b c] quote (a b)")).isEqualTo("c");
        }
    }

    @Nested
    @DisplayName("an empty value that is not a run is found where it is")
    class AnEmptyValue {

        @Test
        @DisplayName("an empty string inside a block")
        void anEmptyStringInABlock() {
            assertThat(answerTo("find [a {}] {}")).isEqualTo("""
                    [""]""");
        }

        @Test
        @DisplayName("an empty binary inside a block")
        void anEmptyBinaryInABlock() {
            assertThat(answerTo("find [a #{}] #{}")).isEqualTo("[#{}]");
        }

        @Test
        @DisplayName("and SELECT answers what follows it")
        void selectAnswersWhatFollows() {
            assertThat(answerTo("select [a {} b] {}")).isEqualTo("b");
        }
    }

    @Nested
    @DisplayName("one item is the smallest pattern that is found")
    class OneItem {

        @Test
        @DisplayName("one character")
        void oneCharacter() {
            assertThat(answerTo("reduce [find {abc} {a}]")).isEqualTo("""
                    ["abc"]""");
        }

        @Test
        @DisplayName("one value in a block")
        void oneValue() {
            assertThat(answerTo("find [a b] [a]")).isEqualTo("[a b]");
        }

        @Test
        @DisplayName("one wildcard character")
        void oneWildcard() {
            assertThat(answerTo("index? find/match/any {Keyboard} {?}")).isEqualTo("1");
        }

        @Test
        @DisplayName("and one character in an empty string is not found")
        void oneCharacterInNothing() {
            assertThat(answerTo("find {} {a}")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("each label in a block query starts its own line")
    class OneFieldToALine {

        @Test
        @DisplayName("a date's parts")
        void aDatesParts() {
            assertThat(answerTo("""
                    q: query 1-Jan-2020 [year month]
                    reduce [new-line? q new-line? next q new-line? skip q 2 new-line? skip q 3]"""))
                    .isEqualTo("[#(true) #(false) #(true) #(false)]");
        }

        @Test
        @DisplayName("a get-word answers its value with no label and no line of its own")
        void aGetWordHasNoLine() {
            assertThat(answerTo("""
                    q: query 1-Jan-2020 [year :month]
                    reduce [new-line? q new-line? next q new-line? skip q 2]"""))
                    .isEqualTo("[#(true) #(false) #(false)]");
        }
    }
}
