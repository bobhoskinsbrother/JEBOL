package org.jebol.mezz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleLoadTest {

    private static final Path SOURCE = Path.of("rebol3-source", "src", "modules");

    private static final Map<String, String> STOPS_ON = Map.ofEntries(
            Map.entry("munge3.reb", "no-service"),
            Map.entry("thru-cache.reb", "no-service"),
            Map.entry("unicode-utils.reb", "missing"),
            Map.entry("webdriver.reb", "invalid-path"));

    @Test
    @DisplayName("every module loads, or stops where this test says it does")
    void loadsTheModules() {
        assertThat(SOURCE)
                .as("Rebol's modules are not at %s, so this test would measure nothing",
                        SOURCE)
                .isDirectory();
        List<AFileTriedOnItsOwn.Attempt> attempts = AFileTriedOnItsOwn.attemptsIn(SOURCE);
        Map<String, AFileTriedOnItsOwn.Attempt> byName = attempts.stream()
                .collect(Collectors.toMap(
                        AFileTriedOnItsOwn.Attempt::file, Function.identity()));

        assertThat(attempts).as("found no module files to try").isNotEmpty();

        List<String> unexpectedStops = attempts.stream()
                .filter(attempt -> !attempt.loaded())
                .map(AFileTriedOnItsOwn.Attempt::file)
                .filter(file -> !STOPS_ON.containsKey(file))
                .toList();
        assertThat(unexpectedStops)
                .as("a new stop is a regression, and one of these stops without "
                        + "being written down:%n%s", AFileTriedOnItsOwn.report(attempts))
                .isEmpty();

        List<String> expectedButLoaded = STOPS_ON.keySet().stream()
                .filter(file -> byName.containsKey(file) && byName.get(file).loaded())
                .toList();
        assertThat(expectedButLoaded)
                .as("these now load whole, so the recorded stop is stale and the "
                        + "entry should come off the list:%n  %s",
                        String.join("\n  ", expectedButLoaded))
                .isEmpty();

        STOPS_ON.forEach((file, expected) -> {
            AFileTriedOnItsOwn.Attempt attempt = byName.get(file);
            assertThat(attempt).as("%s should still be tried", file).isNotNull();
            assertThat(attempt.outcome())
                    .as("%s should still stop on %s", file, expected)
                    .contains(expected);
        });

        System.out.println(AFileTriedOnItsOwn.report(attempts));
    }
}
