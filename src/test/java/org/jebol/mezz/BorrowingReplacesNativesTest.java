package org.jebol.mezz;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A borrowed REBOL definition replaces the native of the same name.
 *
 * <p>Worth a test rather than a comment, because the whole borrowing
 * strategy rests on it and it is not obvious from reading either side.
 * The natives are put into the system context first and the library is
 * evaluated into the same context afterwards, so an assignment there
 * overwrites the slot. Nothing warns; the native simply stops being
 * reachable.
 *
 * <p>Which means every JEBOL native that R3 defines in REBOL is either
 * dead code waiting to be shadowed or a deliberate divergence. There
 * are sixteen of them, listed in {@code spec/natives.allium}.
 */
class BorrowingReplacesNativesTest {

    @Test
    @DisplayName("a borrowed definition takes the word from the native")
    void theBorrowedDefinitionWins() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("type? :split")))
                .isEqualTo("#(function!)");
    }

    /**
     * The point is that a host-language function survives, not what kind it
     * is. This asked for {@code native!} from {@code :add}, which is one of
     * the sixty Rebol declares as actions -- an answer a real R3 has never
     * given, and one this test would have gone on defending. Both kinds are
     * checked now, so it says what it means.
     */
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
