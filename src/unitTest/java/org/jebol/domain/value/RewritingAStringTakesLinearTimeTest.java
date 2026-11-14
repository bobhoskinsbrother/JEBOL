package org.jebol.domain.value;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class RewritingAStringTakesLinearTimeTest {

    private static final int TWO_MILLION = 2_000_000;

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("folding the line endings of two million characters takes moments, not minutes")
    void foldsALongTextQuickly() {
        String lines = "ab\r\n".repeat(TWO_MILLION / 4);
        AnyStringValue folded = StringValue.of(lines).withOneLineFeedPerEnding();
        assertThat(folded.text()).hasSize(TWO_MILLION / 4 * 3);
        assertThat(folded.text()).startsWith("ab\nab\n");
    }

    @Test
    @DisplayName("a rewrite from a position keeps what is before it")
    void keepsTheHead() {
        AnyStringValue text = new StringValue(new StringStorage("x\r\ny\r\n"), 3);
        text.withOneLineFeedPerEnding();
        assertThat(text.storage().text()).isEqualTo("x\r\ny\n");
    }

    @Test
    @DisplayName("a rewrite that lengthens the text holds all of it")
    void holdsALongerRewrite() {
        AnyStringValue text = StringValue.of("ab");
        text.rewriteFromHere(whole -> whole.repeat(3));
        assertThat(text.text()).isEqualTo("ababab");
    }
}
