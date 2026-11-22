package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnOutputPortEndsALineWithALineFeedTest {

    @Test
    @DisplayName("a line the interpreter writes ends with a line feed on every system; the host's adapter decides what that becomes")
    void aLineEndsWithALineFeed() {
        StringBuilder written = new StringBuilder();
        OutputPort output = written::append;
        output.writeLine("no-error");
        assertThat(written.toString()).isEqualTo("no-error\n");
    }
}
