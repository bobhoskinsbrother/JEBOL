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

    private static String readTheErrorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("KEEP with no COLLECT around it is refused")
    void keepNeedsACollect() {
        assertThat(readTheErrorIdOf("parse [1] [keep skip]")).isEqualTo("parse-no-collect");
        assertThat(readTheErrorIdOf("parse \"1\" [keep skip]")).isEqualTo("parse-no-collect");
    }

    @Test
    @DisplayName("a COLLECT whose rule keeps nothing into it is refused the same way")
    void refuseACollectThatNeverOpens() {
        assertThat(readTheErrorIdOf("parse [1] [collect integer! keep (1)]"))
                .isEqualTo("parse-no-collect");
    }

    @Test
    @DisplayName("KEEP inside a COLLECT is unaffected")
    void stillLetTheOrdinaryCaseThrough() {
        assertThat(answerTo("(parse [1 2] [collect some keep skip]) = [1 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("COLLECT INTO something that is not a series is refused")
    void requireASeriesTarget() {
        assertThat(readTheErrorIdOf("a: 1 parse \"1\" [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
    }

    @Test
    @DisplayName("COLLECT INTO a series of the wrong type is refused")
    void requireATargetThatSuitsTheInput() {
        assertThat(readTheErrorIdOf("a: #{} parse \"1\" [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
        assertThat(readTheErrorIdOf("a: \"1\" parse #{01} [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
        assertThat(readTheErrorIdOf("a: \"1\" parse [] [collect into a keep skip]"))
                .isEqualTo("parse-into-type");
    }

    @Test
    @DisplayName("a block or a paren takes whatever a parse yields")
    void alwaysAcceptABlockTarget() {
        assertThat(readTheErrorIdOf("a: copy [] parse [1] [collect into a keep skip]"))
                .isEqualTo("no-error");
        assertThat(readTheErrorIdOf("a: quote () parse #{01} [collect into a keep skip]"))
                .isEqualTo("no-error");
        assertThat(readTheErrorIdOf("a: #{} parse #{01} [collect into a keep skip]"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("COLLECT INTO a block still delivers")
    void deliveryStillHappens() {
        assertThat(answerTo("a: copy [] parse [1 2] [collect into a some keep skip] a = [1 2]"))
                .isEqualTo("#(true)");
    }
}
