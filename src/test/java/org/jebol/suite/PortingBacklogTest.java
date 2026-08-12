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
     *
     * <p>It was 134 of 580 while this read a dump of a running binary, and it is
     * 30 of 353 now that it reads Rebol's source. The dump was not wrong about
     * what that build held; it was wrong about what the library is. It listed
     * every top-level word of every file the build had loaded, and most of those
     * files are modules whose words no script can reach -- forty in
     * `prot-tls.reb`, forty more in `codec-swf.reb`. Counting them made the
     * backlog look four times its size and pointed the work at functions nobody
     * can call.
     *
     * <p>What remains is worth reading rather than counting: seven are Goal 1's,
     * two are graphics, and most of the rest are the module and load machinery --
     * LOAD-HEADER, LOAD-MODULE, MAKE-MODULE*, EXPORT, DO-NEEDS. A few of those
     * JEBOL does have in its `sys` context rather than its library, and this
     * asks the library, so they read as missing. That is this measure's own
     * blind spot and it belongs in the open rather than in a fudge.
     */
    private static final int STILL_TO_PORT = 24;

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
        Set<String> theirs = functionsRebolDefines();
        Set<String> ours = functionsInJebol();
        writeForTheAudit(ours);
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

    /**
     * Every function Rebol's own source defines, read from that source.
     *
     * <p>Three kinds of line in {@code c-surface.txt} name one: ACTION and
     * NATIVE for the third of the library Rebol writes in C, and LIBRARY for the
     * rest, which it writes in REBOL in {@code src/mezz/*.reb}.
     *
     * <p>This used to read a frozen dump of a running binary. A dump says what
     * one build had loaded on one machine and cannot be checked against
     * anything; the source says what the language is and explains itself. Where
     * the two disagreed the source was right every time.
     */
    private static Set<String> functionsRebolDefines() {
        try (var source = PortingBacklogTest.class.getResourceAsStream("/r3/c-surface.txt")) {
            if (source == null) {
                throw new IllegalStateException(
                        "r3/c-surface.txt is not on the test path; "
                                + "run scripts/c-surface.py");
            }
            return new String(source.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("ACTION ")
                            || line.startsWith("NATIVE ")
                            || line.startsWith("LIBRARY "))
                    .map(line -> line.substring(line.indexOf(' ') + 1))
                    .map(line -> line.substring(0, line.indexOf(" |")).trim())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * The same names, written where `scripts/c-parity.py` can read them.
     *
     * <p>The audit needs to tell three things apart: a function JEBOL has in
     * Java, one it has in REBOL, and one it has not got. The registry answers
     * the first and this answers the second, and without both a function Rebol
     * writes in C and JEBOL writes in its prelude reads as missing.
     */
    private static void writeForTheAudit(Set<String> names) {
        java.nio.file.Path into = java.nio.file.Path.of("build", "jebol-library.txt");
        try {
            java.nio.file.Files.createDirectories(into.getParent());
            java.nio.file.Files.write(into, names);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
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
