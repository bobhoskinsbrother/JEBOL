package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ENUM builds an enumeration object from names and values.
 *
 * <p>Ported from Rebol's own {@code src/mezz/mezz-func.reb}, and the
 * object it builds on from {@code sysobj.reb}. Specified in
 * {@code spec/natives.allium}.
 *
 * <p>A name with no value after it takes the one after the last, counting
 * from zero. A name with a value takes that value, and the counting starts
 * again from there.
 */
class EnumTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("names with no values count from zero")
    void theCountingStartsAtZero() {
        assertThat(answerTo("e: enum [a b c] \"letters\" e/a")).isEqualTo("0");
        assertThat(answerTo("e: enum [a b c] \"letters\" e/c")).isEqualTo("2");
    }

    @Test
    @DisplayName("a name with a value takes it, and the counting goes on from there")
    void aGivenValueMovesTheCount() {
        assertThat(answerTo("e: enum [a 5 b] \"x\" e/b")).isEqualTo("6");
    }

    @Test
    @DisplayName("NAME gives back the name of a value")
    void aValueCanBeNamed() {
        assertThat(answerTo("e: enum [a b c] \"letters\" e/name 1")).isEqualTo("b");
    }

    @Test
    @DisplayName("NAME of a value that is not there answers none")
    void anUnknownValueHasNoName() {
        assertThat(answerTo("e: enum [a b] \"x\" none? e/name 9")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ASSERT holds for a value in the enumeration")
    void aKnownValuePasses() {
        assertThat(answerTo("e: enum [a b c] \"letters\" e/assert 2")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ASSERT fails for a value that is not there")
    void anUnknownValueFails() {
        assertThat(errorIdOf("e: enum [a b] \"x\" e/assert 9"))
                .isEqualTo("invalid-value-for");
    }

    @Test
    @DisplayName("the title is kept on the object")
    void theEnumerationKnowsItsName() {
        assertThat(answerTo("e: enum [a] \"letters\" e/title*")).isEqualTo("\"letters\"");
    }

    @Test
    @DisplayName("something that is not a name is refused")
    void aWrongItemIsRefused() {
        assertThat(errorIdOf("enum [1 2] \"x\"")).isEqualTo("invalid-data");
    }

    @Test
    @DisplayName("an empty specification makes an empty enumeration")
    void theDegenerateSpecification() {
        assertThat(answerTo("e: enum [] \"nothing\" empty? values-of e")).isEqualTo("#(false)");
    }
}
