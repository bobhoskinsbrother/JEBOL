package org.jebol.domain.eval.natives;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatsProfileFromTheSourceTest {

    private static final String THE_THIRTEEN_FIELDS = """
            [timer evals eval-natives eval-functions series-made series-freed series-expanded \
            series-bytes series-recycled made-blocks made-objects recycles collisions]""";

    private final Interpreter interpreter = AnInterpreter.create();

    private String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the template has the thirteen fields Rebol declares, in its order")
    void hasTheDeclaredFields() {
        assertThat(answerTo("words-of system/standard/stats")).isEqualTo(THE_THIRTEEN_FIELDS);
    }

    @Test
    @DisplayName("before anything profiles, every field of the template is none, as a real 3.22.5 answers")
    void startsAsNone() {
        assertThat(answerTo("""
                fields: system/standard/stats
                collect [foreach word words-of fields [unless none? get in fields word [keep word]]]"""))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("STATS/PROFILE answers the template itself, filled in place")
    void answersTheTemplateItself() {
        assertThat(answerTo("same? stats/profile system/standard/stats")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("every field is a number once profiled: the timer a time, the rest integers")
    void fillsEveryFieldWithANumber() {
        assertThat(answerTo("""
                fields: stats/profile
                collect [foreach word words-of fields [keep type? get in fields word]]"""))
                .isEqualTo("[#(time!) #(integer!) #(integer!) #(integer!) #(integer!) #(integer!) "
                        + "#(integer!) #(integer!) #(integer!) #(integer!) #(integer!) #(integer!) #(integer!)]");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"series-made", "series-freed", "series-expanded", "series-recycled",
            "made-blocks", "made-objects", "recycles", "collisions"})
    @DisplayName("a count JEBOL does not keep is 0, never a guess")
    void aCountNotKeptIsZero(String field) {
        assertThat(answerTo("select stats/profile '" + field)).isEqualTo("0");
    }

    @Test
    @DisplayName("the counts JEBOL keeps are its own: evaluations and natives are above zero, and a function call counts")
    void theKeptCountsAreReal() {
        assertThat(answerTo("""
                before: copy stats/profile
                f: func [] [1]
                f f f
                after: stats/profile
                reduce [
                    after/evals > before/evals
                    after/eval-natives > before/eval-natives
                    after/eval-functions - before/eval-functions >= 3
                    after/series-bytes > 0
                    after/timer >= before/timer
                ]"""))
                .isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("a second STATS/PROFILE overwrites the first in the same object")
    void aSecondProfileOverwritesTheFirst() {
        assertThat(answerTo("""
                first: stats/profile
                held: first/evals
                loop 100 [1 + 1]
                second: stats/profile
                reduce [same? first second second/evals > held]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("DELTA-PROFILE in a fresh interpreter answers numbers, though the template started as none")
    void deltaProfileWorksFromAFreshInterpreter() {
        assertThat(answerTo("""
                measured: delta-profile [loop 10 [1 + 1]]
                reduce [measured/evals > 0 measured/made-blocks]"""))
                .isEqualTo("[#(true) 0]");
    }
}
