package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumberOfAnythingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the four number datatypes answer true")
    void theFourNumberDatatypesAnswerTrue() {
        assertThat(answerTo("""
                collect [foreach v [1 1.0 1% $1 0 -1 0.0] [keep number? :v]]"""))
                .isEqualTo("""
                        [#(true) #(true) #(true) #(true) #(true) #(true) #(true)]""");
    }

    @Test
    @DisplayName("a NaN is the decimal that is not a number")
    void aNanIsTheDecimalThatIsNotANumber() {
        assertThat(answerTo("reduce [number? 1.#NaN  number? 1.#inf]"))
                .isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("everything else answers false rather than refusing")
    void everythingElseAnswersFalse() {
        assertThat(answerTo("""
                collect [
                    foreach v ["1" [] #(none) #(true) #"a" 1:00 1-Jan-2020 'w 1x1] [
                        keep number? :v
                    ]
                ]""")).isEqualTo("""
                        [#(false) #(false) #(false) #(false) #(false) #(false) \
                        #(false) #(false) #(false)]""");
    }

    @Test
    @DisplayName("and an unset is a question it will answer, which is what it is for")
    void anUnsetIsAQuestionItWillAnswer() {
        assertThat(answerTo("reduce [number? ()  number? #(unset)]"))
                .isEqualTo("[#(false) #(false)]");
    }
}
