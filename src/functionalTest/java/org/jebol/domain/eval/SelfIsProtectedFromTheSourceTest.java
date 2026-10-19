package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfIsProtectedFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String failureOf(String source) {
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
    @DisplayName("an assignment to self inside USE or a function that does not declare it reaches the script's own self, and is refused")
    void anAssignmentReachingTheScriptsOwnSelfIsRefused() {
        assertThat(failureOf("use [x] [self: 5]")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                inside: func [x] [self: 5]
                inside 1""")).isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("a field spelt self in a context with no self of its own is written by a set-word bound to it")
    void aSetWordReachingAFieldSpeltSelfWritesIt() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                append holder [self 7]
                do bind [self: 8] holder
                holder/self""")).isEqualTo("8");
    }

    @Test
    @DisplayName("and by SET through IN, and SET/ANY can clear it")
    void setThroughInWritesAFieldSpeltSelf() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                append holder [self 7]
                set in holder 'self 8
                holder/self""")).isEqualTo("8");
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                append holder [self 7]
                set/any in holder 'self ()
                value? in holder 'self""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("and by UNSET")
    void unsetClearsAFieldSpeltSelf() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                append holder [self 7]
                unset in holder 'self
                value? in holder 'self""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("and by SET of a block across the context's fields")
    void setOfABlockWritesAFieldSpeltSelf() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                append holder [self 7]
                set holder [8 9]
                reduce [holder/x holder/self]""")).isEqualTo("[8 9]");
    }

    @Test
    @DisplayName("and through a path")
    void aPathWritesAFieldSpeltSelf() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                append holder [self 7]
                holder/self: 5
                holder/self""")).isEqualTo("5");
    }

    @Test
    @DisplayName("an object made from a map with a self key has an ordinary self field, written by a path, SET IN and SET")
    void anObjectsFieldSpeltSelfIsWritten() {
        assertThat(answerTo("""
                holder: make object! make map! [self 2]
                holder/self: 3
                holder/self""")).isEqualTo("3");
        assertThat(answerTo("""
                holder: make object! make map! [self 2]
                set in holder 'self 4
                holder/self""")).isEqualTo("4");
        assertThat(answerTo("""
                holder: make object! make map! [self 2]
                set holder [9]
                holder/self""")).isEqualTo("9");
    }

    @Test
    @DisplayName("a map's self key is written through a path like any other key")
    void aMapsSelfKeyIsWrittenThroughAPath() {
        assertThat(answerTo("""
                holder: make map! [self 2]
                holder/self: 3
                holder/self""")).isEqualTo("3");
    }

    @Test
    @DisplayName("an argument or a USE word spelt self is an ordinary word")
    void anArgumentOrUseWordSpeltSelfIsOrdinary() {
        assertThat(answerTo("""
                inside: func [self] [self: 4 self]
                inside 9""")).isEqualTo("4");
        assertThat(answerTo("""
                inside: func [/local self] [self: 4 self]
                inside""")).isEqualTo("4");
        assertThat(answerTo("use [self] [self: 5 self]")).isEqualTo("5");
    }

    @Test
    @DisplayName("an object's own self written through a path is invalid-path naming the path and self")
    void anObjectsOwnSelfThroughAPathIsAnInvalidPath() {
        assertThat(answerTo("""
                holder: make object! [a: 1]
                e: try [holder/self: 1]
                reduce [e/id e/arg1 e/arg2]""")).isEqualTo("[invalid-path holder/self: self]");
    }

    @Test
    @DisplayName("CONSTRUCT refuses a spec that writes self, with /ONLY and /WITH too")
    void constructRefusesASpecThatWritesSelf() {
        assertThat(failureOf("construct [self: 3]")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("construct [a: 1 self: 3]")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("construct/only [self: 3]")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("construct/with [self: 3] make object! [b: 2]"))
                .isEqualTo("[Script self-protected]");
    }

    @Test
    @DisplayName("self at the top level is the user context, an object")
    void selfAtTheTopLevelIsTheUserContext() {
        assertThat(answerTo("type? self")).isEqualTo("#(object!)");
        assertThat(answerTo("same? self system/contexts/user")).isEqualTo("#(true)");
        assertThat(answerTo("same? self lib")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("and inside USE or a function that does not declare self")
    void selfInsideUseOrAFunctionIsTheUserContext() {
        assertThat(answerTo("use [x] [same? self system/contexts/user]")).isEqualTo("#(true)");
        assertThat(answerTo("""
                inside: func [x] [same? self system/contexts/user]
                inside 1""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an object's own self is still itself inside its body")
    void anObjectsOwnSelfIsItselfInsideItsBody() {
        assertThat(answerTo("""
                holder: make object! [whose: self]
                same? holder holder/whose""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BIND/NEW and BIND/SET of a block add every word an object lacks but self, which reaches the object")
    void bindingABlockAnewLeavesAnObjectsOwnSelf() {
        assertThat(answerTo("""
                holder: make object! [a: 1]
                bind/new [self b] holder
                words-of holder""")).isEqualTo("[a b]");
        assertThat(answerTo("""
                holder: make object! [a: 1]
                bind/set [self: 1 b: 2] holder
                words-of holder""")).isEqualTo("[a b]");
        assertThat(answerTo("""
                holder: make object! [a: 1]
                same? holder do bind/new [self] holder""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("but a context with no self of its own gains a self field from BIND/NEW")
    void bindingAnewGivesAContextWithNoSelfAField() {
        assertThat(answerTo("""
                holder: context? use [x] ['x]
                bind/new [self] holder
                words-of holder""")).isEqualTo("[x self]");
    }

    @Test
    @DisplayName("a script's own words reach the user context without self becoming a field of it")
    void aScriptsWordsLeaveTheUserContextWithoutASelfField() {
        assertThat(answerTo("""
                intern [self fresh-word]
                find words-of system/contexts/user 'self""")).isEqualTo("_");
    }

    @Test
    @DisplayName("CONSTRUCT still makes an object from a spec that does not write self")
    void constructStillMakesAnObject() {
        assertThat(answerTo("words-of construct [a: 1]")).isEqualTo("[a]");
        assertThat(answerTo("get in construct [a: self] 'a")).isEqualTo("self");
    }

    @Test
    @DisplayName("an object's own self is refused from inside a function its body made")
    void anObjectsOwnSelfFromInsideItsFunction() {
        assertThat(failureOf("""
                holder: make object! [f: does [self: 1]]
                holder/f""")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                holder: make object! [f: does [set 'self 1]]
                holder/f""")).isEqualTo("[Script self-protected]");
        assertThat(failureOf("""
                holder: make object! [f: does [unset 'self]]
                holder/f""")).isEqualTo("[Script self-protected]");
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
