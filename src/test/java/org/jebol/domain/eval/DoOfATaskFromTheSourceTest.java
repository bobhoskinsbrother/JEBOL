package org.jebol.domain.eval;

import org.jebol.adapter.host.TasksOnThreads;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DoOfATaskFromTheSourceTest {

    private static final String THE_BODY_SPEAKS = "the body ran";
    private static final String THE_BODY_REACHED = "the body reached the raise";
    private static final String NOTHING_AFTER_A_RAISE = "after the raise";
    private static final String THE_BODY_SEES_NOTHING = "from the parent";

    private static final Duration A_WHILE = Duration.ofSeconds(10);

    private static Interpreter writingTo(StringBuffer written) {
        Interpreter interpreter = Interpreter.writingTo(written::append);
        interpreter.useTasks(new TasksOnThreads(Bounds.standard().withoutAWallClockLimit()));
        return interpreter;
    }

    private static void untilOutputSays(StringBuffer written, String mark) {
        long givenUpAt = System.nanoTime() + A_WHILE.toNanos();
        while (!written.toString().contains(mark)) {
            if (System.nanoTime() > givenUpAt) {
                throw new AssertionError("the output never said " + mark + ": " + written);
            }
            Thread.onSpinWait();
        }
    }

    @Test
    @DisplayName("with no way given to run a body, DO of a task answers the task and starts nothing")
    void withoutAHostTheBodyStartsNothing() {
        StringBuilder written = new StringBuilder();
        Interpreter interpreter = Interpreter.writingTo(written::append);

        String answer = interpreter.display(interpreter.run(
                "task? do make task! [print \"" + THE_BODY_SPEAKS + "\"]"));

        assertThat(answer).isEqualTo("#(true)");
        assertThat(written.toString()).doesNotContain(THE_BODY_SPEAKS);
    }

    @Test
    @Timeout(20)
    @DisplayName("with the port, the body runs in its own interpreter on a thread of its own")
    void withThePortTheBodyRuns() {
        StringBuffer written = new StringBuffer();
        Interpreter interpreter = writingTo(written);

        String answer = interpreter.display(interpreter.run(
                "task? do make task! [print \"" + THE_BODY_SPEAKS + "\"]"));

        assertThat(answer).as("the parent answers before the body has produced anything")
                .isEqualTo("#(true)");
        untilOutputSays(written, THE_BODY_SPEAKS);
    }

    @Test
    @Timeout(20)
    @DisplayName("a body that raises stops there, and the statement after it never runs")
    void aRaisingBodyStopsWhereItRaises() {
        StringBuffer written = new StringBuffer();
        Interpreter interpreter = writingTo(written);

        String answer = interpreter.display(interpreter.run(
                "task? do make task! [print \"" + THE_BODY_REACHED + "\" 1 / 0 print \""
                        + NOTHING_AFTER_A_RAISE + "\"]"));

        assertThat(answer).isEqualTo("#(true)");
        untilOutputSays(written, THE_BODY_REACHED);
        assertThat(written.toString()).doesNotContain(NOTHING_AFTER_A_RAISE);
    }

    @Test
    @Timeout(20)
    @DisplayName("the body sees none of the parent's words")
    void theBodySeesNoneOfTheParentsWords() {
        StringBuffer written = new StringBuffer();
        Interpreter interpreter = writingTo(written);

        String answer = interpreter.display(interpreter.run("""
                inherited: "%s"
                task? do make task! [print "reached" print inherited]
                """.formatted(THE_BODY_SEES_NOTHING)));

        assertThat(answer).isEqualTo("#(true)");
        untilOutputSays(written, "reached");
        assertThat(written.toString()).doesNotContain(THE_BODY_SEES_NOTHING);
    }

    @Test
    @Timeout(20)
    @DisplayName("two interpreters run whole scripts on two threads at once")
    void twoInterpretersRunOnTwoThreads() throws Exception {
        String script = "total: 0 repeat i 10000 [total: total + i] total";
        String firstAnswer = onItsOwnThread(script);
        String secondAnswer = onItsOwnThread(script);

        assertThat(firstAnswer).isEqualTo("50005000");
        assertThat(secondAnswer).isEqualTo("50005000");
    }

    private String onItsOwnThread(String script) throws Exception {
        StringBuffer written = new StringBuffer();
        Interpreter interpreter = writingTo(written);
        return CompletableFuture.supplyAsync(
                () -> interpreter.display(interpreter.run(script))).get(
                A_WHILE.toSeconds(), TimeUnit.SECONDS);
    }
}
