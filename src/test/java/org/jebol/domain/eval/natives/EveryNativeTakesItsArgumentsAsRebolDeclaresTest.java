package org.jebol.domain.eval.natives;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EveryNativeTakesItsArgumentsAsRebolDeclaresTest {

    private final Interpreter interpreter = Interpreter.create();

    private Value answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.run(source).value();
    }

    private String moldedAnswerTo(String source) {
        return Molder.moldFlat(answerTo(source));
    }

    private String theArgumentsItsParametersName(NativeValue built) {
        return built.parameters().stream()
                .filter(Parameter::consumesAnArgument)
                .map(Parameter::name)
                .sorted()
                .collect(Collectors.joining(" ", "[", "]"));
    }

    private String theArgumentsRebolDeclares(String spelling) {
        return moldedAnswerTo("""
                arguments: copy []
                foreach word words-of :%s [
                    if word = /local [break]
                    unless refinement? word [append arguments to word! word]
                ]
                sort arguments""".formatted(spelling));
    }

    private List<String> everyNativeWhoseParametersDisagree() {
        List<String> disagreeing = new ArrayList<>();
        BlockValue every = (BlockValue) answerTo("append copy system/catalog/natives system/catalog/actions");
        for (Value each : every.remaining()) {
            String spelling = ((WordValue) each).spelling();
            if (!(answerTo(":" + spelling) instanceof NativeValue built)) {
                continue;
            }
            String declared = theArgumentsRebolDeclares(spelling);
            String named = theArgumentsItsParametersName(built);
            if (!declared.equalsIgnoreCase(named)) {
                disagreeing.add(spelling + " declares " + declared + " but names " + named);
            }
        }
        return disagreeing;
    }

    @Test
    @DisplayName("an error naming an argument names it as Rebol's own declaration does")
    void everyNativeNamesItsArgumentsAsDeclared() {
        assertThat(everyNativeWhoseParametersDisagree()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("an argument is checked against the types Rebol declares, as it arrives")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            e: try [pick 1 1] reduce [e/id e/arg1 e/arg2 e/arg3]               | [expect-arg pick aggregate #(integer!)]
            e: try [foreach x 1 []] reduce [e/id e/arg1 e/arg2 e/arg3]         | [expect-arg foreach data #(integer!)]
            e: try [foreach x 1.2.3 []] reduce [e/id e/arg1 e/arg2 e/arg3]     | [expect-arg foreach data #(tuple!)]
            e: try [register 1] reduce [e/id e/arg1 e/arg2 e/arg3]             | [expect-arg register 'name #(integer!)]
            e: try [ascii? #{7F}] reduce [e/id e/arg1 e/arg2 e/arg3]           | [expect-arg ascii? value #(binary!)]
            e: try [ascii? none] reduce [e/id e/arg1 e/arg2 e/arg3]            | [expect-arg ascii? value #(none!)]
            e: try [last 1] reduce [e/id e/arg1 e/arg2 e/arg3]                 | [expect-arg last value #(integer!)]
            e: try [first 1] reduce [e/id e/arg1 e/arg2]                       | [cannot-use pick: #(integer!)]
            e: try [second none] reduce [e/id e/arg1 e/arg2]                   | [cannot-use pick: #(none!)]
            e: try [reflect () 'spec] reduce [e/id e/arg1 e/arg2]              | [cannot-use reflect: #(unset!)]
            e: try [at [1 2 3] 1x2] reduce [e/id e/arg1]                       | [invalid-arg 1x2]
            e: try [append/part [] 1 "x"] reduce [e/id e/arg1]                 | [invalid-part "x"]
            e: try [insert/part [] 1 [x]] reduce [e/id e/arg1]                 | [invalid-part [x]]
            e: try [reverse/part [1 2 3] 50%] reduce [e/id e/arg1]             | [invalid-part 50%]
            e: try [reverse/part [1 2 3] "s"] reduce [e/id e/arg1]             | [invalid-part "s"]
            """)
    void refusesAsRebolDoes(String source, String wanted) {
        assertThat(moldedAnswerTo(source)).isEqualTo(wanted);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("and the types Rebol declares are taken, where the Java once refused them")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            ascii? 127                                       | #(true)
            ascii? 128                                       | #(false)
            ascii? -1                                        | #(true)
            latin1? 255                                      | #(true)
            latin1? 256                                      | #(false)
            foreach x none []                                | _
            foreach x make object! [a: 1] [x]                | a
            atz [1 2 3] true                                 | [2 3]
            atz [1 2 3] false                                | [3]
            atz [1 2 3] 1.5                                  | [2 3]
            reverse/part [1 2 3] 1.5                         | [1 2 3]
            reverse/part [1 2 3] 2.9                         | [2 1 3]
            b: [1 2 3] reverse/part b next next b            | [2 1 3]
            checksum/part {abcdef} 'crc32 none               | 1267612143
            with system/ports/output [1]                     | 1
            with system/contexts/lib [1]                     | 1
            """)
    void takesWhatRebolTakes(String source, String wanted) {
        assertThat(moldedAnswerTo(source)).isEqualTo(wanted);
    }
}
