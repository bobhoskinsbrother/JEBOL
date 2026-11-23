package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AProgramThatCannotStartIsReportedAsR3ReportsItTest {

    @Test
    @DisplayName("elsewhere the reason is strerror's text, as Java passes it on")
    void readsStrerrorsText() {
        assertThat(ProgramFailures.reasonIn("""
                Cannot run program "nothing-here": error=2, No such file or directory""", false))
                .isEqualTo("No such file or directory");
    }

    @Test
    @DisplayName("on Windows it is FormatMessage's text, full stop included, which Java drops and R3 keeps")
    void keepsFormatMessagesFullStop() {
        assertThat(ProgramFailures.reasonIn("""
                Cannot run program "nothing-here": CreateProcess error=2, The system cannot find the file specified""",
                true))
                .isEqualTo("The system cannot find the file specified.");
    }

    @Test
    @DisplayName("a reason in brackets is read from inside them")
    void readsBracketedReason() {
        assertThat(ProgramFailures.reasonIn("something failed (Permission denied)", false))
                .isEqualTo("Permission denied");
    }

    @Test
    @DisplayName("a message with no reason the system gave is passed on whole")
    void passesUnknownMessageOn() {
        assertThat(ProgramFailures.reasonIn("it went wrong", true)).isEqualTo("it went wrong");
    }
}
