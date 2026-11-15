package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LzwNativesFromTheSourceTest {

    private static String answerTo(String source) {
        return Molder.moldFlat(AnInterpreter.create().run(source).value());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            compress/part "abcdef" 'lzw 3                             | #{07616263FF01}
            decompress/size compress "abcdef" 'lzw 'lzw 3             | #{616263}
            decompress/size compress "abcdef" 'lzw 'lzw 100           | #{616263646566}
            """)
    void answersWhatRebolAnswers(String source, String wanted) {
        assertThat(answerTo(source)).isEqualTo(wanted);
    }

    @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            decompress #{0102} 'lzw                                   | bad-press    | 1
            """)
    void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
        assertThat(answerTo("select try [" + source + "] 'id")).isEqualTo(id);
        assertThat(answerTo("select try [" + source + "] 'arg1")).isEqualTo(firstArgument);
    }
}
