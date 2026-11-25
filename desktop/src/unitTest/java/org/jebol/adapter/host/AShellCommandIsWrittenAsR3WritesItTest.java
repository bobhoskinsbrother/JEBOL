package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AShellCommandIsWrittenAsR3WritesItTest {

    @Test
    @DisplayName("on Windows the command goes to cmd.exe /C inside one pair of quotes, its own quotes untouched")
    void wrapsTheCommandInQuotesForCmd() {
        List<String> written = ShellCommands.asShellCommand(
                List.of("C:\\r3\\jebol-boot.cmd --do \"print 'á🙂\""), true, true);

        assertThat(written).containsExactly(
                "cmd.exe", "/C", "\"C:\\r3\\jebol-boot.cmd --do \"print 'á🙂\"\"");
    }

    @Test
    @DisplayName("away from Windows the command goes to /bin/sh -c as it is")
    void handsTheCommandToSh() {
        assertThat(ShellCommands.asShellCommand(List.of("printf '%s' \"$HOME\""), true, false))
                .containsExactly("/bin/sh", "-c", "printf '%s' \"$HOME\"");
    }

    @Test
    @DisplayName("on Windows one string read by no shell is split as CreateProcess splits a command line, as R3 hands it over whole")
    void splitsOneStringAsWindowsDoes() {
        List<String> written = ShellCommands.asShellCommand(List.of("""
                "C:\\Users\\a b\\jebol-boot.cmd" -s C:\\units\\files\\bug-load-null.r3"""), false, true);

        assertThat(written).containsExactly(
                "C:\\Users\\a b\\jebol-boot.cmd", "-s", "C:\\units\\files\\bug-load-null.r3");
    }

    @Test
    @DisplayName("and its arguments keep a quote escaped by a backslash, and backslashes before no quote, as Windows reads them")
    void readsArgumentQuotingAsWindowsDoes() {
        List<String> written = ShellCommands.asShellCommand(List.of("""
                prog "a b" c\\\\d e\\"f "g\\\\" h"""), false, true);

        assertThat(written).containsExactly("prog", "a b", "c\\\\d", "e" + '"' + "f", "g\\", "h");
    }

    @Test
    @DisplayName("away from Windows one string read by no shell stays one program name, as R3's execvp is given it")
    void leavesOneStringWholeAwayFromWindows() {
        assertThat(ShellCommands.asShellCommand(List.of("boot.sh -s x.r3"), false, false))
                .containsExactly("boot.sh -s x.r3");
    }

    @Test
    @DisplayName("and a command not read by a shell is left as it was given")
    void leavesACommandWithoutAShellAlone() {
        assertThat(ShellCommands.asShellCommand(List.of("ls", "-l"), false, true))
                .containsExactly("ls", "-l");
    }
}
