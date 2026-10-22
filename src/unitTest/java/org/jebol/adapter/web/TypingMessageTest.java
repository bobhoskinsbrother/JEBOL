package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypingMessageTest {

    @Test
    @DisplayName("a form taking the typing tells the page so, with the heading the hidden input is named by")
    void writesHeldWithItsHeading() {
        assertThat(new TypingMessage(true, "Your name?").write()).isEqualTo("""
                {"type":"typing","held":true,"heading":"Your name?"}""");
    }

    @Test
    @DisplayName("a form letting the typing go says so with an empty heading")
    void writesReleased() {
        assertThat(new TypingMessage(false, "").write()).isEqualTo("""
                {"type":"typing","held":false,"heading":""}""");
    }

    @Test
    @DisplayName("a heading with quotes, a backslash or a line end cannot break the message")
    void escapesTheHeading() {
        assertThat(new TypingMessage(true, """
                say "x"\\
                """).write()).isEqualTo("""
                {"type":"typing","held":true,"heading":"say \\"x\\"\\\\\\n"}""");
    }
}
