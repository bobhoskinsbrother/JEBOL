package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An angle bracket part-way through a value, from {@code scanword} in
 * {@code rebol3-source/src/core/l-scan.c}.
 *
 * <p>Two rules meet here, and which one applies depends on what came before the
 * bracket.
 *
 * <p><b>A number simply ends.</b> A path is assembled from separate tokens, so the
 * last segment of {@code a/3<} is scanned as a number, and a number stops at any
 * character that is not a digit. The word rule is never consulted. The same holds
 * without a path: {@code 1<}, {@code 1.0<a>} and {@code 1.#INF<} all end at the
 * bracket, and {@code WordCharactersTest} has pinned that half for a while.
 *
 * <p><b>A word obeys {@code scanword}</b>, whose comment states the rule outright:
 * "Allow word&lt;tag&gt; and word&lt;/tag&gt; but not word&lt; word&lt;=
 * word&lt;&gt; etc."
 *
 * <pre>
 * cp = Skip_To_Char(cp, scan_state-&gt;end, '&lt;');
 * if (cp[1] == '&lt;' || cp[1] == '&gt;' || cp[1] == '=' ||
 *     IS_LEX_SPACE(cp[1]) || (cp[1] != '/' &amp;&amp; IS_LEX_DELIMIT(cp[1])))
 *     return -type;
 * scan_state-&gt;end = cp;
 * </pre>
 *
 * <p>So the character after the bracket decides. A name or a slash means a tag or an
 * arrow word is beginning and the word is finished. Another bracket, an equals, a
 * space or the end of input means somebody wrote an operator hard against a name,
 * and that is a mistake rather than two values.
 *
 * <p>Which is what separates {@code a/3<} from {@code a/b<}: the same path shape,
 * the same bracket at the same place, and the last segment is the whole difference.
 * Rebol's own lexer test asserts the pair side by side, and no single reading of
 * either rule explains both.
 */
class WordThenAngleFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /**
     * Whether loading this failed as a refused word.
     *
     * <p>Both halves asked at once, as Rebol's own test asks them:
     * {@code all [error? e: try [load {a/b<}] e/id = 'invalid e/arg1 = "word"]}.
     * Molding the pair instead would put the answer inside a string that itself
     * holds quotes, and then the assertion is about how a string displays.
     */
    private static String refusedAsAWord(String source) {
        return answerTo("e: try [load " + source + "] "
                + "all [error? e e/id = 'invalid e/arg1 = \"word\"]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("after a word, the bracket either ends it or spoils it")
    class AfterAWord {

        @Test
        @DisplayName("a tag after a name is a name and a tag")
        void aWordThenATag() {
            assertThat(answerTo("""
                    mold load {a<a>}""")).isEqualTo("\"[a <a>]\"");
            assertThat(answerTo("""
                    parse load {a<a>} [word! tag!]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a closing tag too, which is the one delimiter excused")
        void aWordThenAClosingTag() {
            assertThat(answerTo("""
                    mold load {a</a>}""")).isEqualTo("\"[a </a>]\"");
        }

        @Test
        @DisplayName("and an arrow word after a name is two words")
        void aWordThenAnArrow() {
            assertThat(answerTo("""
                    mold load {a<--}""")).isEqualTo("\"[a <--]\"");
            assertThat(answerTo("""
                    parse load {a<--} [word! word!]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a comparison written hard against a name is refused, not split")
        void anOperatorAgainstAName() {
            assertThat(refusedAsAWord("""
                    {a<=}""")).isEqualTo(TRUE);
            assertThat(refusedAsAWord("""
                    {a<>}""")).isEqualTo(TRUE);
            assertThat(refusedAsAWord("""
                    {a<<}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and so is a bracket with nothing after it")
        void aBracketAtTheEnd() {
            assertThat(refusedAsAWord("""
                    {a<}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("after a number, it just ends")
    class AfterANumber {

        @Test
        @DisplayName("a number ends at the bracket, whatever follows it")
        void aNumberEnds() {
            assertThat(answerTo("""
                    mold load {1<}""")).isEqualTo("\"[1 <]\"");
            assertThat(answerTo("""
                    mold load {1.2<}""")).isEqualTo("\"[1.2 <]\"");
            assertThat(answerTo("""
                    mold load {1.0<a>}""")).isEqualTo("\"[1.0 <a>]\"");
        }

        @Test
        @DisplayName("and so does anything else that is not a word")
        void aScalarEnds() {
            assertThat(answerTo("""
                    mold load {19-Jan-2010<}""")).isEqualTo("\"[19-Jan-2010 <]\"");
            assertThat(answerTo("""
                    mold load {1.#INF<}""")).isEqualTo("\"[1.#INF <]\"");
        }
    }

    @Nested
    @DisplayName("and in a path the last segment decides")
    class InsideAPath {

        @Test
        @DisplayName("a numeric last segment ends at the bracket")
        void aNumericSegment() {
            assertThat(answerTo("""
                    mold load {a/3<}""")).isEqualTo("\"[a/3 <]\"");
            assertThat(answerTo("""
                    (first load {a/3<}) = 'a/3""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a word last segment is refused, which is the same bracket")
        void aWordSegment() {
            assertThat(refusedAsAWord("""
                    {a/b<}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the pair together, because either alone reads as a rule about paths")
        void thePairSideBySide() {
            assertThat(answerTo("""
                    block? load {a/3<}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    error? try [load {a/b<}]""")).isEqualTo(TRUE);
        }
    }
}
