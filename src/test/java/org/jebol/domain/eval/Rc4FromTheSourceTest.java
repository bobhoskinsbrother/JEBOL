package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Rc4FromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("/KEY answers a context to encipher through")
    class TheContext {

        @Test
        @DisplayName("it is a handle")
        void itIsAHandle() {
            assertThat(answerTo("handle? rc4/key #{0102030405}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("of type RC4, which is the only thing it will say about itself")
        void itNamesItsType() {
            assertThat(answerTo("k: rc4/key #{0102030405}  k/type = 'rc4"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("k: rc4/key #{0102030405}  word? k/type"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and TYPE is the whole of what WORDS-OF finds on it")
        void wordsOfNamesTheTypeAlone() {
            assertThat(answerTo("k: rc4/key #{0102030405}  mold words-of k"))
                    .isEqualTo("\"[type]\"");
        }

        @Test
        @DisplayName("an empty key is accepted, because there is a permutation for one")
        void anEmptyKeyIsAccepted() {
            assertThat(answerTo("handle? rc4/key #{}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("two contexts from one key are different handles")
        void twoContextsAreDistinct() {
            assertThat(answerTo("not same? (rc4/key #{01}) (rc4/key #{01})"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("though EQUAL? compares the type alone, so any two are equal")
        void equalityComparesTheTypeOnly() {
            assertThat(answerTo("equal? (rc4/key #{01}) (rc4/key #{02})")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and RC4 is in the handle catalogue a script can read")
        void theTypeIsCatalogued() {
            assertThat(answerTo("true? find system/catalog/handles 'rc4")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("/STREAM enciphers in place")
    class TheStream {

        @Test
        @DisplayName("the ciphertext is what a real 3.22.1 produces")
        void theCiphertextMatchesTheBinary() {
            assertThat(answerTo("""
                    d: #{AABBCCDD}
                    rc4/stream rc4/key #{0102030405} d
                    d""")).isEqualTo("#{1882AFD8}");
        }

        @Test
        @DisplayName("it rewrites the binary it was given rather than answering a copy")
        void itRewritesTheArgument() {
            assertThat(answerTo("""
                    d: #{AABBCCDD}
                    rc4/stream rc4/key #{0102030405} d
                    d = #{1882AFD8}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and answers that same binary, not a fresh one")
        void itAnswersTheSameValue() {
            assertThat(answerTo("""
                    d: #{AABBCCDD}
                    same? d rc4/stream rc4/key #{0102030405} d""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a fresh context with the same key turns it back")
        void theCipherIsItsOwnInverse() {
            assertThat(answerTo("""
                    d: #{AABBCCDD}
                    rc4/stream rc4/key #{0102030405} d
                    rc4/stream rc4/key #{0102030405} d
                    d = #{AABBCCDD}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the permutation advances, so one context used twice does not")
        void oneContextDoesNotUndoItself() {
            assertThat(answerTo("""
                    k: rc4/key #{0102030405}
                    d: #{AABBCCDD}
                    rc4/stream k d
                    rc4/stream k d
                    d = #{AABBCCDD}""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("enciphering nothing writes nothing and does not complain")
        void emptyDataIsAccepted() {
            assertThat(answerTo("""
                    d: #{}
                    rc4/stream rc4/key #{01} d
                    d = #{}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it works from where the binary stands, leaving the bytes before it")
        void itWorksFromThePosition() {
            assertThat(answerTo("""
                    p: #{AABBCCDD}
                    rc4/stream rc4/key #{01} next p
                    p""")).isEqualTo("#{AABDC4D3}");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @Test
        @DisplayName("a handle of another type is invalid-handle, not silently misread")
        void aHandleOfAnotherTypeIsRefused() {
            assertThat(answerTo("""
                    e: try [rc4/stream system/codecs/text/entry #{0102}] e/id"""))
                    .isEqualTo("invalid-handle");
        }

        @Test
        @DisplayName("something that is not a handle at all is refused by the declaration")
        void aNonHandleIsRefused() {
            assertThat(answerTo("""
                    e: try [rc4/stream "notahandle" #{00}] e/id""")).isEqualTo("expect-arg");
            assertThat(answerTo("""
                    e: try [rc4/stream 42 #{00}] e/id""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and data that is not a binary likewise")
        void nonBinaryDataIsRefused() {
            assertThat(answerTo("""
                    e: try [rc4/stream rc4/key #{01} "text"] e/id"""))
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a key that is not a binary likewise")
        void aNonBinaryKeyIsRefused() {
            assertThat(answerTo("""
                    e: try [rc4/key "text"] e/id""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("asked for neither refinement it does nothing and answers nothing")
        void neitherRefinementAnswersUnset() {
            assertThat(answerTo("unset? rc4")).isEqualTo(TRUE);
        }
    }
}
