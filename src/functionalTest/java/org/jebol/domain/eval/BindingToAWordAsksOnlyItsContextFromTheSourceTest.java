package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BindingToAWordAsksOnlyItsContextFromTheSourceTest {

    private static final String NESTED_OBJECTS = """
            ctx: make object! [outer: 1 inner: make object! [local: 2]]
            target: in ctx/inner 'local
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        String whole = NESTED_OBJECTS + source;
        interpreter.defineNewWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Nested
    @DisplayName("a single word bound to a word target")
    class TheWordForm {

        @Test
        @DisplayName("takes a word the target's own context holds")
        void takesAWordTheTargetHolds() {
            assertThat(answerTo("get bind 'local target")).isEqualTo("2");
        }

        @Test
        @DisplayName("and moves it into that context, not merely one with the same value")
        void movesTheWordIntoTheTargetsContext() {
            assertThat(answerTo("same? ctx/inner context? bind 'local target")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("refuses a word only the enclosing object holds, as R3 does")
        void refusesAWordOnlyTheParentHolds() {
            assertThat(answerTo("""
                    e: try [bind 'outer target] reduce [e/id e/arg1]"""))
                    .isEqualTo("[not-in-context outer]");
        }

        @Test
        @DisplayName("refuses a word no context holds")
        void refusesAWordNoContextHolds() {
            assertThat(answerTo("""
                    e: try [bind 'nowhere-at-all target] reduce [e/id e/arg1]"""))
                    .isEqualTo("[not-in-context nowhere-at-all]");
        }

        @Test
        @DisplayName("refuses a global word from inside a function's frame")
        void refusesAGlobalWordFromAFunctionsFrame() {
            assertThat(answerTo("""
                    outside: 10
                    f: func [a] [e: try [bind 'outside 'a] e/id]
                    f 5""")).isEqualTo("not-in-context");
        }

        @Test
        @DisplayName("takes the function's own argument through its frame")
        void takesTheFunctionsOwnArgument() {
            assertThat(answerTo("""
                    f: func [a] [get bind 'a 'a]
                    f 5""")).isEqualTo("5");
        }

        @Test
        @DisplayName("finds the frame of the call that is running when the function recurses")
        void findsTheRunningFrameWhenTheFunctionRecurses() {
            assertThat(answerTo("""
                    g: func [n /local r] [r: get bind 'n 'r either n > 0 [reduce [r g n - 1]] [r]]
                    g 2""")).isEqualTo("[2 [1 0]]");
        }

        @Test
        @DisplayName("refuses SELF, which an object answers to but does not hold as a word")
        void refusesSelf() {
            assertThat(answerTo("""
                    o: make object! [x: 1]
                    e: try [bind 'self in o 'x] e/id""")).isEqualTo("not-in-context");
        }

        @Test
        @DisplayName("refuses a hidden word rather than failing inside the interpreter")
        void refusesAHiddenWord() {
            assertThat(answerTo("""
                    c: make object! [k: 1 h: 2]
                    protect/hide in c 'h
                    e: try [bind 'h in c 'k] e/id""")).isEqualTo("not-in-context");
        }

        @Test
        @DisplayName("takes a word a derived object copied from its prototype")
        void takesAWordADerivedObjectCopied() {
            assertThat(answerTo("""
                    proto: make object! [x: 1]
                    derived: make proto [y: 2]
                    get bind 'x in derived 'y""")).isEqualTo("1");
        }

        @Test
        @DisplayName("/new adds the missing word to the target's own context, as base-defs relies on")
        void newAddsTheWordToTheTargetsContext() {
            assertThat(answerTo("""
                    bind/new 'fresh target
                    reduce [words-of ctx/inner  words-of ctx]"""))
                    .isEqualTo("[[local fresh] [outer inner]]");
        }
    }

    @Nested
    @DisplayName("a block bound to a word target")
    class TheBlockForm {

        @Test
        @DisplayName("moves a word the target's own context holds")
        void movesAWordTheTargetHolds() {
            assertThat(answerTo("""
                    b: [local] bind b target get first b""")).isEqualTo("2");
        }

        @Test
        @DisplayName("leaves a word only the enclosing object holds where it was")
        void leavesAWordOnlyTheParentHolds() {
            assertThat(answerTo("""
                    b: [outer] bind b target
                    e: try [get first b] e/id""")).isEqualTo("no-value");
        }

        @Test
        @DisplayName("leaves a word no context holds where it was")
        void leavesAWordNoContextHolds() {
            assertThat(answerTo("""
                    b: [nowhere-at-all] bind b target
                    e: try [get first b] e/id""")).isEqualTo("no-value");
        }
    }
}
