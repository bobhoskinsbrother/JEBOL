package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollectGuardsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("KEEP with no COLLECT around it is refused")
    void keepNeedsACollect() {
        assertThat(errorIdOf("parse [1] [keep skip]")).isEqualTo("parse-no-collect");
        assertThat(errorIdOf("parse \"1\" [keep skip]")).isEqualTo("parse-no-collect");
    }

    @Test
    @DisplayName("a COLLECT whose rule keeps nothing into it is refused the same way")
    void aCollectThatNeverOpensIsRefused() {
        assertThat(errorIdOf("parse [1] [collect integer! keep (1)]"))
                .isEqualTo("parse-no-collect");
    }

    @Test
    @DisplayName("KEEP inside a COLLECT is unaffected")
    void theOrdinaryCaseStillWorks() {
        assertThat(answerTo("(parse [1 2] [collect some keep skip]) = [1 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COLLECT INTO something that is not a series is refused")
    void theTargetMustBeASeries() {
        assertThat(errorIdOf("a: 1 parse \"1\" [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
    }

    @Test
    @DisplayName("COLLECT INTO a series of the wrong kind is refused")
    void theTargetMustSuitTheInput() {
        assertThat(errorIdOf("a: #{} parse \"1\" [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
        assertThat(errorIdOf("a: \"1\" parse #{01} [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
        assertThat(errorIdOf("a: \"1\" parse [] [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
    }

    @Test
    @DisplayName("a block or a paren takes whatever a parse yields")
    void aBlockTargetAlwaysSuits() {
        assertThat(errorIdOf("a: copy [] parse [1] [collect into a keep skip]"))
                .isEqualTo("no-error");
        assertThat(errorIdOf("a: quote () parse #{01} [collect into a keep skip]"))
                .isEqualTo("no-error");
        assertThat(errorIdOf("a: #{} parse #{01} [collect into a keep skip]"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("COLLECT INTO a block still delivers")
    void deliveryStillHappens() {
        assertThat(answerTo("a: copy [] parse [1 2] [collect into a some keep skip] a = [1 2]"))
                .isEqualTo("#(true)");
    }
}
