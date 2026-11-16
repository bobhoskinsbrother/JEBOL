package org.jebol.domain.eval.compression.brotli;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrotliNativesFromTheSourceTest {

    private static String answerTo(String source) {
        return Molder.moldFlat(AnInterpreter.create().run(source).value());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            decompress/size compress "abcdef" 'br 'br 3               | #{616263}
            decompress/size compress "abcdef" 'br 'br 100             | #{616263646566}
            """)
    void answersWhatRebolAnswers(String source, String wanted) {
        assertThat(answerTo(source)).isEqualTo(wanted);
    }

    @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            decompress #{0102} 'br                                    | bad-press    | 2
            decompress #{} 'br                                        | bad-press    | 2
            """)
    void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
        assertThat(answerTo("select try [" + source + "] 'id")).isEqualTo(id);
        assertThat(answerTo("select try [" + source + "] 'arg1")).isEqualTo(firstArgument);
    }
}
