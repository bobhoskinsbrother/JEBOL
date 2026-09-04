package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

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
     * <p>It has been wrong three times and each was the same mistake: the
     * number was believed and the question behind it was not.
     *
     * <p>It said 134 of 580 while it read a dump of a running binary, which
     * listed every top-level word of every loaded file including the modules
     * whose words no script can reach -- forty in `prot-tls.reb`, forty more
     * in `codec-swf.reb`. Reading Rebol's source instead took it to 30 of 353.
     *
     * <p>Then it said 24, and the real number was three. Twenty-one of the
     * twenty-four were in `system/contexts/sys`, where Rebol puts them too,
     * and this asked the library alone. One was `limit-usage`, which Rebol
     * deletes on purpose. Two were `completion!` and `line-editor!`, which are
     * objects rather than functions and were collected as though they were.
     *
     * <p>And the input was short: `c-surface.py` read only the boot files, so
     * the 54 natives the C declares in its own comments were invisible --
     * `binary` among them, the word `prot-tls.reb` stopped on for months while
     * the parity report said nothing was missing.
     *
     * <p>All four are fixed, and the number now means what it says. What is
     * left is three natives in `n-math.c`, none of them in the 3.22.1 binary:
     * the vendored source is ahead of it.
     */
    private static final int STILL_TO_PORT = 3;

    /**
     * Names Rebol takes back out, so their absence is the port working.
     *
     * <p>{@code mezz-secure.reb:334} is {@code unset in lib 'limit-usage},
     * which JEBOL runs faithfully. It is collected because the file that
     * defines it defines it, and unset because the file that removes it
     * removes it -- both correct, and counting the gap between them as work
     * would mean porting a function in order to delete it again.
     */
    private static final Set<String> REMOVED_BY_REBOL = Set.of("limit-usage");

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
        missing.removeAll(REMOVED_BY_REBOL);

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
     * Every function name a booted JEBOL has, in both contexts a script can
     * reach one through.
     *
     * <p>Asked of the interpreter rather than of the native registry, because
     * the prelude and the borrowed Rebol files define a third of the library
     * between them and none of it is in the registry.
     *
     * <p>Both contexts, and asking only the first is what made this measure
     * claim twenty-four functions were missing when the real number was three.
     * Twenty-one of the twenty-four are in {@code system/contexts/sys} --
     * {@code make-port*}, {@code load-module}, {@code do*} and the rest of the
     * module and load machinery -- which is where Rebol puts them too. A
     * correctly-placed function was being counted as a gap.
     */
    private static Set<String> functionsInJebol() {
        Interpreter interpreter = Interpreter.create();
        String source = """
                names: copy []
                foreach w words-of system/contexts/lib [
                    set/any 'v try [get/any in system/contexts/lib w]
                    if all [not error? :v  any-function? :v] [append names w]
                ]
                foreach w words-of system/contexts/sys [
                    set/any 'v try [get/any in system/contexts/sys w]
                    if all [not error? :v  any-function? :v] [append names w]
                ]
                mold sort unique names
                """;
        interpreter.defineFreshWordsIn(source);
        String listed = interpreter.display(interpreter.run(source));
        return java.util.Arrays.stream(
                        listed.replace("\"", "").replace("[", "").replace("]", "").split("\\s+"))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
