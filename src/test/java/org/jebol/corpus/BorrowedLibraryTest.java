package org.jebol.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What Rebol's own library still needs from JEBOL's native layer.
 *
 * <p>The other way round from counting missing functions. An inventory
 * says what is absent; this says what is <em>wrong</em>, by running
 * Rebol's own code on top of JEBOL's natives and seeing where it breaks.
 * That is a better work-list, because it is driven by what the language
 * actually asks for rather than by what a catalogue happens to list.
 *
 * <p>It found REDUCE refusing anything but a block, which Rebol's JOIN
 * does on every call, and the eight typeset predicates being absent
 * altogether -- SERIES? among them, which REJOIN branches on. Neither
 * showed up as a missing name: REDUCE was present and wrong, and the
 * typeset predicates were never in the catalogue to be counted.
 *
 * <p>Reports rather than asserts. The list is long and shrinking is the
 * work; a test that stays red for weeks stops being read.
 */
class BorrowedLibraryTest {

    @Test
    @DisplayName("what breaks when Rebol's own library runs on JEBOL's natives")
    void theBorrowedLibraryIsMeasured() {
        Map<String, Integer> byReason = new LinkedHashMap<>();
        Map<String, String> anExample = new LinkedHashMap<>();
        int checked = 0;
        int broken = 0;

        for (CorpusEntry entry : EvaluationCorpusTest.runnableEntries().toList()) {
            if (entry.expectedError().isPresent()) {
                continue;
            }
            checked++;
            String plain = outcomeOf(Interpreter.create(), entry);
            String borrowed = outcomeOf(Interpreter.borrowingFromRebol(), entry);
            if (plain.equals(borrowed)) {
                continue;
            }
            broken++;
            byReason.merge(borrowed, 1, Integer::sum);
            anExample.putIfAbsent(borrowed, entry.code());
        }

        System.out.printf("%n%d of %d corpus entries answer differently once%n"
                + "Rebol's own library is loaded over the natives:%n", broken, checked);
        byReason.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(12)
                .forEach(entry -> System.out.printf("  %4d  %s%n        e.g. %s%n",
                        entry.getValue(), entry.getKey(),
                        anExample.get(entry.getKey()).replace("\n", " ")));

        assertThat(checked).as("nothing measured means nothing ran").isPositive();
    }

    private static String outcomeOf(Interpreter interpreter, CorpusEntry entry) {
        try {
            ScriptOutcome outcome = interpreter.run(entry.code());
            return outcome.succeeded()
                    ? interpreter.display(outcome)
                    : "error " + outcome.errorId().orElse("?");
        } catch (RuntimeException refused) {
            return "host exception: " + refused.getClass().getSimpleName();
        }
    }
}
