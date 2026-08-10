package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Writes the suite out in the order it runs, for a real R3 to run too.
 *
 * <p>The point is to find which of Rebol's own assertions Rebol's own
 * binary fails. There are some: {@code parse-test.r3} has a group named
 * "block collect nested (known issues)" whose comment reads "following
 * tests produces empty block at tail :-/". An assertion the binary fails
 * is not a gap in JEBOL and should not sit in a list of them.
 *
 * <p>The slicing is not redone in REBOL, because {@link SuiteFile} already
 * does it and a second implementation would be a second thing to be wrong.
 * Each step goes out as hex so that nothing in it -- braces, carets,
 * quotes -- has to survive two layers of quoting on the way.
 */
class SuiteExportTest {

    static final Path EXPORT = Path.of("build", "suite-export");

    @Test
    @DisplayName("every step of every file, in order, for the binary to run")
    void theSuiteIsExported() {
        try {
            Files.createDirectories(EXPORT);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }

        int exported = 0;
        for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
            StringBuilder written = new StringBuilder("[\n");
            for (SuiteFile.Step step : file.steps()) {
                String source = step.isAssertion() ? step.assertion().source() : step.setup();
                if (source == null || source.isBlank()) {
                    continue;
                }
                written.append(step.isAssertion() ? "  assert " : "  setup  ")
                        .append("#{")
                        .append(HexFormat.of().formatHex(
                                source.getBytes(StandardCharsets.UTF_8)))
                        .append("}");
                if (step.isAssertion()) {
                    written.append(" #{").append(HexFormat.of().formatHex(
                            step.assertion().toString().getBytes(StandardCharsets.UTF_8)))
                            .append("} ")
                            .append(step.assertion().from()).append(" ")
                            .append(step.assertion().to());
                }
                written.append("\n");
                exported++;
            }
            written.append("]\n");
            write(EXPORT.resolve(file.name().replace(".r3", "") + ".steps"), written.toString());
        }

        assertThat(exported).as("nothing exported means the reader took nothing in")
                .isGreaterThan(3_000);
    }

    private static void write(Path where, String what) {
        try {
            Files.writeString(where, what, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
