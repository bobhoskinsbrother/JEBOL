package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A complemented bitset keeps a flag rather than flipping every bit.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1. COMPLEMENT? came off the porting backlog and could not be
 * written until the set knew it was a complement.
 *
 * <p>Flipped bits give a set that answers every membership question the
 * same way, so nothing is wrong until something asks the set what it is.
 * Then MOLD prints a wall of FF and COMPLEMENT? has nothing to read.
 */
class ComplementedBitsetTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("COMPLEMENT? tells the two kinds apart")
    void theFlagCanBeRead() {
        assertThat(answerTo("complement? complement charset \"a\"")).isEqualTo("#(true)");
        assertThat(answerTo("complement? charset \"a\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a complemented set holds everything the other one does not")
    void membershipTurnsRound() {
        assertThat(answerTo("true? find complement charset \"a\" #\"b\"")).isEqualTo("#(true)");
        assertThat(answerTo("true? find complement charset \"a\" #\"a\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("the plain set is unaffected")
    void theOrdinarySetStillWorks() {
        assertThat(answerTo("true? find charset \"a\" #\"a\"")).isEqualTo("#(true)");
        assertThat(answerTo("true? find charset \"a\" #\"b\"")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("MOLD prints the bits that were named, with NOT before them")
    void theMoldedFormSaysWhatWasWritten() {
        assertThat(answerTo("true? find mold complement charset \"a\" \"not\""))
                .isEqualTo("#(true)");
        assertThat(answerTo("none? find mold charset \"a\" \"not\""))
                .as("and the plain one has no NOT in it")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the two forms mold the same bits")
    void onlyTheWordDiffers() {
        assertThat(answerTo(
                "bits: \"00000000000000000000000040\" "
                        + "true? all [find mold charset \"a\" bits "
                        + "find mold complement charset \"a\" bits]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("complementing twice gives the first set back")
    void theFlagTurnsBothWays() {
        assertThat(answerTo("complement? complement complement charset \"a\""))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("COMPLEMENT still works on a logic and a number")
    void theOtherKindsAreUnaffected() {
        assertThat(answerTo("complement true")).isEqualTo("#(false)");
        assertThat(answerTo("complement 0")).isEqualTo("-1");
    }
}
