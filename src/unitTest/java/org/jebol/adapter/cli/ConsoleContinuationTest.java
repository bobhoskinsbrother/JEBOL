package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleContinuationTest {

    private ConsoleContinuation read(String... lines) {
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
        ConsoleContinuation continuation = read(line);

        assertThat(continuation.isWaitingForMore()).isTrue();
        assertThat(continuation.findWhatIsStillOpen()).isEqualTo(open);
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
        assertThat(read(line).isWaitingForMore()).isFalse();
    }

    @Test
    @DisplayName("a string spanning lines is followed to its close")
    void spansLinesWithABrace() {
        assertThat(read("{abc", "def").isWaitingForMore()).isTrue();
        assertThat(read("{abc", "def}").isWaitingForMore()).isFalse();
    }

    @Test
    @DisplayName("a raw string closes only on as many percents as opened it")
    void needsTheRawStringsPercents() {
        assertThat(read("%%{a", "}%").isWaitingForMore()).isTrue();
        assertThat(read("%%{a", "}%%").isWaitingForMore()).isFalse();
    }

    @Test
    @DisplayName("1024 openings are each remembered, and the 1025th shows a dash")
    void deeperThanItRemembers() {
        assertThat(read("(".repeat(1024)).findWhatIsStillOpen()).isEqualTo('(');
        assertThat(read("[".repeat(1023) + "(").findWhatIsStillOpen()).isEqualTo('(');
        assertThat(read("(".repeat(1025)).findWhatIsStillOpen()).isEqualTo('-');
    }

    @Test
    @DisplayName("taking the input forgets what was open")
    void forgetsOnceTheInputIsTaken() {
        ConsoleContinuation continuation = read("[[[");

        continuation.takeTheInput();

        assertThat(continuation.isWaitingForMore()).isFalse();
    }
}
