package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CopyDeepKeepsANestedPositionFromTheSourceTest {

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
    @DisplayName("a nested series is copied whole and keeps its position")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            index? first copy/deep reduce [next [5 6]]                   | [value 2]
            head first copy/deep reduce [next [5 6]]                     | [value [5 6]]
            index? first copy/deep reduce [tail [5 6]]                   | [value 3]
            index? first copy/deep reduce [next {ab}]                    | [value 2]
            head first copy/deep reduce [next {ab}]                      | [value "ab"]
            index? first copy/deep reduce [next #{0102}]                 | [value 2]
            index? first first copy/deep reduce [reduce [next [5 6]]]    | [value 2]
            index? first copy/deep/types reduce [next [5 6]] block!      | [value 2]
            index? first copy/deep/types reduce [next {ab}] block!       | [value 2]
            index? second copy/deep reduce [1 next [5 6]]                | [value 2]
            index? first copy/deep/part reduce [next [5 6] 7] 1          | [value 2]
            index? select copy/deep reduce [1 next [5 6]] 1              | [value 2]
            o: make object! [b: next [5 6]] index? get in copy/deep o 'b | [value 2]
            index? first copy/deep reduce [next 'a/b/c]                  | [value 2]
            mold copy/deep reduce [next [5 6]]                           | [value "[[6]]"]
            b: next [5 6] same? head b head first copy/deep reduce [b]   | [value #(false)]
            index? first copy reduce [next [5 6]]                        | [value 2]
            index? copy/deep next [5 6]                                  | [value 1]
            index? first copy/deep reduce [to paren! next [5 6]]         | [value 1]
            """)
    void copiesTheWholeSeriesAtTheSameIndex(String source, String expected) {
        assertThat(answerTo(source)).isEqualTo(expected);
    }
}
