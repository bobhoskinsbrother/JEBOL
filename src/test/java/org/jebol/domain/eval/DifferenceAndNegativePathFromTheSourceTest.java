package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DIFFERENCE over text, and a path index that counts backwards.
 *
 * <p>Two unrelated defects that a sweep of the unicode suite turned up
 * together, and both of the same shape: a form that worked for one datatype
 * and was never wired for the other.
 */
class DifferenceAndNegativePathFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("DIFFERENCE works over text as well as blocks")
    void differenceWorksOverText() {
        assertThat(answerTo("""
                reduce [
                    difference "ač" "čbš"
                    difference "ač🙂" "čbš"
                    difference "ab" "čbš🙂"
                ]""")).isEqualTo("[\"abš\" \"a🙂bš\" \"ačš🙂\"]");
    }

    @Test
    @DisplayName("and it is the symmetric one, taking from both sides")
    void differenceTakesFromBothSides() {
        assertThat(answerTo("""
                difference ["a" "b"] ["b" "c"]""")).isEqualTo("[\"a\" \"c\"]");
    }

    @Test
    @DisplayName("/CASE stops it folding, so two spellings are two members")
    void caseStopsItFolding() {
        assertThat(answerTo("""
                reduce [
                    difference ["a"] ["A"]
                    difference/case ["a"] ["A"]
                ]""")).isEqualTo("[[] [\"a\" \"A\"]]");
    }

    @Test
    @DisplayName("a negative path index counts back from the series position")
    void aNegativePathIndexCountsBack() {
        assertThat(answerTo("""
                s: tail "áb"
                reduce [s/-1 s/-2]""")).isEqualTo("[#\"b\" #\"á\"]");
    }

    @Test
    @DisplayName("including past a character that takes two of Java's")
    void aNegativeIndexOverAWideCharacter() {
        assertThat(answerTo("""
                s: tail "🙂b"
                reduce [s/-1 s/-2]""")).isEqualTo("[#\"b\" #\"🙂\"]");
    }

    @Test
    @DisplayName("and reaching past either end is none, not an error")
    void reachingPastEitherEndIsNone() {
        assertThat(answerTo("""
                reduce [
                    none? all [s: "áb" s/3]
                    none? all [s: tail "áb" s/-3]
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("PICK and a path agree, which is the whole point")
    void pickAndAPathAgree() {
        assertThat(answerTo("""
                s: tail "ab"
                reduce [(pick s -2) = s/-2 (pick s -1) = s/-1]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("FIND in a binary looks for the bytes that spell the text")
    void findInABinaryLooksForBytes() {
        assertThat(answerTo("""
                bin: to binary! "ačb"
                reduce [find bin #"č" find bin "čb" find/tail bin #"č"]"""))
                .isEqualTo("[#{C48D62} #{C48D62} #{62}]");
    }

    @Test
    @DisplayName("but a char up to 255 is that one byte, not its encoding")
    void aCharThatFitsInAByteIsThatByte() {
        assertThat(answerTo("""
                reduce [
                    find #{00FF} #"^(ff)"
                    tail? find/tail #{00FF} #"^(ff)"
                    find #{00C3BF} #"^(ff)"
                ]""")).isEqualTo("[#{FF} #(true) _]");
    }

    @Test
    @DisplayName("and a needle that is not there is still none")
    void aNeedleThatIsNotThereIsNone() {
        assertThat(answerTo("""
                needle: #"x"
                none? find (to binary! {ačb}) needle""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CHANGE counts the replacement in characters, not in Java's units")
    void changeCountsCharacters() {
        assertThat(answerTo("""
                collect [
                    keep all [(change o: "ábč" "č") == "bč"  o == "čbč"]
                    keep all [(change o: "abc" "🙂") == "bc"  o == "🙂bc"]
                    keep all [(change o: "ábč" "x🙂") == "č"  o == "x🙂č"]
                    keep all [(change o: "🙂bc" "a") == "bc"  o == "abc"]
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
    }
}
