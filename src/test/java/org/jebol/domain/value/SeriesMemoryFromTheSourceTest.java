package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STATS counts the bytes the series buffers hold, and RECYCLE gives them back.
 *
 * <p>Rebol's STATS answers {@code PG_Mem_Usage}, the total its allocator has
 * handed out for series. JEBOL answered the JVM's heap, which is a different
 * quantity: it counts everything the interpreter and the runtime are holding
 * and it moves when a collection runs whether or not the script released
 * anything.
 *
 * <p>Rebol's own test is the one that catches it. It makes a five million
 * character string, asks whether the number rose by at least that much, drops
 * the string, recycles, and asks whether the number came back. Against a heap
 * reading the first question passed by luck and the second did not pass at all.
 *
 * <p>The number here is not Rebol's number and cannot be. A Rebol block holds
 * its values inline where a Java one holds references to values counted where
 * they live, so the two count a tree of series differently. What matches is
 * the behaviour the test asks about: a large buffer arriving is visible, and
 * letting go of it gives the bytes back.
 */
class SeriesMemoryFromTheSourceTest {

    private static final int A_LARGE_STRING = 5_000_000;

    private static long answerToNumber(Interpreter interpreter, String source) {
        return Long.parseLong(interpreter.display(interpreter.run(source)));
    }

    private static boolean answerIsTrue(Interpreter interpreter, String source) {
        return interpreter.display(interpreter.run(source)).equals("#(true)");
    }

    @Test
    @DisplayName("a string made with room for five million characters is visible in STATS")
    void alargeStringIsVisible() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0 before: 0");
        long before = answerToNumber(interpreter, "recycle before: stats");
        interpreter.run("held: make string! " + A_LARGE_STRING);
        long after = answerToNumber(interpreter, "stats");

        assertThat(after - before)
                .as("the size a MAKE is given is a hint about what is coming rather "
                        + "than a length, but it is not decoration")
                .isGreaterThanOrEqualTo(A_LARGE_STRING);
    }

    @Test
    @DisplayName("and the string is still empty, because the size was room and not length")
    void thestringIsStillEmpty() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0");
        assertThat(answerToNumber(interpreter, "length? held: make string! " + A_LARGE_STRING))
                .isZero();
    }

    @Test
    @DisplayName("letting go of it and recycling gives the bytes back")
    void lettingGoGivesTheBytesBack() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0 before: 0");
        interpreter.run("recycle before: stats");
        interpreter.run("held: make string! " + A_LARGE_STRING);
        interpreter.run("held: none recycle");

        assertThat(answerToNumber(interpreter, "stats - before"))
                .as("this is Rebol's own assertion, which allows two thousand bytes "
                        + "of drift for whatever the script itself allocated")
                .isLessThan(2000);
    }

    @Test
    @DisplayName("Rebol's own assertion, run whole")
    void rebolsOwnAssertion() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0 before: 0");
        interpreter.run("recycle before: stats held: make string! " + A_LARGE_STRING);

        assertThat(answerIsTrue(interpreter, """
                all [
                    stats >= (before + 5000000)
                    none? held: none
                    recycle
                    (stats - before) < 2000
                ]"""))
                .isTrue();
    }

    @Test
    @DisplayName("a binary made with room reserves it too")
    void abinaryReservesItsRoom() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0 before: 0");
        long before = answerToNumber(interpreter, "recycle before: stats");
        interpreter.run("held: make binary! 2000000");

        assertThat(answerToNumber(interpreter, "stats") - before)
                .isGreaterThanOrEqualTo(2_000_000);
    }

    @Test
    @DisplayName("a string that grows past its room is counted again as it grows")
    void agrowingStringIsCountedAgain() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0 before: 0");
        long before = answerToNumber(interpreter, "recycle held: copy {} before: stats");
        interpreter.run("loop 200000 [append held {x}]");

        assertThat(answerToNumber(interpreter, "stats") - before)
                .as("the buffer doubles as it fills, and each doubling has to be told")
                .isGreaterThanOrEqualTo(200_000);
    }

    @Test
    @DisplayName("RECYCLE answers how many bytes it took back")
    void recycleAnswersWhatItFreed() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("held: 0");
        interpreter.run("recycle held: make string! " + A_LARGE_STRING + " held: none");

        assertThat(answerToNumber(interpreter, "recycle"))
                .isGreaterThanOrEqualTo(A_LARGE_STRING);
    }

    @Test
    @DisplayName("STATS still answers its other questions")
    void statsStillAnswersItsOtherQuestions() {
        Interpreter interpreter = Interpreter.create();
        assertThat(interpreter.display(interpreter.run("integer? stats/evals")))
                .isEqualTo("#(true)");
        assertThat(interpreter.display(interpreter.run("time? stats/timer")))
                .isEqualTo("#(true)");
    }
}
