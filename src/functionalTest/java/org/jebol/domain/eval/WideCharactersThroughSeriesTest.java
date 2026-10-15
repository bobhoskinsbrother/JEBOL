package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WideCharactersThroughSeriesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("REVERSE turns the characters round, not their halves")
    void reverseTurnsCharactersRound() {
        assertThat(answerTo("""
                reduce [reverse "a🙂b" length? reverse "a🙂b"]"""))
                .isEqualTo("[\"b🙂a\" 3]");
    }

    @Test
    @DisplayName("INSERT puts a whole character in")
    void insertPutsAWholeCharacterIn() {
        assertThat(answerTo("""
                o: copy "ab"
                insert o "x🙂"
                reduce [o length? o]""")).isEqualTo("[\"x🙂ab\" 4]");
    }

    @Test
    @DisplayName("CHANGE counts its replacement in characters")
    void changeCountsCharacters() {
        assertThat(answerTo("""
                o: copy "abc"
                change o "🙂"
                reduce [o length? o]""")).isEqualTo("[\"🙂bc\" 3]");
    }

    @Test
    @DisplayName("COPY/PART takes whole characters")
    void copyPartTakesWholeCharacters() {
        assertThat(answerTo("""
                wide: "🙂ab"
                reduce [copy/part wide 1 copy/part wide 2]"""))
                .isEqualTo("[\"🙂\" \"🙂a\"]");
    }

    @Test
    @DisplayName("FIND matches a needle holding one")
    void findMatchesAWideNeedle() {
        assertThat(answerTo("""
                reduce [find "a🙂bc" "🙂b" find "a🙂bc" "🙂"]"""))
                .isEqualTo("[\"🙂bc\" \"🙂bc\"]");
    }

    @Test
    @DisplayName("UPPERCASE leaves one alone and changes the letters round it")
    void uppercaseLeavesItAlone() {
        assertThat(answerTo("""
                reduce [uppercase copy "a🙂b" lowercase copy "A🙂B"]"""))
                .isEqualTo("[\"A🙂B\" \"a🙂b\"]");
    }

    @Test
    @DisplayName("and LENGTH? agrees with all of them")
    void lengthAgreesWithThemAll() {
        assertThat(answerTo("""
                collect [
                    foreach text ["🙂" "a🙂" "🙂a" "a🙂b" "🙂🙂"][
                        keep length? text
                    ]
                ]""")).isEqualTo("[1 2 2 3 2]");
    }
}
