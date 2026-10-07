package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ARefusedArgumentIsLocatedAtTheCallAlreadyRunningTest {

    private String nearAndWhereOfTheErrorFrom(String source) {
        return Molder.moldFlat(Interpreter.create().run(
                source + " reduce [first e/near first e/where]").value());
    }

    @ParameterizedTest(name = "{0} is located at {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            e: try [mold/part "abc" 2.5]                                | [try try]
            e: try [parse "a" "a"]                                      | [try try]
            e: try [append/dup [] 1 "x"]                                | [try try]
            f: func [/a b [integer!]] [b] e: try [f/a "x"]              | [try try]
            k: func [b [integer!] /a c [integer!]] [b] e: try [k/a 1 "x"] | [try try]
            g: does [mold/part "a" 2.5] e: try [g]                      | [g g]
            h: does [parse "a" "a"] e: try [h]                          | [h h]
            """)
    void locatesTheRefusalAtTheCallAlreadyRunning(String source, String location) {
        assertThat(nearAndWhereOfTheErrorFrom(source)).isEqualTo(location);
    }

    @ParameterizedTest(name = "{0} answers {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            part: false append/:part [1] 2 none                 | [1 2]
            part: false mold/:part "abc" none                   | {"abc"}
            f: func [/a b [integer!]] [b] a: false f/:a "x"     | _
            repend [1] [1 + 1]                                  | [1 2]
            remold [1 + 1]                                      | "[2]"
            """)
    void anArgumentOfASwitchedOffRefinementIsNotChecked(String source, String answer) {
        assertThat(Molder.moldFlat(Interpreter.create().run(source).value())).isEqualTo(answer);
    }

    @ParameterizedTest(name = "{0} is refused as {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            part: true e: try [append/:part [1] 2 none]         | [expect-arg append range #(none!)]
            part: true e: try [mold/:part "abc" none]           | [expect-arg mold limit #(none!)]
            """)
    void anArgumentOfASwitchedOnRefinementIsChecked(String source, String arguments) {
        assertThat(Molder.moldFlat(Interpreter.create().run(
                source + " reduce [e/id e/arg1 e/arg2 e/arg3]").value())).isEqualTo(arguments);
    }
}
