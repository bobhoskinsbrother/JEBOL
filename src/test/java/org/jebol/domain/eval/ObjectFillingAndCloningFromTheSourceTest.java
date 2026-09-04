package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filling one object from another is {@code Copy_Deep_Values(obj, 1, tail,
 * TS_CLONE)} followed by {@code Rebind_Block}: the target shares no series with
 * the source, and only words bound to the source frame are rehomed. Rebol's own
 * tests pin the shapes -- issue-1874 for the cloned series, issue-2045 for a
 * function and a block that came from outside, issue-2049 and issue-2050 for what
 * is rehomed and what is not, and issue-2118 for CONSTRUCT's floor.
 */
class ObjectFillingAndCloningFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("SET from one object to another copies by name")
    class FillingFromAnObject {

        @Test
        @DisplayName("only the target's own fields are written, and the extras are ignored")
        void onlyTheTargetsFieldsAreWritten() {
            assertThat(answerTo("""
                    target: object [a: 3 b: 4] source: object [z: 0 a: 6 b: 7 c: 9]
                    set target source
                    (mold/flat target) = {make object! [a: 6 b: 7]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a source field holding nothing leaves the target's own value standing")
        void anUnsetSourceFieldIsPassedOver() {
            assertThat(answerTo("""
                    target: object [a: 1 b: 2] source: object [a: 10 b: 20]
                    set/any 'source/b ()
                    set target source
                    all [target/a = 10 target/b = 2]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/ANY threads the absence through into the target")
        void anyWritesTheAbsence() {
            assertThat(answerTo("""
                    target: object [a: 1 b: 2] source: object [a: 10 b: 20]
                    set/any 'source/b ()
                    set/any target source
                    all [target/a = 10 unset? get/any in target 'b]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/SOME leaves a field the source holds none for alone")
        void someLeavesANoneAlone() {
            assertThat(answerTo("""
                    target: object [a: 3 b: 4] source: object [z: 0 a: none b: 7]
                    set/some target source
                    (mold/flat target) = {make object! [a: 3 b: 7]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/ONLY writes the object itself into every field instead")
        void onlyWritesTheWholeObject() {
            assertThat(answerTo("""
                    target: object [a: 3 b: 4] source: object [z: 0]
                    set/only target source
                    all [target/a = source target/b = source]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the call answers the source it was given")
        void theCallAnswersTheSource() {
            assertThat(answerTo("""
                    target: object [a: 1] source: object [a: 10]
                    (set target source) = source""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("making an object from a prototype clones what it takes")
    class CloningAPrototype {

        @Test
        @DisplayName("a series field is a duplicate, not the prototype's own")
        void aSeriesFieldIsDuplicated() {
            assertThat(answerTo("""
                    prototype: make object! [b: []]
                    copy-of-it: make prototype []
                    not same? prototype/b copy-of-it/b""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("merging an empty object clones it just the same")
        void mergingAnEmptyObjectStillClones() {
            assertThat(answerTo("""
                    prototype: make object! [b: []]
                    merged: make prototype make object! []
                    not same? prototype/b merged/b""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the duplicate holds what the original held")
        void theDuplicateHoldsTheSameItems() {
            assertThat(answerTo("""
                    prototype: make object! [b: [7 8]]
                    copy-of-it: make prototype []
                    copy-of-it/b = [7 8]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a method written in the object reads the copy's own field")
        void aMethodIsRehomed() {
            assertThat(answerTo("""
                    original: make object! [n: 'o f: func [] [n]]
                    copy-of-it: make original [n: 'p]
                    all [original/f = 'o copy-of-it/f = 'p]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a closure method is rehomed the same way")
        void aClosureMethodIsRehomedToo() {
            assertThat(answerTo("""
                    original: make object! [n: 'o f: closure [] [n]]
                    copy-of-it: make original [n: 'p]
                    all [original/f = 'o copy-of-it/f = 'p]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the two objects hold two different functions")
        void theTwoFunctionsAreNotOne() {
            assertThat(answerTo("""
                    original: make object! [n: 'o f: func [] [n]]
                    copy-of-it: make original [n: 'p]
                    not same? (get in original 'f) (get in copy-of-it 'f)"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a value that came from outside keeps the binding its author gave it")
    class WhatIsLeftAsWritten {

        @Test
        @DisplayName("a function written outside still reads the outer word")
        void aBorrowedFunctionKeepsItsBinding() {
            assertThat(answerTo("""
                    a: 1 outer: func [] [a]
                    o1: make object! [a: 2 g: :outer]
                    o1/g = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("through a merge as well")
        void aBorrowedFunctionSurvivesAMerge() {
            assertThat(answerTo("""
                    a: 1 outer: func [] [a]
                    o1: make object! [a: 2 g: :outer]
                    o2: make o1 [a: 3 g: :outer]
                    o2/g = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and when the merge does not mention it at all")
        void aBorrowedFunctionSurvivesAMergeThatIgnoresIt() {
            assertThat(answerTo("""
                    a: 1 outer: func [] [a]
                    o1: make object! [a: 2 g: :outer]
                    o3: make o1 [a: 4]
                    o3/g = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block written outside keeps its words bound outside")
        void aBorrowedBlockKeepsItsBinding() {
            assertThat(answerTo("""
                    a: 1 written-outside: [a]
                    o1: make object! [a: 2 c: written-outside]
                    (do o1/c) = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and keeps it through a merge")
        void aBorrowedBlockSurvivesAMerge() {
            assertThat(answerTo("""
                    a: 1 written-outside: [a]
                    o1: make object! [a: 2 c: written-outside]
                    o3: make o1 [a: 4]
                    (do o3/c) = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a function inside a block field is not reached by the rehoming")
        void aFunctionInsideABlockIsNotRehomed() {
            assertThat(answerTo("""
                    original: make object! [n: 'o b: reduce [func [] [n]]]
                    copy-of-it: make original [n: 'p]
                    original/b/1 = 'o""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("CONSTRUCT floors an absence to none")
    class TheConstructFloor {

        @Test
        @DisplayName("a trailing set-word holds none rather than nothing")
        void aTrailingSetWordHoldsNone() {
            assertThat(answerTo("""
                    o: construct [a: b:] all [none? o/a none? o/b]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an absence written after a set-word floors to none as well")
        void anAbsenceFloorsToNone() {
            assertThat(answerTo("""
                    none? get/any in construct head insert tail [a:] () 'a"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where OBJECT of the same block raises instead")
        void objectRefusesTheSameBlock() {
            assertThat(answerTo("""
                    error? try [object [a: b:]]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/ONLY is a rule about words, and leaves the field holding nothing")
        void onlyLeavesTheFieldUndefined() {
            assertThat(answerTo("""
                    unset? get/any in construct/only head insert tail [a:] () 'a"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a trailing set-word under /ONLY holds nothing too")
        void onlyLeavesATrailingSetWordUndefined() {
            assertThat(answerTo("""
                    unset? get/any in construct/only [a: b:] 'a""")).isEqualTo("#(true)");
        }
    }
}
