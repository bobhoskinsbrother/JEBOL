package org.jebol.domain.eval;

import org.jebol.adapter.host.ThreadTasks;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
class AValueCrossingToATaskIsACopyTest {

    private static final String DONE = "the-body-is-done";

    private static final Duration A_WHILE = Duration.ofSeconds(20);

    private record Ran(Interpreter parent, String childPrinted) {
    }

    private static Interpreter makeParentWritingTo(StringBuffer written) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withTasks(new ThreadTasks(Bounds.standard().withoutTimeLimit())),
                written::append,
                Bounds.standard());
    }

    private static String waitForChild(StringBuffer written) {
        long givenUpAt = System.nanoTime() + A_WHILE.toNanos();
        while (!written.toString().contains(DONE)) {
            if (System.nanoTime() > givenUpAt) {
                throw new AssertionError("the body never finished: " + written);
            }
            Thread.onSpinWait();
        }
        String all = written.toString();
        return all.substring(0, all.indexOf(DONE)).strip();
    }

    private static Ran runTask(String setup, String body, String afterward) {
        StringBuffer written = new StringBuffer();
        Interpreter parent = makeParentWritingTo(written);
        String source = setup + "\ndo make task! compose/deep/only [" + body + "\nprint {" + DONE + "}]\n"
                + afterward;
        parent.defineNewWordsIn(source);
        ScriptOutcome outcome = parent.run(source);
        assertThat(outcome.succeeded()).as(outcome.display()).isTrue();
        return new Ran(parent, waitForChild(written));
    }

    private static String childPrints(String setup, String body) {
        return runTask(setup, body, "").childPrinted();
    }

    private static String parentAnswer(Interpreter parent, String source) {
        parent.defineNewWordsIn(source);
        return parent.run(source).display();
    }

    @Nested
    @DisplayName("data copies, and the child reads what the parent held")
    class DataCopies {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "42", "1.5", "5%", "$1.50", "to char! 97", "1x2", "1.2.3", "1:02:03", "14-Nov-2026/10:00",
                "{abc}", "%a/b.txt", "http://example.com/x", "a@b.c", "<tag a=1>", "@me", "#{DEADBEEF}",
                "'w", "quote w:", "quote :w", "quote 'w", "/r", "#iss",
                "[1 [2 {three}] #[k: [4]]]", "quote (1 + 2)", "quote a/b", "quote a/b:", "quote :a/b",
                "quote 'a/b", "make hash! [a 1 b 2]", "#[a: 1 b: [2]]", "make object! [a: 1 b: [2]]",
                "charset {ab}", "make image! [2x2 255.0.0]", "make vector! [integer! 32 [1 2 3]]",
                "any-string!", "integer!", "none", "true", "make error! {made here}",
                "make task! [[title: {t}] [print 1]]", "make module! [[name: crossing] [a: 1]]",
                "make struct! [a [int32!] b [uint8!]]"})
        @DisplayName("a value molds in the child as it molds in the parent")
        void moldsAlike(String made) {
            Ran ran = runTask("v: " + made, "print mold quote (v)", "");

            assertThat(ran.childPrinted()).isEqualTo(ran.parent().run("mold v").asHostValue());
        }

        @Test
        @DisplayName("a block's line breaks mold in the child as the parent wrote them, through a copy made there too")
        void keepsLineBreaks() {
            Ran ran = runTask("""
                    b: [
                        1
                        2 3
                        [4
                            5]
                    ]""", """
                    print mold quote (b)
                    print mold copy/deep quote (b)""", "");

            assertThat(ran.childPrinted()).isEqualTo(
                    ran.parent().run("rejoin [mold b newline mold copy/deep b]").asHostValue());
        }

        @Test
        @DisplayName("a nested series keeps its own position")
        void keepsPositions() {
            assertThat(childPrints("""
                    b: next [1 2 3]
                    s: skip {abcd} 2
                    holder: reduce [b s]""", """
                    print mold reduce [index? first (holder)  index? second (holder)  (holder)]""")).isEqualTo("""
                    [2 3 [[2 3] "cd"]]""");
        }
    }

    @Nested
    @DisplayName("the copy is the child's own")
    class TheCopyIsTheChildsOwn {

        @Test
        @DisplayName("a block the parent changes after DO is the block as it was when DO was called")
        void copiesWhenDoIsCalled() {
            assertThat(runTask("""
                    b: [1]""", """
                    wait 0.3
                    print mold (b)""", """
                    append b 2""").childPrinted()).isEqualTo("[1]");
        }

        @Test
        @DisplayName("a string the child changes is unchanged when the parent reads it again")
        void keepsParentsStringFromChild() {
            Ran ran = runTask("""
                    s: {abc}""", """
                    append (s) {d}
                    print (s)""", "");

            assertThat(ran.childPrinted()).isEqualTo("abcd");
            assertThat(ran.parent().run("s").asHostValue()).isEqualTo("abc");
        }

        @Test
        @DisplayName("an object the child changes is unchanged in the parent")
        void keepsParentsObjectFromChild() {
            Ran ran = runTask("""
                    o: make object! [n: 1]""", """
                    x: (o)
                    x/n: 2
                    print x/n""", "");

            assertThat(ran.childPrinted()).isEqualTo("2");
            assertThat(parentAnswer(ran.parent(), "o/n")).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("sharing and cycles are kept")
    class SharingIsKept {

        @Test
        @DisplayName("a series held twice, at two positions, is one series held twice in the copy")
        void keepsSeriesHeldTwice() {
            assertThat(childPrints("""
                    b: [1 2]""", """
                    x: (b)
                    y: (next b)
                    append x 3
                    print mold reduce [same? x head y  y]""")).isEqualTo("[#(true) [2 3]]");
        }

        @Test
        @DisplayName("a string held by two blocks is one string in the copy")
        void keepsStringHeldByTwoBlocks() {
            assertThat(childPrints("""
                    s: {abc}
                    pair: reduce [reduce [s] reduce [s]]""", """
                    x: (pair)
                    append first first x {d}
                    print first second x""")).isEqualTo("abcd");
        }

        @Test
        @DisplayName("a block that holds itself copies as a block that holds itself")
        void keepsCycle() {
            assertThat(childPrints("""
                    b: [1]
                    append/only b b""", """
                    x: (b)
                    print mold reduce [same? x second x  first x]""")).isEqualTo("[#(true) 1]");
        }

        @Test
        @DisplayName("an object that holds itself copies as an object that holds itself")
        void keepsObjectCycle() {
            assertThat(childPrints("""
                    o: make object! [me: none n: 1]
                    o/me: o""", """
                    x: (o)
                    x/n: 2
                    print x/me/n""")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("functions copy, and natives are the child's own")
    class FunctionsAndNatives {

        @Test
        @DisplayName("a function runs in the child")
        void runsFunction() {
            assertThat(childPrints("""
                    double: func [x] [x * 2]""", """
                    print (:double) 21""")).isEqualTo("42");
        }

        @Test
        @DisplayName("a function with a refinement and a local runs in the child")
        void runsFunctionWithRefinementAndLocal() {
            assertThat(childPrints("""
                    scale: func [x /by n /local result] [result: x * either by [n] [2] result]""", """
                    f: quote (:scale)
                    print [f 5  f/by 5 3]""")).isEqualTo("10 15");
        }

        @Test
        @DisplayName("a closure runs in the child, and what it makes keeps its own argument")
        void runsClosure() {
            assertThat(childPrints("""
                    remember: closure [x] [func [] [x]]""", """
                    f: quote (:remember)
                    one: f 1
                    two: f 2
                    print [one two]""")).isEqualTo("1 2");
        }

        @Test
        @DisplayName("an object's function finds the object's own word in the copy, and the parent's is untouched")
        void bindsObjectsFunctionToCopy() {
            Ran ran = runTask("""
                    counter: make object! [n: 1 bump: does [n: n + 1]]""", """
                    x: (counter)
                    x/bump
                    x/bump
                    print x/n""", "");

            assertThat(ran.childPrinted()).isEqualTo("3");
            assertThat(parentAnswer(ran.parent(), "counter/n")).isEqualTo("1");
        }

        @Test
        @DisplayName("a native and an action cross as the child's own of the same name")
        void givesChildItsOwnNatives() {
            assertThat(childPrints("""
                    a: :add
                    p: :print
                    plus: :+""", """
                    print reduce [same? quote (:a) :add  same? quote (:p) :print  same? quote (:plus) :+  (:a) 1 2]"""))
                    .isEqualTo("true true true 3");
        }
    }

    @Nested
    @DisplayName("words are bound in the child as LOAD would bind them")
    class WordsAreBoundAsLoadWould {

        @Test
        @DisplayName("a body that worked through MOLD and LOAD answers the same")
        void answersAsMoldAndLoadDid() {
            assertThat(childPrints("", """
                    x: 6
                    print x * 7""")).isEqualTo("42");
        }

        @Test
        @DisplayName("a word of the parent's user context has no value in the child")
        void losesParentsUserContext() {
            assertThat(childPrints("""
                    inherited: {from the parent}""", """
                    print value? 'inherited""")).isEqualTo("false");
        }

        @Test
        @DisplayName("a word bound to an object that did not cross is bound as LOAD would bind it")
        void rebindsWordOfObjectLeftBehind() {
            assertThat(childPrints("""
                    o: make object! [left-behind: 5]
                    w: in o 'left-behind""", """
                    print value? quote (w)""")).isEqualTo("false");
        }

        @Test
        @DisplayName("words in a data block are bound in the child, holding what lib holds")
        void bindsDataWordsInChild() {
            assertThat(childPrints("""
                    b: [print]""", """
                    print same? get first (b) :print""")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("what stands outside the interpreter is refused in the parent")
    class WhatStandsOutsideIsRefused {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "make event! [type: 'key]|event!",
                "make gob! []|gob!",
                "open checksum://sha1|port!",
                "system/codecs/text/entry|handle!",
                "get in import 'easing 'linear|command!"})
        @DisplayName("the parent catches invalid-type naming the datatype, and the child never starts")
        void refusesInParent(String madeAndNamed) {
            String made = madeAndNamed.substring(0, madeAndNamed.indexOf('|'));
            String named = madeAndNamed.substring(madeAndNamed.indexOf('|') + 1);
            StringBuffer written = new StringBuffer();
            Interpreter parent = makeParentWritingTo(written);

            String answer = parentAnswer(parent, """
                    v: %s
                    e: try [do make task! compose/deep/only [print {started} x: [nested [(:v)]]]]
                    reduce [e/id e/arg1]""".formatted(made));

            assertThat(answer).isEqualTo("[invalid-type #(" + named + ")]");
            assertThat(written.toString()).doesNotContain("started");
        }

        @Test
        @DisplayName("a refused value inside an object is found and refused too")
        void refusesInsideObject() {
            StringBuffer written = new StringBuffer();
            Interpreter parent = makeParentWritingTo(written);

            assertThat(parentAnswer(parent, """
                    o: make object! [p: open checksum://sha1]
                    e: try [do make task! compose/only [print {started} x: (o)]]
                    e/id""")).isEqualTo("invalid-type");
        }
    }
}
