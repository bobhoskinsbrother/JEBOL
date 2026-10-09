package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnObjectsOwnSelfIsNoFieldFromTheSourceTest {

    private final Interpreter interpreter = Interpreter.create();

    private String answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("an object's own self")
    class ItsOwnSelf {

        @Test
        @DisplayName("is reached by a self word in the body it was made from")
        void aBoundSelfReachesTheObject() {
            assertThat(answerTo("""
                    o: object [a: 1 me: does [self]]
                    same? o o/me""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and by one written straight in that body")
        void aSelfInTheBodyReachesTheObject() {
            assertThat(answerTo("""
                    o: object [a: 1 b: self]
                    same? o o/b""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("is not a field a path can reach: o/self is invalid-path")
        void aPathCannotReachIt() {
            assertThat(answerTo("""
                    o: object [a: 1]
                    e: try [o/self]
                    reduce [e/id e/arg2]""")).isEqualTo("[invalid-path self]");
        }

        @Test
        @DisplayName("IN finds no self in it")
        void inFindsNone() {
            assertThat(answerTo("""
                    o: object [a: 1]
                    in o 'self""")).isEqualTo("_");
        }

        @Test
        @DisplayName("WORDS-OF, VALUES-OF and LENGTH? leave it out")
        void theFieldListsLeaveItOut() {
            assertThat(answerTo("""
                    o: object [a: 1 b: 2]
                    reduce [words-of o values-of o length? o]""")).isEqualTo("[[a b] [1 2] 2]");
        }

        @Test
        @DisplayName("MOLD leaves it out")
        void moldLeavesItOut() {
            assertThat(answerTo("""
                    {make object! [^/    a: 1^/]} = mold object [a: 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an object with no fields at all is still not selfless")
        void anEmptyObjectIsNotSelfless() {
            assertThat(answerTo("""
                    reduce [selfless? object [] length? object []]""")).isEqualTo("[#(false) 0]");
        }

        @Test
        @DisplayName("a copy made with MAKE answers its own self, not the prototype's")
        void aCopysSelfIsItsOwn() {
            assertThat(answerTo("""
                    o: object [a: 1 me: does [self]]
                    p: make o [b: 2]
                    reduce [same? p p/me  same? o p/me]""")).isEqualTo("[#(true) #(false)]");
        }
    }

    @Nested
    @DisplayName("a field spelt self, made from a map's key")
    class AFieldSpeltSelf {

        @Test
        @DisplayName("is an ordinary field, molded with the rest")
        void itIsMolded() {
            assertThat(answerTo("""
                    {make object! [^/    self: 2^/    a: 1^/]} = mold make object! make map! [self 2 a 1]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and counted, listed and valued with the rest")
        void itIsListed() {
            assertThat(answerTo("""
                    o: make object! make map! [self 2 a 1]
                    reduce [words-of o values-of o length? o]""")).isEqualTo("[[self a] [2 1] 2]");
        }

        @ParameterizedTest(name = "{0} answers {1}")
        @CsvSource(delimiter = '|', value = {
                "o/self              | 2",
                "get in o 'self      | 2",
                "same? o o/self      | #(false)",
                "selfless? o         | #(false)",
                "{[^/    self: 2^/]} = mold body-of o | #(true)",
        })
        @DisplayName("and read by path, IN and GET as itself, not as the object")
        void itIsReadAsItself(String asked, String answer) {
            assertThat(answerTo("""
                    o: make object! make map! [self 2]
                    %s""".formatted(asked))).isEqualTo(answer);
        }

        @Test
        @DisplayName("and kept when MAKE copies the object")
        void aCopyKeepsIt() {
            assertThat(answerTo("""
                    o: make object! make map! [self 2 a 1]
                    {make object! [^/    self: 2^/    a: 1^/    b: 3^/]} = mold make o [b: 3]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a map with no self key gives an object whose path to self is invalid, as any object's is")
        void noSelfKeyNoField() {
            assertThat(answerTo("""
                    p: make object! make map! [a 1]
                    e: try [p/self]
                    reduce [words-of p e/id]""")).isEqualTo("[[a] invalid-path]");
        }
    }
}
