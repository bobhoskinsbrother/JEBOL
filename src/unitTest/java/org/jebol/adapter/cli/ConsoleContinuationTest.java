package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleContinuationTest {

    private ConsoleContinuation afterReading(String... lines) {
        ConsoleContinuation continuation = new ConsoleContinuation();
        for (String line : lines) {
            continuation.read(line + "\n");
        }
        return continuation;
    }

    @ParameterizedTest(name = "{0} leaves {1} open")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            [1           | [
            (1           | (
            {abc         | {
            %{abc        | {
            %%{abc}%     | {
            [(           | (
            ([           | [
            [{a}         | [
            `{a^}`       | {
            """)
    void waitsWithTheInnermostOpening(String line, char open) {
        ConsoleContinuation continuation = afterReading(line);

        assertThat(continuation.waitingForMore()).isTrue();
        assertThat(continuation.whatIsStillOpen()).isEqualTo(open);
    }

    @ParameterizedTest(name = "{0} is complete")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            1 + 1
            [1]
            (1)
            {abc}
            %{abc}%
            %%{a}%}%%
            `"["`
            `"a^"["`
            `#"["`
            1 ; [
            `%"{"`
            ]
            )
            [1]]
            `"[`
            """)
    void isComplete(String line) {
        assertThat(afterReading(line).waitingForMore()).isFalse();
    }

    @Test
    @DisplayName("a string spanning lines is followed to its close")
    void aBraceSpansLines() {
        assertThat(afterReading("{abc", "def").waitingForMore()).isTrue();
        assertThat(afterReading("{abc", "def}").waitingForMore()).isFalse();
    }

    @Test
    @DisplayName("a raw string closes only on as many percents as opened it")
    void aRawStringNeedsItsPercents() {
        assertThat(afterReading("%%{a", "}%").waitingForMore()).isTrue();
        assertThat(afterReading("%%{a", "}%%").waitingForMore()).isFalse();
    }

    @Test
    @DisplayName("1024 openings are each remembered, and the 1025th shows a dash")
    void deeperThanItRemembers() {
        assertThat(afterReading("(".repeat(1024)).whatIsStillOpen()).isEqualTo('(');
        assertThat(afterReading("[".repeat(1023) + "(").whatIsStillOpen()).isEqualTo('(');
        assertThat(afterReading("(".repeat(1025)).whatIsStillOpen()).isEqualTo('-');
    }

    @Test
    @DisplayName("taking the input forgets what was open")
    void takingTheInputForgets() {
        ConsoleContinuation continuation = afterReading("[[[");

        continuation.inputTaken();

        assertThat(continuation.waitingForMore()).isFalse();
    }
}
