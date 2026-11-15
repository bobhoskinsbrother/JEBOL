package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToBinaryOfAnImageFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @ParameterizedTest(name = "{0} answers {1}")
    @DisplayName("TO BINARY! answers the pixels from the image's index to its tail, red, green, blue and alpha, as Make_Image_Binary copies them")
    @CsvSource(delimiter = '|', textBlock = """
            to binary! img                  | #{FF0000FF00FF00FF0000FFFF}
            to binary! skip img 1           | #{00FF00FF0000FFFF}
            to binary! skip img 2           | #{0000FFFF}
            to binary! tail img             | #{}
            length? to binary! make image! 1920x1080 | 8294400
            """)
    void answersThePixelsFromHere(String converted, String wanted) {
        assertThat(answerTo("img: make image! [3x1 #{FF000000FF000000FF}] " + converted)).isEqualTo(wanted);
    }
}
