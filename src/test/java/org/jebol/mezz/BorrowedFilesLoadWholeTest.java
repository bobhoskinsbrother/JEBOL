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

    /**
     * File to the word it stopped on: the work queue, one line each.
     *
     * <p>mezz-shell.reb was here, stopping on LIST-DIR. It stopped on the
     * first statement of the file and lost all twelve of its definitions,
     * which is what this test exists to make visible. LIST-DIR arrived when
     * mezz-files.reb was imported, and the line came out.
     *
     * <p>An entry here is a real gap rather than tolerated noise: each one
     * names something the borrowed code expects and JEBOL has not got. A new
     * one is a regression and a removed one is progress. This list was twelve
     * long and is two: APPEND on a map took two out, {@code make map! 111}
     * two more, NOW's refinements and {@code system/standard/file-info} one
     * each, a word selector on a block took prot-mysql the rest of the way,
     * loading mezz-tail.reb before the on-demand imports took two, and filing a
     * loaded module in {@code system/modules} took the last. mezz-osx-dialogs.reb
     * came off the list a different way: it is no longer loaded at all, because
     * what it defines is what the WINDOWS port serves. See ORDER.txt.
     *
     * <p>The words are matched as substrings of the failure, so each is the
     * shortest piece that names the gap rather than the whole message.
     *
     * <p>prot-tls.reb made the point five times and is now off the list. It
     * stopped on {@code binary} until the binary dialect was ported, then on
     * {@code system/catalog/ciphers} until the catalogues were filled, then
     * inside {@code decode-list} until a single-word read answered a value
     * rather than a block, then on a get-path in a dialect block, and under
     * all of it REPEND answered NONE because a declined refinement's argument
     * was not being taken. Not one of those five words was the whole of what
     * that file wanted, and the last of them was not a word at all.
     *
     * <p>A word here names a gap and nothing more, which is worth saying
     * because this list once hid one. view-funcs.reb stopped on {@code font}
     * for months and that read as "waiting on the view dialect"; the truth
     * was that seventeen of sysobj.reb's twenty-nine standard templates had
     * never been copied into the prelude, and a set-path cannot make a field.
     * With those declared the file ran a hundred lines further, to
     * {@code init-top-window}, and that read as "waiting on a widget
     * toolkit". It was not that either: what the file wanted was three
     * commands from {@code boot/window.reb}, a port behind them, and an event
     * port to exist. Read an entry as the first thing in the way, never as
     * the whole of what is missing.
     *
     * <p>The list is empty, and it is the first time it has been. Twelve
     * files were on it. Keeping the empty map rather than deleting the test
     * is the point of the ratchet: a file that starts stopping again fails
     * here and names itself, rather than quietly defining less than it used
     * to.
     */
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
