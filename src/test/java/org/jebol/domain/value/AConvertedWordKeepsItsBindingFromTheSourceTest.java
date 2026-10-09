package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AConvertedWordKeepsItsBindingFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("to set-word! of a word bound to an object sets that object's field")
    void aSetWordMadeFromABoundWordSetsTheField() {
        assertThat(answerTo("""
                o: object [x: 1]
                w: to set-word! in o 'x
                do reduce [w 2]
                o/x""")).isEqualTo("2");
    }

    @ParameterizedTest(name = "to {0} of a word bound to an object still reads the field")
    @ValueSource(strings = {"word!", "get-word!", "lit-word!", "refinement!", "issue!"})
    @DisplayName("TO from a bound word to another kind of word keeps the binding")
    void toKeepsTheBinding(String kind) {
        assertThat(answerTo("""
                o: object [x: 1]
                get to %s in o 'x""".formatted(kind))).isEqualTo("1");
    }

    @Test
    @DisplayName("make word! of a bound word keeps the binding")
    void makeKeepsTheBinding() {
        assertThat(answerTo("""
                o: object [x: 1]
                get make word! in o 'x""")).isEqualTo("1");
    }

    @Test
    @DisplayName("a word read from text is bound to nothing")
    void aWordFromTextIsUnbound() {
        assertThat(answerTo("""
                x: 99
                e: try [get to word! "x"]
                e/id""")).isEqualTo("not-defined");
    }

    @Test
    @DisplayName("a converted word keeps its spelling and changes only its kind")
    void onlyTheKindChanges() {
        assertThat(answerTo("""
                o: object [Abc: 1]
                w: to set-word! in o 'Abc
                reduce [type? w mold w]""")).isEqualTo("[#(set-word!) \"Abc:\"]");
    }
}
