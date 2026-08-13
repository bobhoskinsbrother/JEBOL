package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mold_Url writes a url bare only where the scanner would read it back as the
 * same value, and falls back to construct syntax everywhere else. Inside a
 * construct the datatype is already named, so Mold_Block writes a path's
 * segments as a block and Mold_All_String forces a positioned string to its
 * plain quoted text. The line breaks a block carries are rendered by
 * {@code New_Indented_Line} at each flagged position, and BODY-OF flags every
 * set-word with {@code VAL_SET_LINE} -- which together are the bytes a saved
 * script's header is checksummed on.
 */
class MoldConstructAndLinesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a url or an email that would not read back")
    class TheConstructFallback {

        @Test
        @DisplayName("a url holding a space molds as a construct")
        void aUrlWithASpace() {
            assertThat(answerTo("""
                    (mold to url! "a b") = {#(url! "a b")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a url holding a quote molds as a construct")
        void aUrlWithAQuote() {
            assertThat(answerTo("""
                    (mold to url! {a"b}) = {#(url! {a"b})}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty url molds as a construct, having nothing to read back")
        void theEmptyUrl() {
            assertThat(answerTo("""
                    (mold to url! "") = {#(url! "")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a url with no colon would read back as a word")
        void aUrlWithoutAColon() {
            assertThat(answerTo("""
                    (mold to url! "nocolon") = {#(url! "nocolon")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an email with no at-sign would not read back as an email")
        void anEmailWithoutAnAtSign() {
            assertThat(answerTo("""
                    (mold to email! "noatsign") = {#(email! "noatsign")}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nor one with the at-sign at the front")
        void anEmailBeginningWithTheAtSign() {
            assertThat(answerTo("""
                    (mold to email! "@b") = {#(email! "@b")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nor one with it at the end")
        void anEmailEndingWithTheAtSign() {
            assertThat(answerTo("""
                    (mold to email! "a@") = {#(email! "a@")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an ordinary url molds bare, which is the whole point of the test")
        void theOrdinaryUrlIsTheOffPoint() {
            assertThat(answerTo("""
                    (mold to url! "http://x") = {http://x}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and an ordinary email does too")
        void theOrdinaryEmailIsTheOffPoint() {
            assertThat(answerTo("""
                    (mold to email! "a@b") = {a@b}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a positioned url carries its position into the construct")
        void aPositionedUrlKeepsItsIndex() {
            assertThat(answerTo("""
                    (mold/all next to url! "http://x") = {#(url! "http://x" 2)}"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a positioned series molds its body in the construct's notation")
    class TheConstructBody {

        @Test
        @DisplayName("a path writes its segments as a block")
        void aPathWritesABlockBody() {
            assertThat(answerTo("""
                    (mold/all next next 'p/p) = {#(path! [p p] 3)}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string writes its whole text quoted")
        void aStringWritesItsWholeText() {
            assertThat(answerTo("""
                    (mold/all next "abc") = {#(string! "abc" 2)}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty tag is a construct under /ALL, because <> reads back as a word")
        void theEmptyTagUnderAll() {
            assertThat(answerTo("""
                    (mold/all to tag! "") = {#(tag! "")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where plain MOLD writes it as the two brackets")
        void theEmptyTagWithoutAll() {
            assertThat(answerTo("""
                    (mold to tag! "") = {<>}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a tag with something in it needs no construct")
        void aTagWithContentIsTheOffPoint() {
            assertThat(answerTo("""
                    (mold/all to tag! "a") = {<a>}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a block carrying line breaks molds one item to a line")
    class TheLinedBlock {

        @Test
        @DisplayName("a break at one position breaks only there")
        void oneFlaggedPosition() {
            assertThat(answerTo("""
                    b: [1 2 3] new-line next b true
                    (mold b) = {[1^/    2 3^/]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a break at every position gives one item to a line")
        void everyPositionFlagged() {
            assertThat(answerTo("""
                    b: [1 2 3] new-line/all b true
                    (mold b) = {[^/    1^/    2^/    3^/]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block with no breaks stays on one line")
        void noPositionFlagged() {
            assertThat(answerTo("""
                    (mold [1 2 3]) = {[1 2 3]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("BODY-OF an object flags every set-word")
        void bodyOfFlagsEverySetWord() {
            assertThat(answerTo("""
                    (mold body-of make object! [a: 1 b: 2]) = {[^/    a: 1^/    b: 2^/]}"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a saved script is those two rules written out")
    class TheSavedHeader {

        @Test
        @DisplayName("the header is a molded object body under the word REBOL")
        void theHeaderBytes() {
            assertThat(answerTo("""
                    (to string! save/header none [1 + 1] [title: "t" version: 1.0.0])
                        = {REBOL [^/    title: "t"^/    version: 1.0.0^/]^/1 + 1^/}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty header is the two brackets on the same line")
        void theEmptyHeaderBytes() {
            assertThat(answerTo("""
                    (to string! save/header none [1] []) = {REBOL []^/1^/}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and no header at all writes only the body")
        void noHeaderAtAll() {
            assertThat(answerTo("""
                    (to string! save none [1]) = {1^/}""")).isEqualTo("#(true)");
        }
    }
}
