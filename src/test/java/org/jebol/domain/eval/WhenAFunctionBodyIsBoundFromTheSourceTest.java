package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a function's body acquires its bindings, which is when the function is
 * made rather than when it is called.
 *
 * <p>R3 binds the body block in place: each word the spec declares is given a
 * binding that names the function, and a call lends the function its frame. So
 * the same bound word reads a different value each time round, and the
 * innermost call's value inside a recursion.
 *
 * <p>Binding by name at each call instead is the plausible implementation, and
 * it reads the same for every body nobody shares and nobody edits -- which is
 * nearly all of them. It differs on exactly two cases, and Rebol's own
 * func-test asserts both.
 *
 * <p>Every figure here was read off {@code ./r3-head} 3.22.5.
 */
class WhenAFunctionBodyIsBoundFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] "
                + "either error? e [mold e/id] ['no-error]");
    }

    @Nested
    @DisplayName("the ordinary case, which neither implementation gets wrong")
    class TheOrdinaryCase {

        @Test
        @DisplayName("a body's words read the call's own arguments")
        void abodyReadsItsArguments() {
            assertThat(answerTo("f: func [value] [value + value] f 3"))
                    .isEqualTo("6");
        }

        @Test
        @DisplayName("and a recursion reads the innermost call's")
        void arecursionReadsTheInnermost() {
            assertThat(answerTo("""
                    countdown: func [n] [either n <= 0 [{done}] [countdown n - 1]]
                    countdown 5""")).isEqualTo("\"done\"");
            assertThat(answerTo("""
                    total: func [n] [either n <= 0 [0] [n + total n - 1]]
                    total 4""")).isEqualTo("10");
        }

        @Test
        @DisplayName("a local keeps its own value per call")
        void alocalIsPerCall() {
            assertThat(answerTo("""
                    f: func [n /local held] [held: n * 2 either n <= 1 [held] [f n - 1]]
                    f 3""")).isEqualTo("2");
        }

        @Test
        @DisplayName("and a word the spec does not declare keeps the binding it had")
        void anundeclaredWordKeepsItsBinding() {
            assertThat(answerTo("""
                    outer: 7
                    f: func [a] [a + outer]
                    f 1""")).isEqualTo("8");
        }

        @Test
        @DisplayName("a nested block inside the body is bound too, and so is a map")
        void anestedBlockAndMapAreBoundToo() {
            assertThat(answerTo("f: func [a] [either true [a * 2] [0]] f 4"))
                    .isEqualTo("8");
            assertThat(answerTo("""
                    #[num: 5] = apply func [val] [compose/deep #[num: (val)]] [5]"""))
                    .as("a map literal in a body reaches the argument through COMPOSE")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a word PARSE writes into is the call's own")
        void aparseTargetIsTheCallsOwn() {
            assertThat(answerTo("""
                    f: func [s /local part] [parse s [copy part to end] part]
                    f {abc}""")).isEqualTo("\"abc\"");
        }
    }

    /**
     * Rebol's own func-test, "function rebinding (closure compatibility)", for
     * issue 2048. A word put into the body after the function was made was
     * never bound, so it reads the global one -- which is the whole of what
     * makes a body adjustable from outside.
     */
    @Nested
    @DisplayName("a word put into the body afterwards, which was never bound")
    class AWordPutInAfterwards {

        @Test
        @DisplayName("reads the global of that name, not the argument")
        void itreadsTheGlobal() {
            assertThat(answerTo("""
                    f: make function! reduce [[value] f-body: [value + value]]
                    reduce [f 1 f 2 f 3]""")).isEqualTo("[2 4 6]");

            assertThat(answerTo("""
                    f: make function! reduce [[value] f-body: [value + value]]
                    value: 1
                    change f-body 'value
                    reduce [f 1 f 2 f 3]"""))
                    .as("the inserted value is the global 1; the other is the argument")
                    .isEqualTo("[2 3 4]");
        }

        @Test
        @DisplayName("and a closure made the same way behaves the same")
        void aclosureBehavesTheSame() {
            assertThat(answerTo("""
                    f: make closure! reduce [[value] f-body: [value + value]]
                    reduce [f 1 f 2 f 3]""")).isEqualTo("[2 4 6]");

            assertThat(answerTo("""
                    f: make closure! reduce [[value] f-body: [value + value]]
                    value: 1
                    change f-body 'value
                    reduce [f 1 f 2 f 3]""")).isEqualTo("[2 3 4]");
        }
    }

    /**
     * Rebol's own func-test, issue 2025 and issue 2044. Making the second
     * function binds the same block again, so the words belong to it, and a
     * word bound to a function that is not running names no slot.
     */
    @Nested
    @DisplayName("a body shared between two functions, which the second one takes")
    class ABodySharedByTwo {

        @Test
        @DisplayName("the first function stops working once the second is made")
        void thefirstStopsWorking() {
            assertThat(answerTo("""
                    body: [x + y]
                    f: make function! reduce [[x] body]
                    g: make function! reduce [[y] body]
                    error? try [f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and it is not-defined, a word bound to a function nobody is running")
        void itisNotDefined() {
            assertThat(errorIdFrom("""
                    body2: [x-v: 1]
                    f: make function! reduce [[x /local x-v] body2]
                    g: make function! reduce [[y /local x-v] body2]
                    f 1""")).isEqualTo("\"not-defined\"");
        }

        /**
         * Both of them, which is the part that reads as a surprise. Making the
         * second function binds only the words its own spec declares, so the
         * body is left half belonging to one function and half to the other
         * and neither can run it. Assigning the missing word at the top level
         * does not help: the word is bound, just not to anything running.
         */
        @Test
        @DisplayName("and so does the second, each holding half the body's words")
        void thesecondStopsTooAndTheGlobalDoesNotHelp() {
            assertThat(errorIdFrom("""
                    body: [x + y]
                    f: make function! reduce [[x] body]
                    g: make function! reduce [[y] body]
                    x: 10
                    g 5""")).isEqualTo("\"not-defined\"");
        }

        /**
         * The whole of Rebol's own issue-2025 test, which asks only that
         * calling the first function afterwards fails at all.
         */
        @Test
        @DisplayName("which is what Rebol's own issue-2025 asks")
        void whichIsWhatIssue2025Asks() {
            assertThat(answerTo("""
                    f: make function! reduce [[x /local x-v y-v] body: [
                        x-v: either error? try [get/any 'x] [{no x}] [
                            rejoin [{x: } mold/all :x]]
                        y-v: either error? try [get/any 'y] [{no y}] [
                            rejoin [{y: } mold/all :y]]
                    ]]
                    g: make function! reduce [[y /local x-v y-v] body]
                    error? try [f 1]""")).isEqualTo("#(true)");
        }

        /**
         * And BIND still reaches such a word. Binding into a function's own
         * words is binding relatively -- the answer reads whichever call is
         * running when it is evaluated -- so the target has the name because
         * the spec declares it, not because a call is lending a frame.
         */
        @Test
        @DisplayName("but BIND still binds into a function nobody is running")
        void bindStillReachesIt() {
            assertThat(answerTo("""
                    word: do func [x] ['x] 1
                    same? word try [bind 'x word]""")).isEqualTo("#(true)");
        }
    }

    /**
     * A closure's frame outlives the call that made it, so it cannot be lent
     * a context that is handed back. Its body is copied per call and the words
     * that named the function are pointed at that call's frame -- by binding
     * rather than by name, which is what keeps the rules above true of it.
     */
    @Nested
    @DisplayName("a closure, which is copied per call rather than lent a frame")
    class AClosure {

        @Test
        @DisplayName("the names of a call outlive the call, one frame per call")
        void thenamesOutliveTheCall() {
            assertThat(answerTo("""
                    keeper: closure [a] [does [a]]
                    one: keeper 9
                    two: keeper 7
                    reduce [do one do two]""")).isEqualTo("[9 7]");
        }

        @Test
        @DisplayName("CONTEXT? of a closure's argument is an object, not the closure")
        void contextOfAClosuresArgument() {
            assertThat(answerTo("type? do closure [a] [context? 'a] 1"))
                    .isEqualTo("#(object!)");
        }
    }

    @Nested
    @DisplayName("and what CONTEXT? reads, which is the binding this rule makes")
    class WhatContextReads {

        @Test
        @DisplayName("a function's argument answers the function itself")
        void anargumentAnswersTheFunction() {
            assertThat(answerTo("f: func [a] [context? 'a] same? :f f 1"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("f: func [a] [spec-of context? 'a] mold f 1"))
                    .isEqualTo("\"[a]\"");
        }

    }
}
