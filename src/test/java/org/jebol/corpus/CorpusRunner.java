package org.jebol.corpus;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.eval.OutputPort;
import org.jebol.domain.value.ErrorValue;

import java.util.List;
import java.util.Optional;

/**
 * Runs one corpus entry and says what it did.
 *
 * <p>Shared so that the strict test and the coverage report agree on what
 * passing means. Two implementations of that would drift, and the one that
 * drifted would be the flattering one.
 */
final class CorpusRunner {

    private CorpusRunner() {
    }

    /** What running an entry produced, in the terms the corpus asserts on. */
    record Result(
            boolean completed,
            String displayed,
            String printed,
            Optional<String> errorCategory,
            Optional<String> errorId) {

        /** Whether this matches everything the entry asserted. */
        boolean matches(CorpusEntry entry) {
            return mismatch(entry).isEmpty();
        }

        /** What did not match, or empty if everything did. */
        Optional<String> mismatch(CorpusEntry entry) {
            if (entry.expectedError().isPresent()) {
                List<String> wanted = List.of(entry.expectedError().orElseThrow().split("\\s+"));
                if (completed) {
                    return Optional.of("wanted error " + entry.expectedError().orElseThrow()
                            + ", got [" + displayed + "]");
                }
                if (!errorCategory.orElse("").equals(wanted.get(0))
                        || !errorId.orElse("").equals(wanted.get(1))) {
                    return Optional.of("wanted error " + entry.expectedError().orElseThrow()
                            + ", got error " + errorCategory.orElse("?")
                            + " " + errorId.orElse("?"));
                }
                return Optional.empty();
            }

            if (!completed) {
                return Optional.of("failed with error " + errorCategory.orElse("?")
                        + " " + errorId.orElse("?"));
            }
            if (entry.expectedResult().isPresent()
                    && !displayed.equals(entry.expectedResult().orElseThrow())) {
                return Optional.of("wanted [" + entry.expectedResult().orElseThrow()
                        + "] got [" + displayed + "]");
            }
            if (entry.expectedPrints().isPresent()
                    && !printed.strip().equals(entry.expectedPrints().orElseThrow().strip())) {
                return Optional.of("wanted printed [" + entry.expectedPrints().orElseThrow()
                        + "] got [" + printed.strip() + "]");
            }
            return Optional.empty();
        }
    }

    /** Collects everything the script printed, so `prints` can be asserted. */
    private static final class Captured implements OutputPort {

        private final StringBuilder written = new StringBuilder();

        @Override
        public void write(String text) {
            written.append(text);
        }

        @Override
        public void writeLine(String text) {
            written.append(text).append('\n');
        }

        String text() {
            return written.toString();
        }
    }

    private static String categoryOf(ScriptOutcome outcome) {
        return outcome.value() instanceof ErrorValue error
                ? error.category().spelling()
                : "script";
    }

    static Result run(CorpusEntry entry) {
        Captured captured = new Captured();
        Interpreter interpreter = Interpreter.writingTo(captured);
        interpreter.defineFreshWordsIn(entry.code());

        ScriptOutcome outcome = interpreter.run(entry.code());

        return new Result(
                outcome.succeeded(),
                interpreter.display(outcome),
                captured.text(),
                outcome.succeeded()
                        ? Optional.empty()
                        : Optional.of(categoryOf(outcome)),
                outcome.errorId());
    }
}
