package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What is left to port: the functions a real R3 has and JEBOL has not.
 *
 * <p>The goal is to port every function R3 has. The imported test suite
 * says whether a port is right; this says what is not there at all, which
 * is a different question and one no failing assertion asks. A missing
 * function usually shows up as nothing rather than as a failure.
 *
 * <p>It exists because the failure report does. That one prints a ranked
 * queue on every run and this one did not, so the work went where the
 * queue pointed. Both are printed now, side by side.
 *
 * <p>The count is asserted rather than only reported, so leaving the list
 * alone breaks the build. Lower {@link #STILL_TO_PORT} as they land; it
 * only ever goes down.
 */
class PortingBacklogTest {

    /**
     * How many of R3's functions JEBOL has not got.
     *
     * <p>A ratchet, not a target. Lower it when a port lands; never raise
     * it. If a change makes this fail by going up, a function that used
     * to be reachable is not any more -- which has happened, and silently:
     * a borrowed Rebol file can define a name over one of ours.
     */
    private static final int STILL_TO_PORT = 134;

    /** The sets that wait on a decision rather than on the work. */
    private static final Set<String> HOST = Set.of(
            "access-os", "ask", "browse", "call", "cd", "change-dir", "close", "create",
            "delete", "delete-dir", "dir", "dir?", "dir-tree", "dirize", "echo", "flush",
            "get-env", "in-dir", "input", "launch", "list-dir", "list-env", "ls", "make-dir",
            "mkdir", "modified?", "more", "now", "open", "open?", "pwd", "query", "read-key",
            "rebol-console", "recycle", "rename", "request-color", "request-dir",
            "request-file", "request-password", "rm", "secure", "set-env", "set-scheme",
            "set-user", "size?", "stats", "su", "suffix?", "undirize", "wait", "wait-key",
            "what-dir", "do-callback", "do-commands", "load-extension", "evoke");

    private static final Set<String> CRYPTOGRAPHY = Set.of(
            "checksum", "compress", "debase", "decloak", "decompress", "dehex", "ecdh",
            "ecdsa", "enbase", "encloak", "enhex", "file-checksum", "iconv", "rc4", "rsa",
            "rsa-init", "swap-endian");

    private static final Set<String> GRAPHICS = Set.of(
            "as-blue", "as-cyan", "as-gray", "as-green", "as-purple", "as-red", "as-white",
            "as-yellow", "ansi-colorize", "blur", "color-distance", "grayscale",
            "hsv-to-rgb", "image", "image-diff", "luminosity", "map-event",
            "map-gob-offset", "premultiply", "resize", "rgb-to-hsv", "tint", "unfilter");

    @Test
    @DisplayName("what R3 has and JEBOL has not, grouped by what is blocking it")
    void theBacklogIsPrinted() {
        Set<String> theirs = functionsInTheBinary();
        Set<String> ours = functionsInJebol();
        TreeSet<String> missing = new TreeSet<>(theirs);
        missing.removeAll(ours);

        List<String> language = missing.stream()
                .filter(name -> !HOST.contains(name))
                .filter(name -> !CRYPTOGRAPHY.contains(name))
                .filter(name -> !GRAPHICS.contains(name))
                .toList();

        System.out.printf("%nPORTING BACKLOG: %d of R3's %d functions are missing.%n",
                missing.size(), theirs.size());
        report("ordinary language -- this is the work", language);
        report("the host -- waiting on where the boundary sits",
                missing.stream().filter(HOST::contains).toList());
        report("cryptography and codecs", missing.stream()
                .filter(CRYPTOGRAPHY::contains).toList());
        report("images and colour", missing.stream().filter(GRAPHICS::contains).toList());

        assertThat(missing.size())
                .as("the backlog only goes down; lower STILL_TO_PORT when a port lands, "
                        + "and if this went up a name that used to be reachable is not")
                .isLessThanOrEqualTo(STILL_TO_PORT);
    }

    private static void report(String heading, List<String> names) {
        System.out.printf("%n  %s (%d)%n", heading, names.size());
        for (int at = 0; at < names.size(); at += 6) {
            System.out.println("    " + String.join("  ",
                    names.subList(at, Math.min(at + 6, names.size()))));
        }
    }

    /** Every function name in the checked-in record of R3's library. */
    private static Set<String> functionsInTheBinary() {
        try (var source = PortingBacklogTest.class.getResourceAsStream("/r3/surface.txt")) {
            if (source == null) {
                throw new IllegalStateException("r3/surface.txt is not on the test path");
            }
            return new String(source.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.contains(" |"))
                    .map(line -> line.substring(0, line.indexOf(" |")).trim())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * Every function name a booted JEBOL has.
     *
     * <p>Asked of the interpreter rather than of the native registry,
     * because the prelude and the borrowed Rebol files define a third of
     * the library between them and none of it is in the registry.
     */
    private static Set<String> functionsInJebol() {
        Interpreter interpreter = Interpreter.create();
        String source = """
                names: copy []
                foreach w words-of system/contexts/lib [
                    set/any 'v try [get/any in system/contexts/lib w]
                    if all [not error? :v  any-function? :v] [append names w]
                ]
                mold sort names
                """;
        interpreter.defineFreshWordsIn(source);
        String listed = interpreter.display(interpreter.run(source));
        return java.util.Arrays.stream(
                        listed.replace("\"", "").replace("[", "").replace("]", "").split("\\s+"))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
