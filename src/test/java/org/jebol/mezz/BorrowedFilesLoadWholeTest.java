package org.jebol.mezz;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BorrowedFilesLoadWholeTest {

    private static final Map<String, String> STOPS_ON = Map.of();

    @Test
    @DisplayName("no borrowed file stops partway except the ones known to")
    void everyBorrowedFileRunsToItsEnd() {
        Map<String, String> failures = Interpreter.create().borrowedLoadFailures();

        assertThat(failures.keySet())
                .as("a new partial load is a regression, and a fixed one is progress")
                .containsExactlyInAnyOrderElementsOf(STOPS_ON.keySet());
    }

    @Test
    @DisplayName("each known stop is on the word this test says it is")
    void theKnownStopsAreWhereTheyAreSaidToBe() {
        Map<String, String> failures = Interpreter.create().borrowedLoadFailures();

        STOPS_ON.forEach((file, word) -> assertThat(failures.get(file))
                .as("%s should still be stopping on %s", file, word)
                .contains(word));
    }

    @Test
    @DisplayName("base-defs.reb generates reflectors that outlive its USE scope")
    void theReflectorsSurvive() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("mold words-of make object! [a: 1]")))
                .as("WORDS-OF is written inside a USE in base-defs.reb")
                .isEqualTo("\"[a]\"");
        assertThat(interpreter.display(interpreter.run("mold body-of func [] [1]")))
                .as("BODY-OF comes from the same generator")
                .isEqualTo("\"[1]\"");
    }
}
