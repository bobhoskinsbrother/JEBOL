package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfIsProtectedFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("set/any 'e try [" + source + """
                ]
                either all [value? 'e  error? :e] [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("SET and UNSET both refuse the word self")
    void setAndUnsetBothRefuseSelf() {
        assertThat(failureOf("set 'self 5")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("unset 'self")).isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("an object spec that writes self is refused")
    void anObjectSpecThatWritesSelfIsRefused() {
        assertThat(failureOf("make object! [self: 5]"))
                .isEqualTo("[Script self-protected]");
        assertThat(failureOf("context [self: 5]"))
                .isEqualTo("[Script self-protected]");
        assertThat(failureOf("make object! [a: 1 self: 5 b: 2]"))
                .isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("and so is appending one to an object that already exists")
    void appendingOneToAnObjectIsRefused() {
        assertThat(failureOf("""
                holder: make object! [a: 1]
                append holder [self: 5]""")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                holder: make object! [a: 1]
                append holder [b: 2 self: 5]""")).isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("INSERT is the same door as APPEND, and a bare word is the same as a set-word")
    void insertingOneAndAddingTheBareWordAreRefusedToo() {
        assertThat(failureOf("""
                holder: make object! [a: 1]
                insert holder [self: 5]""")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                holder: make object! [a: 1]
                append holder 'self""")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                holder: make object! [a: 1]
                insert holder 'self""")).isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("a context with no self of its own gains an ordinary field of that name")
    void aContextWithNoSelfGainsAnOrdinaryFieldOfThatName() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                object? append holder 'self""")).isEqualTo("#(true)");
        assertThat(failureOf("""
                holder: context? use [x] ['x]
                append holder 'self""")).isEqualTo("[ok]");
        assertThat(answerTo("""
                holder: make object! [a: 1]
                words-of holder""")).isEqualTo("[a]");
    }

    @Test
    @DisplayName("but an assignment to self is refused wherever it is written")
    void anAssignmentToSelfIsRefusedWhereverItIsWritten() {
        assertThat(failureOf("use [x] [self: 5]")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                inside: func [x] [self: 5]
                inside 1""")).isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("a refused addition adds no field at all, not even the ones before it")
    void aRefusedAdditionAddsNoFieldAtAll() {
        assertThat(answerTo("""
                holder: make object! [a: 1]
                try [append holder [b: 2 self: 5]]
                words-of holder""")).isEqualTo("[a]");
        assertThat(answerTo("""
                holder: make object! [a: 1]
                try [append holder [self: 5 b: 2]]
                words-of holder""")).isEqualTo("[a]");
    }

    @Test
    @DisplayName("a field the object may have is still added when self is nowhere in the block")
    void aFieldTheObjectMayHaveIsStillAdded() {
        assertThat(answerTo("""
                holder: make object! [a: 1]
                append holder [b: 2]
                words-of holder""")).isEqualTo("[a b]");
    }

    @Test
    @DisplayName("a word merely spelled like self somewhere else is not refused")
    void aWordSpelledLikeSelfElsewhereIsNotRefused() {
        assertThat(failureOf("set 'selfish 5")).isEqualTo("[ok]");
        assertThat(failureOf("make object! [myself: 5]")).isEqualTo("[ok]");
        assertThat(failureOf("""
                make object! [a: "self"]""")).isEqualTo("[ok]");
        assertThat(failureOf("func [self] []")).isEqualTo("[ok]");
    }

    @Test
    @DisplayName("PROTECT and UNPROTECT on self both succeed and change nothing")
    void protectAndUnprotectOnSelfBothSucceed() {
        assertThat(failureOf("protect 'self")).isEqualTo("[ok]");
        assertThat(failureOf("unprotect 'self")).isEqualTo("[ok]");
        assertThat(failureOf("""
                unprotect 'self
                set 'self 5""")).isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("self still reads as the object it belongs to")
    void selfStillReadsAsTheObjectItBelongsTo() {
        assertThat(answerTo("""
                holder: make object! [a: 1 whose: does [self]]
                same? holder holder/whose""")).isEqualTo("#(true)");
    }
}
