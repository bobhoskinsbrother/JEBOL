package org.jebol.domain.parse;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuotedRulesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a lit-word in a rule matches a plain word")
    void matchesAWordWithALitWord() {
        assertThat(answerTo("parse [a] ['a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a lit-word folds case unless the parse minds it")
    void matchesALitWordByTheParseCase() {
        assertThat(answerTo("parse [a] ['A]")).isEqualTo("#(true)");
        assertThat(answerTo("parse/case [a] ['A]")).isEqualTo("#(false)");
        assertThat(answerTo("parse/case [a] ['a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a lit-path in a rule matches a path")
    void matchesAPathWithALitPath() {
        assertThat(answerTo("parse [p/a] ['p/a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path is compared item by item, and each item folds case")
    void comparesAPathPiecewise() {
        assertThat(answerTo("parse [p/a] ['p/A]")).isEqualTo("#(true)");
        assertThat(answerTo("parse/case [p/a] ['p/A]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a path of a different length does not match")
    void requiresTheLengthsToAgree() {
        assertThat(answerTo("parse [p/a] ['p/a/b]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("QUOTE takes the next rule item as a value")
    void quoteTakesAValue() {
        assertThat(answerTo("parse [a] [quote a]")).isEqualTo("#(true)");
        assertThat(answerTo("parse [p/a] [quote p/a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("QUOTE follows the parse's case as well")
    void quoteFollowsTheParseCase() {
        assertThat(answerTo("parse [a] [quote A]")).isEqualTo("#(true)");
        assertThat(answerTo("parse/case [a] [quote a]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("QUOTE of a paren matches what the paren answered")
    void quoteEvaluatesAParen() {
        assertThat(answerTo("parse [2] [quote (1 + 1)]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("QUOTE with nothing after it is refused")
    void matchesAnEmptyQuote() {
        assertThat(answerTo(
                "e: try [parse [a] [quote]] either error? e [e/id] ['no-error]"))
                .isEqualTo("parse-end");
    }

    @Test
    @DisplayName("QUOTE spans two rule items and the rule carries on")
    void continuesTheRuleAfterQuote() {
        assertThat(answerTo("parse [a b] [quote a 'b]")).isEqualTo("#(true)");
    }
}
