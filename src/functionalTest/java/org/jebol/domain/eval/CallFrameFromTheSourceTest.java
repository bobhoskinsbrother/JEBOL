package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallFrameFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("a function's words read the call that is running")
    class TheRunningCall {

        @Test
        @DisplayName("a block bound in an outer call and run in an inner one reads the inner call")
        void readsTheInnermostCall() {
            assertThat(answerTo("""
                    n: func [x b] [either x = 0 [do b] [n x - 1 [x]]]
                    n 2 none""")).isEqualTo("0");
        }

        @Test
        @DisplayName("a word kept from a call that has ended is not-defined")
        void aWordFromAnEndedCallIsNotDefined() {
            assertThat(errorIdFrom("""
                    f: func [a] ['a]  get f 1""")).isEqualTo("not-defined");
        }

        @Test
        @DisplayName("a word handed to another function reads the call it came from")
        void aHandedWordReadsItsOwnCall() {
            assertThat(answerTo("""
                    q: func [a] [q2 'a]
                    q2: func [w] [get w]
                    q 7""")).isEqualTo("7");
        }

        @Test
        @DisplayName("and so does a block handed to another function")
        void aHandedBlockReadsItsOwnCall() {
            assertThat(answerTo("""
                    v: func [a] [v2 [a]]
                    v2: func [b] [do b]
                    v 4""")).isEqualTo("4");
        }

        @Test
        @DisplayName("a local written before an inner call keeps its value after it")
        void aLocalSurvivesAnInnerCallOfTheSameFunction() {
            assertThat(answerTo("""
                    ls: func [x /local y] [y: x if x > 0 [ls x - 1] y]
                    ls 3""")).isEqualTo("3");
        }

        @Test
        @DisplayName("an error thrown out of an inner call hands the frame back to the outer one")
        void anErrorOutOfAnInnerCallHandsTheFrameBack() {
            assertThat(answerTo("""
                    eo: func [x] [either x = 0 [1 / 0] [try [eo x - 1] x]]
                    eo 2""")).isEqualTo("2");
        }

        @Test
        @DisplayName("and so does a RETURN out of an inner call")
        void aReturnOutOfAnInnerCallHandsTheFrameBack() {
            assertThat(answerTo("""
                    ro: func [x] [if x > 0 [ro x - 1 return x] 0]
                    ro 3""")).isEqualTo("3");
        }

        @Test
        @DisplayName("five hundred calls deep, each reads its own argument")
        void eachOfFiveHundredCallsReadsItsOwnArgument() {
            assertThat(answerTo("""
                    dp: func [x] [either x = 0 [0] [1 + dp x - 1]]
                    dp 500""")).isEqualTo("500");
        }

        @Test
        @DisplayName("a closure made in a function cannot read the function's word once the call is over")
        void aClosureDoesNotKeepAFunctionsCallAlive() {
            assertThat(errorIdFrom("""
                    mk: func [n] [closure [] [n]]  c3: mk 10  c3""")).isEqualTo("not-defined");
        }
    }

    @Nested
    @DisplayName("the frame's shape is fixed by the spec")
    class TheShape {

        @Test
        @DisplayName("a function with no parameters runs")
        void runsWithNoParameters() {
            assertThat(answerTo("""
                    z: func [] [7]  z""")).isEqualTo("7");
        }

        @Test
        @DisplayName("a function with twelve parameters reads the first and the last")
        void readsTheFirstAndLastOfTwelve() {
            assertThat(answerTo("""
                    mp: func [a b c d e f g h i j k l] [reduce [a l]]
                    mp 1 2 3 4 5 6 7 8 9 10 11 12""")).isEqualTo("[1 12]");
        }

        @Test
        @DisplayName("an unused refinement, its argument and a local all start none")
        void unusedSlotsStartNone() {
            assertThat(answerTo("""
                    g: func [a /r b /local c] [reduce [a r b c]]
                    (mold/flat/all g 1) = {[1 _ _ _]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a used refinement is true and its argument arrives")
        void aUsedRefinementIsTrue() {
            assertThat(answerTo("""
                    g: func [a /r b /local c] [reduce [a r b c]]
                    (mold/flat/all g/r 1 2) = {[1 #(true) 2 _]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("WORDS-OF the frame is the spec's words")
        void wordsOfTheFrameAreTheSpecsWords() {
            assertThat(answerTo("""
                    k: func [a b] [words-of context? 'a]
                    k 1 2""")).isEqualTo("[a b]");
        }

        @Test
        @DisplayName("BIND/NEW into a running call adds nothing")
        void bindNewAddsNothing() {
            assertThat(answerTo("""
                    u: func [a] [bind/new [zz] 'a words-of context? 'a]
                    u 1""")).isEqualTo("[a]");
        }

        @Test
        @DisplayName("IN on the frame raises expect-arg, because it is a function, not an object")
        void inOnTheFrameIsExpectArg() {
            assertThat(errorIdFrom("""
                    m: func [a] [in context? 'a 'a]  m 1""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and so does VALUES-OF")
        void valuesOfTheFrameIsExpectArg() {
            assertThat(errorIdFrom("""
                    s: func [a] [values-of context? 'a]  s 9""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("a closure's call has a frame of its own and a body of its own")
    class AClosuresCall {

        @Test
        @DisplayName("words kept from two calls read their own calls afterwards")
        void keptWordsReadTheirOwnCalls() {
            assertThat(answerTo("""
                    c: closure [a] ['a]
                    w1: c 1  w2: c 2
                    reduce [get w1 get w2]""")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a block bound in an outer call reads that call, not the innermost")
        void aRecursionReadsTheCallThatBoundTheBlock() {
            assertThat(answerTo("""
                    cr: closure [x b] [either x = 0 [do b] [cr x - 1 [x]]]
                    cr 2 none""")).isEqualTo("1");
        }

        @Test
        @DisplayName("the closure's own body is left as it was written")
        void theBodyIsUnchanged() {
            assertThat(answerTo("""
                    cb: closure [a] [a + 1]  cb 5
                    body-of :cb""")).isEqualTo("[a + 1]");
        }

        @Test
        @DisplayName("each call answers a different block")
        void eachCallAnswersADifferentBlock() {
            assertThat(answerTo("""
                    cl: closure [] [[1]]
                    same? cl cl""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("which a function does not")
        void aFunctionAnswersTheSameBlock() {
            assertThat(answerTo("""
                    fl: func [] [[1]]
                    same? fl fl""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block literal starts empty on every call")
        void aBlockLiteralStartsEmptyEachCall() {
            assertThat(answerTo("""
                    cm: closure [] [append [] 1]
                    cm cm""")).isEqualTo("[1]");
        }

        @Test
        @DisplayName("and so does a string literal")
        void aStringLiteralStartsEmptyEachCall() {
            assertThat(answerTo("""
                    cs: closure [] [append {} {x}]
                    (cs cs) == {x}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a binary literal")
        void aBinaryLiteralStartsEmptyEachCall() {
            assertThat(answerTo("""
                    cb: closure [] [append #{} #{01}]
                    cb cb""")).isEqualTo("#{01}");
        }

        @Test
        @DisplayName("and a file literal")
        void aFileLiteralIsFreshEachCall() {
            assertThat(answerTo("""
                    cf: closure [] [append %a {b}]
                    cf cf""")).isEqualTo("%ab");
        }

        @Test
        @DisplayName("and a tag literal")
        void aTagLiteralIsFreshEachCall() {
            assertThat(answerTo("""
                    ct: closure [] [append <a> {b}]
                    ct ct""")).isEqualTo("<ab>");
        }

        @Test
        @DisplayName("each call answers a different paren")
        void eachCallAnswersADifferentParen() {
            assertThat(answerTo("""
                    cp: closure [] [quote (1)]
                    same? cp cp""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and a different path")
        void eachCallAnswersADifferentPath() {
            assertThat(answerTo("""
                    cpa: closure [] ['a/b]
                    same? cpa cpa""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("but the same map, which is not a series")
        void eachCallAnswersTheSameMap() {
            assertThat(answerTo("""
                    cm: closure [] [#[k: 1]]
                    same? cm cm""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a nested block's words read the call that made it")
        void aNestedBlockReadsItsOwnCall() {
            assertThat(answerTo("""
                    cn: closure [a] [[a]]
                    b1: cn 1  b2: cn 2
                    reduce [do b1 do b2]""")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("USE in a loop gives each pass a word of its own")
        void useGivesEachPassItsOwnWord() {
            assertThat(answerTo("""
                    ws: copy []
                    repeat i 3 [use [u] [u: i append ws 'u]]
                    reduce [get ws/1 get ws/2 get ws/3]""")).isEqualTo("[1 2 3]");
        }

        @Test
        @DisplayName("the object's fields are in spec order when the refinement is unused")
        void fieldsAreInSpecOrderWithoutTheRefinement() {
            assertThat(answerTo("""
                    co: closure [a /r b /local c] [context? 'a]
                    (mold/flat co 1) = {make object! [a: 1 r: _ b: _ local: _ c: _]}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and when it is used")
        void fieldsAreInSpecOrderWithTheRefinement() {
            assertThat(answerTo("""
                    co: closure [a /r b /local c] [context? 'a]
                    (mold/flat co/r 1 2) = {make object! [a: 1 r: #(true) b: 2 local: _ c: _]}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("CONTEXT? of a word kept from a closure's call is its object")
        void contextOfAKeptClosureWordIsItsObject() {
            assertThat(answerTo("""
                    cd: closure [a] ['a]
                    g5: func [x] [context? x]
                    (mold/flat/all g5 cd 9) = {#(object! [a: 9 local: _])}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("CONTEXT? of a function's word answers the call running when it is asked")
    class WhatContextAnswers {

        @Test
        @DisplayName("not an object")
        void notAnObject() {
            assertThat(answerTo("""
                    fo: func [a] [context? 'a]
                    object? fo 1""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("EITHER, asked from inside EITHER in the word's own function")
        void eitherFromInsideEither() {
            assertThat(answerTo("""
                    f2: func [a] [either true [context? 'a] [0]]
                    same? :either f2 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the asking function, for a live word handed to it")
        void theAskingFunctionForAHandedWord() {
            assertThat(answerTo("""
                    lv: func [a] [g6 'a]
                    g6: func [x] [context? x]
                    same? :g6 lv 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the asking function, for a word whose call has ended")
        void theAskingFunctionForAnEndedWord() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    g2: func [x] [context? x]
                    same? :g2 g2 f 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("not a call still gathering its arguments")
        void notACallStillGathering() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    g3: func [x] [mold context? x]
                    (g3 f 1) = {make function! [[x][mold context? x]]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("REDUCE, from inside REDUCE")
        void reduceFromInsideReduce() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :reduce first reduce [context? f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("COMPOSE, from inside COMPOSE")
        void composeFromInsideCompose() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    h: func [x] [compose [(context? x)]]
                    same? :compose first h f 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("LOOP, from inside LOOP")
        void loopFromInsideLoop() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :loop loop 1 [context? f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("ALL, from inside ALL")
        void allFromInsideAll() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :all all [context? f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("TRY, from inside TRY")
        void tryFromInsideTry() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :try try [context? f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("DO, from inside DO")
        void doFromInsideDo() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :do do [context? f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("DO itself, from inside DO/NEXT written as a path")
        void doFromInsideDoNext() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :do do/next [context? f 1] 'rest""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and TRY itself, from inside TRY/ALL")
        void tryFromInsideTryAll() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    same? :try try/all [context? f 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the function itself, when APPLY runs it")
        void theFunctionWhenApplyRunsIt() {
            assertThat(answerTo("""
                    f: func [a] [context? 'a]
                    same? :f apply :f [1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and when DO runs it")
        void theFunctionWhenDoRunsIt() {
            assertThat(answerTo("""
                    f: func [a] [context? 'a]
                    same? :f do :f 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("none, when nothing at all is running, as at a bare console")
        void noneWhenNothingIsRunning() {
            assertThat(answerTo("""
                    f: func [a] ['a]
                    kept: f 1
                    none? context? kept""")).isEqualTo("#(true)");
        }
    }
}
