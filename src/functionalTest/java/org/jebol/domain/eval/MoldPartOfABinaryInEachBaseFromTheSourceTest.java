package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MoldPartOfABinaryInEachBaseFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @ParameterizedTest(name = "base {0} of {1}")
    @DisplayName("MOLD/PART answers the first characters of the whole mold, in each base")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            2  | #{FFFFFFFFFFFFFFFFFF}                    | 2#{11111
            16 | #{FFFFFFFFFFFFFFFFFF}                    | #{FFFFFF
            64 | #{FFFFFFFFFFFFFFFFFF}                    | 64#{////
            2  | to-binary make image! 2 * 1920x1080      | 2#{11111
            16 | to-binary make image! 2 * 1920x1080      | #{FFFFFF
            64 | to-binary make image! 2 * 1920x1080      | 64#{////
            """)
    void answersTheFirstCharacters(int base, String made, String wanted) {
        assertThat(answerTo("""
                bin: %s
                system/options/binary-base: %d
                molded: mold/part bin 8
                either molded == "%s" ['same] [molded]""".formatted(made, base, wanted)))
                .isEqualTo("same");
    }
}
