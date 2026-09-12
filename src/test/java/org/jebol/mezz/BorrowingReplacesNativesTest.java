package org.jebol.mezz;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BorrowingReplacesNativesTest {

    @Test
    @DisplayName("a borrowed definition takes the word from the native")
    void theBorrowedDefinitionWins() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("type? :split")))
                .isEqualTo("#(function!)");
    }

    @Test
    @DisplayName("a built-in no borrowed file defines is left alone")
    void theUnclaimedBuiltInSurvives() {
        Interpreter borrowing = Interpreter.create();

        assertThat(borrowing.display(borrowing.run("type? :add")))
                .as("ADD is declared in actions.reb")
                .isEqualTo("#(action!)");
        assertThat(borrowing.display(borrowing.run("type? :reduce")))
                .as("REDUCE is declared in natives.reb")
                .isEqualTo("#(native!)");
    }
}
