package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WHILE, and INSERT when the input is a string.
 *
 * <p>Specified in {@code spec/parse.allium}, confirmed against a real R3.
 *
 * <p>INSERT had been built for the block parser alone, so a rule that
 * worked on a block silently failed on a string -- the two halves of one
 * dialect drifting apart, which is where several of these have been.
 */
class WhileAndInsertOnStringsTest {

    private static final String RULE = "r: [while [remove #\"y\" | #\"x\"]] ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("WHILE succeeds on input it never matches")
    void whileSucceedsOnNothing() {
        assertThat(answerTo(RULE + "parse copy \"\" r")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("WHILE repeats until the rule stops matching")
    void whileRepeats() {
        assertThat(answerTo(RULE + "parse v: copy \"yx\" r v")).isEqualTo("\"x\"");
        assertThat(answerTo(RULE + "parse v: copy \"yxxyyx\" r v")).isEqualTo("\"xxx\"");
    }

    @Test
    @DisplayName("WHILE is ANY under another name")
    void whileIsAny() {
        assertThat(answerTo("parse v: copy \"yxxyyx\" [any [remove #\"y\" | #\"x\"]] v"))
                .isEqualTo("\"xxx\"");
    }

    @Test
    @DisplayName("INSERT puts text into a string being parsed")
    void insertWorksOnAString() {
        assertThat(answerTo("parse w: copy \"a\" [remove skip insert \"xxx\"] w"))
                .isEqualTo("\"xxx\"");
    }

    @Test
    @DisplayName("INSERT leaves the position after what it put in")
    void insertLandsAfterItself() {
        assertThat(answerTo("parse w: copy \"a\" [insert \"z\" skip] w"))
                .as("the SKIP took the a, not the z just inserted")
                .isEqualTo("\"za\"");
    }

    @Test
    @DisplayName("the same rule in a block still works")
    void theBlockHalfIsUnaffected() {
        assertThat(answerTo("parse b: [1] [remove skip insert 9] mold b"))
                .isEqualTo("\"[9]\"");
    }

    @Test
    @DisplayName("WHILE works on a block as well as a string")
    void whileWorksOnBothSides() {
        assertThat(answerTo("parse [1 1] [while [integer!]]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"aa\" [while [\"a\"]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("IF works on a string as well as a block")
    void guardWorksOnBothSides() {
        assertThat(answerTo("parse [1] [if (true) integer!]")).isEqualTo("#(true)");
        assertThat(answerTo("parse \"a\" [if (true) \"a\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a false guard stops the rule on either side")
    void aFalseGuardStopsBoth() {
        assertThat(answerTo("parse [1] [if (false) integer!]")).isEqualTo("#(false)");
        assertThat(answerTo("parse \"a\" [if (false) \"a\"]")).isEqualTo("#(false)");
    }
}
