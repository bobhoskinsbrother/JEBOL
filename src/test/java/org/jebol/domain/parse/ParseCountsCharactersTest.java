package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PARSE counts the subject in characters, not in Java's sixteen-bit halves.
 *
 * <p>The parser walked a Java string and kept its position as an index into
 * one, so a subject holding anything above the basic plane made the position
 * and the count disagree from that character onwards. SKIP landed in the
 * middle of a character, and building a character out of half of one threw an
 * IllegalArgumentException clean out of the interpreter -- the one failure
 * the evaluator promises never to produce.
 *
 * <p>The parts that already counted by character were the ones that were
 * right: CHANGE moving past what it wrote, INSERT moving past what it put in.
 * Everything else was measuring in the other unit, which is why the two only
 * disagreed on subjects nobody had tried.
 */
class ParseCountsCharactersTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("walking a subject with a wide character in it")
    class Walking {

        @Test
        @DisplayName("SKIP moves one whole character at a time")
        void skipMovesOneCharacter() {
            assertThat(answerTo("""
                    parse "a🙂b" [skip skip skip]""")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    parse "a🙂b" [skip skip]""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a count of skips counts characters")
        void aCountOfSkips() {
            assertThat(answerTo("""
                    reduce [parse "a🙂b" [3 skip] parse "a🙂b" [4 skip]]"""))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("COPY takes whole characters")
        void copyTakesWholeCharacters() {
            assertThat(answerTo("""
                    parse "áb🙂" [copy x 2 skip to end]
                    x""")).isEqualTo("\"áb\"");
        }

        @Test
        @DisplayName("and one skip copies the whole character, not half of it")
        void oneSkipCopiesTheWholeCharacter() {
            assertThat(answerTo("""
                    parse "🙂b" [copy x skip to end]
                    reduce [x length? x]""")).isEqualTo("[\"🙂\" 1]");
        }

        @Test
        @DisplayName("KEEP of one skip is the character, which used to throw")
        void keepOfOneSkip() {
            assertThat(answerTo("""
                    parse "áb🙂" [collect [keep 2 skip keep skip]]"""))
                    .isEqualTo("[\"áb\" #\"🙂\"]");
        }

        @Test
        @DisplayName("SET takes the character too")
        void setTakesTheCharacter() {
            assertThat(answerTo("""
                    parse "a🙂" [skip set c skip]
                    reduce [c to integer! c]""")).isEqualTo("[#\"🙂\" 128578]");
        }
    }

    @Nested
    @DisplayName("matching against one")
    class Matching {

        @Test
        @DisplayName("a literal holding a wide character matches it")
        void aWideLiteralMatches() {
            assertThat(answerTo("""
                    reduce [parse "a🙂b" ["a" "🙂" "b"] parse "a🙂b" ["a🙂b"]]"""))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("TO and THRU find one and land on a character boundary")
        void toAndThruLandOnABoundary() {
            assertThat(answerTo("""
                    parse "ab🙂cd" [to "🙂" copy x to end]
                    x""")).isEqualTo("\"🙂cd\"");
            assertThat(answerTo("""
                    parse "ab🙂cd" [thru "🙂" copy x to end]
                    x""")).isEqualTo("\"cd\"");
        }

        @Test
        @DisplayName("a charset holding one matches it and nothing else")
        void aCharsetHoldingOne() {
            assertThat(answerTo("""
                    wide: charset "🙂"
                    reduce [parse "🙂" [wide] parse "a" [wide]]"""))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("and the position after a match counts characters")
        void thePositionAfterAMatch() {
            assertThat(answerTo("""
                    parse "a🙂bc" ["a🙂" mark: (found: index? mark) to end]
                    found""")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("changing a subject that holds one")
    class Changing {

        @Test
        @DisplayName("CHANGE puts a wide character in and moves past all of it")
        void changePutsOneIn() {
            assertThat(answerTo("""
                    s: copy "abc"
                    parse s [change skip "🙂" copy rest to end]
                    reduce [s rest]""")).isEqualTo("[\"🙂bc\" \"bc\"]");
        }

        @Test
        @DisplayName("REMOVE takes a whole character out")
        void removeTakesOneOut() {
            assertThat(answerTo("""
                    s: copy "a🙂b"
                    parse s [skip remove skip to end]
                    reduce [s length? s]""")).isEqualTo("[\"ab\" 2]");
        }

        @Test
        @DisplayName("INSERT puts one in without consuming anything")
        void insertPutsOneIn() {
            assertThat(answerTo("""
                    s: copy "ab"
                    parse s [insert "🙂" to end]
                    reduce [s length? s]""")).isEqualTo("[\"🙂ab\" 3]");
        }
    }

    @Nested
    @DisplayName("a binary, whose bytes stand in for characters")
    class Binaries {

        @Test
        @DisplayName("every byte is one step, including those above 127")
        void everyByteIsOneStep() {
            assertThat(answerTo("""
                    reduce [parse #{41F0429F} [4 skip] parse #{41F0429F} [5 skip]]"""))
                    .isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("and a byte comes back as its number")
        void aByteComesBackAsItsNumber() {
            assertThat(answerTo("""
                    parse #{41F042} [skip set b skip to end]
                    b""")).isEqualTo("240");
        }
    }
}
