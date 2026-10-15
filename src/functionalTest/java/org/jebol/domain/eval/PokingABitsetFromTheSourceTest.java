package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PokingABitsetFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("POKE answers the set it changed, not the logic it was handed")
    void pokeAnswersTheSetItChanged() {
        assertThat(answerTo("""
                reduce [
                    poke charset "a" #"b" true
                    poke charset "ab" #"b" false
                    poke charset [not "a"] #"b" true
                ]""")).isEqualTo("""
                        [#(bitset! #{00000000000000000000000060}) \
                        #(bitset! #{00000000000000000000000040}) \
                        #(bitset! not #{00000000000000000000000040})]""");
    }

    @Test
    @DisplayName("and it is the same set, so the answer can be used where it stands")
    void itIsTheSameSet() {
        assertThat(answerTo("""
                members: charset "a"
                reduce [same? members poke members #"c" true  members]"""))
                .isEqualTo("""
                        [#(true) #(bitset! #{00000000000000000000000050})]""");
    }

    @Test
    @DisplayName("a string or a range turns on every character it names")
    void aStringOrARangeTurnsOnEveryCharacterItNames() {
        assertThat(answerTo("""
                reduce [
                    poke charset "" "xy" true
                    poke charset "" [#"a" - #"c"] true
                ]""")).isEqualTo("""
                        [#(bitset! #{000000000000000000000000000000C0}) \
                        #(bitset! #{00000000000000000000000070})]""");
    }

    @Test
    @DisplayName("something that names no characters at all is refused")
    void somethingThatNamesNoCharactersIsRefused() {
        assertThat(answerTo("""
                e: try [poke charset "" 1x1 true]
                either error? e [e/id] ['accepted]""")).isEqualTo("invalid-type");
    }

    @Test
    @DisplayName("every other series still answers the value that was written")
    void everyOtherSeriesStillAnswersTheValueWritten() {
        assertThat(answerTo("""
                reduce [
                    poke [1 2 3] 1 9
                    poke "abc" 1 #"z"
                    poke make map! [a 1] 'a 2
                ]""")).isEqualTo("""
                        [9 #"z" 2]""");
    }
}
