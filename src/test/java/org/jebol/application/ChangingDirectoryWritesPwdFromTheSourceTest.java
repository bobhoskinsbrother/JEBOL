package org.jebol.application;

import org.jebol.domain.eval.EnvironmentPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHANGE-DIR writes PWD, so GET-ENV says where the interpreter is standing.
 *
 * <p>{@code OS_Set_Current_Dir} does both in three lines, and its comment names
 * the issue it was written for:
 *
 * <pre>
 * if (chdir(path) == 0) {
 *     // directory changed... update PWD
 *     // https://github.com/Oldes/Rebol-issues/issues/2448
 *     setenv("PWD", path, 1);
 * </pre>
 *
 * <p>So {@code pwd = to-rebol-file get-env "PWD"} holds before a move and after
 * one, which is what Rebol's own port test asserts on both sides of a
 * {@code change-dir %../}. It holds against whatever the shell exported as
 * well: starting a real 3.22.5 with {@code PWD=/nowhere/at/all} still answers
 * the real directory, because the value is written at the first change and the
 * boot makes one.
 *
 * <p>Which is what makes the answer usable from inside a confined interpreter,
 * and is why this was worth fixing rather than arranging around. A PWD read
 * straight from the host names a directory such a script cannot reach -- the
 * same incoherence as a HOME outside the sandbox -- and the honest answer to
 * "where am I" is where this interpreter is standing.
 */
class ChangingDirectoryWritesPwdFromTheSourceTest {

    /** A host environment holding whatever a shell might have exported. */
    private static final class AShellThatExported implements EnvironmentPort {

        private final Map<String, String> names = new LinkedHashMap<>();

        private AShellThatExported(String name, String value) {
            names.put(name, value);
        }

        @Override
        public String valueOf(String name) {
            return names.get(name);
        }

        @Override
        public Map<String, String> all() {
            return Map.copyOf(names);
        }

        @Override
        public void nameHolds(String name, String value) {
            if (value == null) {
                names.remove(name);
            } else {
                names.put(name, value);
            }
        }
    }

    private static String answerTo(Path root, EnvironmentPort environment, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY)
                        .granting(HostService.ENVIRONMENT));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.useEnvironment(environment);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("moving writes PWD, so the two agree afterwards")
    void movingWritesPwd(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                make-dir %inner/
                change-dir %inner/
                reduce [pwd = to-rebol-file get-env "PWD"  pwd]"""))
                .isEqualTo("[#(true) %/inner/]");
    }

    @Test
    @DisplayName("and again after moving back up")
    void andAgainAfterMovingBackUp(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                make-dir %inner/
                change-dir %inner/
                change-dir %../
                reduce [pwd = to-rebol-file get-env "PWD"  pwd]"""))
                .isEqualTo("[#(true) %/]");
    }

    /**
     * The written value wins over whatever the shell exported, because the
     * shell's names a directory this interpreter may not be standing in -- and
     * under a confined filesystem, one it cannot reach at all.
     */
    @Test
    @DisplayName("and what the shell exported is overwritten, not preferred")
    void whatTheShellExportedIsOverwritten(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                make-dir %inner/
                change-dir %inner/
                get-env "PWD\"""")).isEqualTo("\"/inner/\"");
    }

    /** Every other name the shell exported is left exactly as it was. */
    @Test
    @DisplayName("but every other name is left alone")
    void everyOtherNameIsLeftAlone(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("EDITOR", "vi"), """
                make-dir %inner/
                change-dir %inner/
                get-env "EDITOR\"""")).isEqualTo("\"vi\"");
    }

    /**
     * CHANGE-DIR answers where the interpreter now is, not what it was handed.
     *
     * <p>The C rewrites its own argument before using it -- {@code
     * SET_FILE(arg, ser)}, where {@code ser} is the path made absolute and
     * given a trailing slash -- and then {@code return R_ARG1} hands back the
     * rewritten value.
     *
     * <p>Handing the argument straight back is the obvious thing to write and
     * it reads as correct, since the caller gets a file back either way. What
     * they cannot do with the wrong one is compare it against WHAT-DIR, which
     * is what Rebol's own port test does, or keep it to come back to later
     * from somewhere else.
     */
    @Test
    @DisplayName("CHANGE-DIR answers the new directory, not the path it was given")
    void changeDirAnswersTheNewDirectory(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                make-dir %inner/
                change-dir %inner""")).isEqualTo("%/inner/");
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                what-dir = change-dir %.""")).isEqualTo("#(true)");
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                make-dir %inner/
                change-dir %inner/
                reduce [change-dir %../  what-dir]""")).isEqualTo("[%/ %/]");
    }

    /**
     * And a move that failed names the same rewritten path, because the C
     * rewrites the slot before it tries the move and the trap reads the slot.
     * A relative target therefore comes back absolute, which is what tells the
     * caller which directory was meant.
     */
    @Test
    @DisplayName("and a move that failed names the absolute path it meant")
    void aMoveThatFailedNamesTheAbsolutePath(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere"), """
                make-dir %inner/
                change-dir %inner/
                e: try [change-dir %issues/2446]
                reduce [e/id  e/arg1  e/arg1 = join what-dir %issues/2446/]"""))
                .isEqualTo("[cannot-open %/inner/issues/2446/ #(true)]");
    }

    /**
     * A host that granted no environment has nowhere to write PWD, and that is
     * not a reason to refuse the move. A script that may not read the
     * environment cannot tell whether the name was written, and one that may
     * walk the filesystem is entitled to walk it either way -- refusing would
     * make the two grants a pair when the host was offered them one at a time.
     */
    @Test
    @DisplayName("and a host that granted no environment still moves")
    void aHostThatGrantedNoEnvironmentStillMoves(@TempDir Path root) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        String source = """
                make-dir %inner/
                change-dir %inner/
                pwd""";
        interpreter.defineFreshWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source))).isEqualTo("%/inner/");
    }

    /**
     * And a move that failed changes neither. A refused CHANGE-DIR leaves the
     * interpreter where it was, so PWD would be lying about it.
     */
    @Test
    @DisplayName("and a move that was refused writes nothing")
    void aMoveThatWasRefusedWritesNothing(@TempDir Path root) {
        assertThat(answerTo(root, new AShellThatExported("PWD", "/nowhere/at/all"), """
                try [change-dir %no-such-dir/]
                reduce [pwd  get-env "PWD"]"""))
                .isEqualTo("[%/ \"/nowhere/at/all\"]");
    }
}
