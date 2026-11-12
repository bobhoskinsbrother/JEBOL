package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AFunctionBodyIsItsWholeSeriesFromTheSourceTest {

    private static final String ASKING = """
            set/any (quote e) try [%s]
            print mold/flat either error? :e [reduce [e/id e/arg1 e/arg2]] [reduce [(quote value) :e]]
            """;

    private static String answerTo(String source) {
        StringBuilder printed = new StringBuilder();
        OutputPort output = printed::append;
        AnInterpreter.createWithOutput(output).run(ASKING.formatted(source));
        return printed.toString().strip();
    }

    @ParameterizedTest(name = "{0} gives {1}")
    @DisplayName("make function! and make closure! keep the body's series from its head")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            f: func [] next [5 <> 0] f                             | [value #(true)]
            f: func [] next [5 6] f                                | [value 6]
            f: func [] tail [5 6] f                                | [value 6]
            f: func [] next [5 6] body-of :f                       | [value [5 6]]
            f: func [] tail [5 6] body-of :f                       | [value [5 6]]
            f: func [] next [5 6] mold :f                          | [value "make function! [[][5 6]]"]
            f: make function! reduce [[] next [5 6]] body-of :f    | [value [5 6]]
            f: make function! reduce [[] tail [5 6]] f             | [value 6]
            f: make closure! reduce [[] next [5 6]] body-of :f     | [value [5 6]]
            f: make closure! reduce [[] tail [5 6]] f              | [value 6]
            f: does next [5 6] body-of :f                          | [value [5 6]]
            f: does next [5 <> 0] f                                | [value #(true)]
            f: make function! reduce [[x] next [x 5]] f 3          | [value 5]
            o: make op! make function! reduce [[a b] next [5 a]] 1 o 2 | [value 1]
            f: make function! reduce [next [a b] [a]] f 1 2        | [value 1]
            f: make function! reduce [next [a b] [b]] spec-of :f   | [value [a b]]
            """)
    void runsAndShowsTheBodyFromItsHead(String source, String expected) {
        assertThat(answerTo(source)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} gives {1}")
    @DisplayName("make function! takes exactly two items, read from the definition's head")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            make function! [[] [1] 3]                   | [bad-make-arg #(function!) [[] [1] 3]]
            make function! next [9 [] [1]]              | [bad-make-arg #(function!) [[] [1]]]
            f: make function! next [[a] [a] [] [7]] f 5 | [bad-make-arg #(function!) [[a] [] [7]]]
            make function! [[]]                         | [bad-make-arg #(function!) [[]]]
            """)
    void refusesAnyOtherDefinition(String source, String expected) {
        assertThat(answerTo(source)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} gives {1}")
    @DisplayName("function, closure and has copy the body from where it stands")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            f: has [] next [5 6] body-of :f        | [value [6]]
            f: function [] next [5 6] body-of :f   | [value [6]]
            f: closure [] next [5 6] body-of :f    | [value [6]]
            f: func [x] next [5 x] f 3             | [value 3]
            f: function [] next [5 <> 0] f         | [no-op-arg <> _]
            """)
    void copiesTheBodyFromItsPosition(String source, String expected) {
        assertThat(answerTo(source)).isEqualTo(expected);
    }
}
