package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a map answers about its keys, which is not how a Java map would.
 *
 * <p>{@code Find_Entry} takes a {@code cased} flag and its callers do not
 * agree about it: SELECT, FIND, PUT and a path read pass false, while MAKE and
 * REMOVE/KEY pass true. So the two ends of a map behave differently on
 * purpose -- building one keeps {@code "k"} and {@code "K"} apart, and looking
 * one up does not.
 *
 * <p>And an uncased lookup answers whichever key was stored first, not the one
 * that matches exactly. That is the part that reads as wrong and is right.
 */
class MapKeysFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String MIXED = """
            m: #[<a> 1 <A> 2 "a" 9 "A" 10 a 13 A 14 #"a" 21 #"A" 22 #{61} 23 #{41} 24]
            """;

    @Test
    @DisplayName("a lookup does not mind case, and answers the key stored first")
    void aLookupAnswersTheFirstStored() {
        assertThat(answerTo(MIXED + """
                reduce [select m <A> select m "A" select m 'A select m #"A"]"""))
                .isEqualTo("[1 9 13 21]");
    }

    @Test
    @DisplayName("SELECT/CASE answers the one that matches exactly")
    void selectCaseAnswersTheExactKey() {
        assertThat(answerTo(MIXED + """
                reduce [
                    select/case m <A>
                    select/case m "A"
                    select/case m 'A
                    select/case m #"A"
                ]""")).isEqualTo("[2 10 14 22]");
    }

    @Test
    @DisplayName("a binary key is exact either way, because bytes have no case")
    void aBinaryKeyIsAlwaysExact() {
        assertThat(answerTo(MIXED + """
                reduce [select m #{41} select/case m #{41}]""")).isEqualTo("[24 24]");
    }

    @Test
    @DisplayName("FIND answers the key it holds, and minds case when told to")
    void findAnswersTheStoredKey() {
        assertThat(answerTo("""
                f: #[aB: 1 ab: 2 AB: 3]
                reduce [find f 'ab find/case f 'ab find/case f 'Ab]"""))
                .isEqualTo("[aB: ab: _]");
    }

    @Test
    @DisplayName("two maps are equal whatever order their pairs were written in")
    void equalityIgnoresOrder() {
        assertThat(answerTo("""
                equal? #[a: 1 b: 2] #[b: 2 a: 1]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and EQUAL? does not mind the case of the values either")
    void equalityDoesNotMindCase() {
        assertThat(answerTo("""
                equal? #[a: 1 b: 2 c: "a"] #[b: 2 a: 1 c: "A"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("nor which sigil a key was written with, since a map stores one")
    void equalityIgnoresTheKeySigil() {
        assertThat(answerTo("""
                reduce [
                    equal? #[a: 1 b: 2 c: "a"] #[b: 2 a: 1 c "A"]
                    equal? #[a: 1 b: 2 c: "a"] #[b: 2 a: 1 'c "A"]
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a map with more pairs is not equal to one with fewer")
    void differentSizesAreNotEqual() {
        assertThat(answerTo("""
                equal? #[a: 1] #[b: 2 c: 3 a: 1]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("STRICT-EQUAL? still ignores order but does mind case")
    void strictEqualityMindsCase() {
        assertThat(answerTo("""
                reduce [
                    strict-equal? #[a: 1 b: 2] #[b: 2 a: 1]
                    strict-equal? #[a: 1 c: "a"] #[a: 1 c: "A"]
                ]""")).isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("a text key is copied, so the caller's own string is left alone")
    void aTextKeyIsCopied() {
        assertThat(answerTo("""
                k: ["a" 1]
                n: make map! k
                reduce [
                    same? first k first keys-of n
                    protected? first k
                    protected? first keys-of n
                ]""")).isEqualTo("[#(false) #(false) #(true)]");
    }

    @Test
    @DisplayName("and the copy is locked, because the map is hashed on it")
    void theCopyIsLocked() {
        assertThat(answerTo("""
                n: make map! ["a" 1]
                raised: try [append first keys-of n "x"]
                raised/id""")).isEqualTo("protected");
    }

    @Test
    @DisplayName("a block key is not copied, which the suite points out in a comment")
    void aBlockKeyIsShared() {
        assertThat(answerTo("""
                b: [c]
                p: make map! reduce [b 1]
                same? b first keys-of p""")).isEqualTo("#(true)");
    }
}
