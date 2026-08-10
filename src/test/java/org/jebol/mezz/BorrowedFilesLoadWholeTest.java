package org.jebol.mezz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every vendored file in ORDER.txt runs to its end, or is known not to.
 *
 * <p>A borrowed file that raises halfway defines nothing below the line it
 * stopped on, and until now said so nowhere. That hid base-defs.reb quietly
 * generating its six reflector functions into a scope thrown away
 * immediately afterwards, which cost five suite assertions and looked for a
 * while like the file being a bad borrow.
 *
 * <p>The entries below are real gaps, not tolerated noise: each names a
 * native the borrowed code expects and JEBOL has not got. Fixing one means
 * deleting its line here, which is what happened to base-constants.reb when
 * QUIT arrived.
 */
class BorrowedFilesLoadWholeTest {

    /** File to the word it stopped on. */
    private static final Map<String, String> STOPS_ON = Map.of(
            "mezz-shell.reb", "list-dir");

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
