package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LzmaNativesFromTheSourceTest {

    private static String answerTo(String source) {
        return Molder.moldFlat(AnInterpreter.create().run(source).value());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            compress "aaaaaaaa" 'lzma                                 | #{5D000000010030EA7C00000008000000}
            decompress/size compress "abcdef" 'lzma 'lzma 3           | #{616263}
            """)
    void answersWhatRebolAnswers(String source, String wanted) {
        assertThat(answerTo(source)).isEqualTo(wanted);
    }

    @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            decompress #{0102} 'lzma                                  | past-end     | _
            decompress #{} 'lzma                                      | past-end     | _
            decompress/size compress "abcdef" 'lzma 'lzma 100         | bad-press    | 6
            """)
    void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
        assertThat(answerTo("select try [" + source + "] 'id")).isEqualTo(id);
        assertThat(answerTo("select try [" + source + "] 'arg1")).isEqualTo(firstArgument);
    }
}
