package org.jebol.domain.parse;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionQuestionShowsWhereTheWalkStandsFromTheSourceTest {

    private final StringBuilder printed = new StringBuilder();

    private final Interpreter interpreter = AnInterpreter.createWithOutput((OutputPort) printed::append);

    private String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private List<String> linesPrinted() {
        return printed.toString().lines().toList();
    }

    @Nested
    @DisplayName("the line it prints")
    class TheLine {

        @Test
        @DisplayName("is the next rule, a colon, and the input from where the walk stands")
        void namesTheNextRuleAndTheInput() {
            assertThat(answerTo("parse [a b] [?? skip skip]")).isEqualTo("#(true)");
            assertThat(linesPrinted()).containsExactly("skip: [a b]");
        }

        @Test
        @DisplayName("part way through, shows only the input still to come")
        void showsTheInputStillToCome() {
            answerTo("parse [a b] [skip ?? skip]");
            assertThat(linesPrinted()).containsExactly("skip: [b]");
        }

        @Test
        @DisplayName("at the end of the rules, names the end marker")
        void namesTheEndMarker() {
            assertThat(answerTo("parse [a b] [skip skip ??]")).isEqualTo("#(true)");
            assertThat(linesPrinted()).containsExactly("#(end): []");
        }

        @Test
        @DisplayName("molds a string rule and string input as source, each time it is met")
        void moldsStrings() {
            answerTo("parse {abc} [?? {a} ?? to end]");
            assertThat(printed.toString()).isEqualTo("""
                    "a": "abc"
                    to: "bc"
                    """);
        }

        @Test
        @DisplayName("shows binary input as binary")
        void showsBinary() {
            answerTo("parse #{0102} [?? skip skip]");
            assertThat(linesPrinted()).containsExactly("skip: #{0102}");
        }

        @Test
        @DisplayName("inside INTO, shows the nested block it is walking")
        void showsTheNestedBlock() {
            answerTo("parse [a [b c]] [word! into [?? word! word!]]");
            assertThat(linesPrinted()).containsExactly("word!: [b c]");
        }

        @Test
        @DisplayName("after a set-word, names the rule after the ?? rather than the set-word")
        void afterASetWord() {
            answerTo("parse [a b] [x: ?? skip skip]");
            assertThat(linesPrinted()).containsExactly("skip: [a b]");
        }
    }

    @Nested
    @DisplayName("how long the line may be")
    class TheLength {

        @Test
        @DisplayName("long input is cut to 72 characters after the colon")
        void cutsLongInput() {
            answerTo("""
                    text: copy {} loop 100 [append text {x}]
                    parse text [?? to end]""");
            assertThat(linesPrinted()).containsExactly("to: {" + "x".repeat(71));
        }

        @Test
        @DisplayName("a long block of input is cut the same way")
        void cutsALongBlock() {
            answerTo("""
                    words: copy [] loop 40 [append words 'word]
                    parse words [?? to end]""");
            assertThat(linesPrinted()).containsExactly("to: [" + "word ".repeat(14) + "w");
        }

        @Test
        @DisplayName("a long rule is cut to 74 characters, so the line can run past 76")
        void cutsALongRule() {
            answerTo("""
                    long: copy [] loop 20 [append long [opt skip]]
                    parse [a b] reduce ['?? long]""");
            String rule = ("[" + "opt skip ".repeat(20)).substring(0, 74);
            assertThat(linesPrinted()).containsExactly(rule + ": [a b]");
        }

        @Test
        @DisplayName("a newline inside a long string's braces is written as a space, so the line stays one line")
        void keepsTheLineOneLine() {
            answerTo("""
                    text: {first line of some text that is long enough^/second line}
                    parse text [?? to end]""");
            assertThat(linesPrinted())
                    .containsExactly("to: {first line of some text that is long enough second line}");
        }
    }

    @Nested
    @DisplayName("what it changes")
    class WhatItChanges {

        @Test
        @DisplayName("nothing about the answer: a parse that would fail still fails, after printing")
        void neverDecidesTheAnswer() {
            assertThat(answerTo("parse [a] [??]")).isEqualTo("#(false)");
            assertThat(linesPrinted()).containsExactly("#(end): [a]");
        }

        @Test
        @DisplayName("nothing about which alternative wins")
        void leavesAlternativesAlone() {
            assertThat(answerTo("parse [a] [?? {x} | to end]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nothing about the input, which a rule after it still matches from the same place")
        void consumesNothing() {
            assertThat(answerTo("parse [1 2 3] [?? some integer!]")).isEqualTo("#(true)");
            assertThat(linesPrinted()).containsExactly("some: [1 2 3]");
        }
    }
}
