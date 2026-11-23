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
    @DisplayName("and a command not read by a shell is left as it was given")
    void leavesACommandWithoutAShellAlone() {
        assertThat(ShellCommands.asShellCommand(List.of("ls", "-l"), false, true))
                .containsExactly("ls", "-l");
    }
}
