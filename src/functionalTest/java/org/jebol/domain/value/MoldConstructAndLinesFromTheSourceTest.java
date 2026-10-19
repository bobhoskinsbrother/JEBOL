package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoldConstructAndLinesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a url or an email that would not read back")
    class TheConstructFallback {

        @Test
        @DisplayName("a url holding a space molds as a construct")
        void coversAUrlWithASpace() {
            assertThat(answerTo("""
                    (mold to url! "a b") = {#(url! "a b")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a url holding a quote molds as a construct")
        void coversAUrlWithAQuote() {
            assertThat(answerTo("""
                    (mold to url! {a"b}) = {#(url! {a"b})}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty url molds as a construct, having nothing to read back")
        void coversTheEmptyUrl() {
            assertThat(answerTo("""
                    (mold to url! "") = {#(url! "")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a url with no colon would read back as a word")
        void coversAUrlWithoutAColon() {
            assertThat(answerTo("""
                    (mold to url! "nocolon") = {#(url! "nocolon")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an email with no at-sign would not read back as an email")
        void coversAnEmailWithoutAnAtSign() {
            assertThat(answerTo("""
                    (mold to email! "noatsign") = {#(email! "noatsign")}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nor one with the at-sign at the front")
        void coversAnEmailBeginningWithTheAtSign() {
            assertThat(answerTo("""
                    (mold to email! "@b") = {#(email! "@b")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nor one with it at the end")
        void coversAnEmailEndingWithTheAtSign() {
            assertThat(answerTo("""
                    (mold to email! "a@") = {#(email! "a@")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an ordinary url molds bare, which is the whole point of the test")
        void leavesTheOrdinaryUrlOut() {
            assertThat(answerTo("""
                    (mold to url! "http://x") = {http://x}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and an ordinary email does too")
        void leavesTheOrdinaryEmailOut() {
            assertThat(answerTo("""
                    (mold to email! "a@b") = {a@b}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a positioned url carries its position into the construct")
        void keepsTheIndexOfAPositionedUrl() {
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
        void isABlockForAPath() {
            assertThat(answerTo("""
                    (mold/all next next 'p/p) = {#(path! [p p] 3)}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string writes its whole text quoted")
        void holdsTheWholeTextOfAString() {
            assertThat(answerTo("""
                    (mold/all next "abc") = {#(string! "abc" 2)}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty tag is a construct under /ALL, because <> reads back as a word")
        void writesTheEmptyTagUnderAll() {
            assertThat(answerTo("""
                    (mold/all to tag! "") = {#(tag! "")}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where plain MOLD writes it as the two brackets")
        void writesTheEmptyTagWithoutAll() {
            assertThat(answerTo("""
                    (mold to tag! "") = {<>}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a tag with something in it needs no construct")
        void leavesATagWithContentOut() {
            assertThat(answerTo("""
                    (mold/all to tag! "a") = {<a>}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a block carrying line breaks molds one item to a line")
    class TheLinedBlock {

        @Test
        @DisplayName("a break after the first item is bare, with no indent")
        void breaksAtOneFlaggedPosition() {
            assertThat(answerTo("""
                    b: [1 2 3] new-line next b true
                    (mold b) = {[1^/2 3]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a break at every position gives one item to a line")
        void breaksAtEveryFlaggedPosition() {
            assertThat(answerTo("""
                    b: [1 2 3] new-line/all b true
                    (mold b) = {[^/    1^/    2^/    3^/]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a block with no breaks stays on one line")
        void breaksNowhereWithNoFlag() {
            assertThat(answerTo("""
                    (mold [1 2 3]) = {[1 2 3]}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("BODY-OF an object flags every set-word")
        void breaksBeforeEverySetWordOfBodyOf() {
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
        void holdsTheHeaderBytes() {
            assertThat(answerTo("""
                    (to string! save/header none [1 + 1] [title: "t" version: 1.0.0])
                        = {REBOL [^/    title: "t"^/    version: 1.0.0^/]^/1 + 1^/}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty header is the two brackets on the same line")
        void holdsTheEmptyHeaderBytes() {
            assertThat(answerTo("""
                    (to string! save/header none [1] []) = {REBOL []^/1^/}"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and no header at all writes only the body")
        void isLeftOutWithNoHeader() {
            assertThat(answerTo("""
                    (to string! save none [1]) = {1^/}""")).isEqualTo("#(true)");
        }
    }
}
