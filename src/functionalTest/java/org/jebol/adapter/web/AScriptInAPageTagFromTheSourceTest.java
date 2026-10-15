package org.jebol.adapter.web;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AScriptInAPageTagFromTheSourceTest {

    private static final String THE_TAG_IT_IS_WRITTEN_IN =
            "<script type=\"text/rebol\" id=\"rebol\">";

    @Test
    @DisplayName("the page carries the script in a script tag, and it runs and prints its answers")
    void thePageScript() throws IOException {
        StringBuilder written = new StringBuilder();
        Interpreter interpreter = Interpreter.writingTo(written::append);

        ScriptOutcome outcome = interpreter.run(theScriptOnThePage());

        assertThat(outcome.succeeded()).isTrue();
        assertThat(written.toString().lines().toList())
                .containsExactly("3", "true", "true", "true", "true", "true", "true", "waited");
    }

    private static String theScriptOnThePage() throws IOException {
        try (InputStream page = AScriptInAPageTagFromTheSourceTest.class
                .getResourceAsStream("/web/rebol-on-a-page.html")) {
            String html = new String(page.readAllBytes(), StandardCharsets.UTF_8);
            int opens = html.indexOf(THE_TAG_IT_IS_WRITTEN_IN);
            int startsWriting = opens + THE_TAG_IT_IS_WRITTEN_IN.length();
            return html.substring(startsWriting, html.indexOf("</script>", startsWriting));
        }
    }
}
