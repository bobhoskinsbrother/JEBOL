package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Embedding JEBOL in a host application, which is what the project is for.
 *
 * <p>Written before the API exists. What a host needs is not "run this" but
 * "run this, and stop if it takes too long or nests too deep, and tell me
 * which of those happened without making me catch a throwable".
 */
class EmbeddingTest {

    @Nested
    @DisplayName("running a script and getting a value back")
    class Running {

        @Test
        void aScriptProducesAValue() {
            ScriptOutcome outcome = Interpreter.create().run("add 1 2");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.PRODUCED_A_VALUE);
            assertThat(outcome.display()).isEqualTo("3");
        }

        @Test
        @DisplayName("a failing script reports why without throwing")
        void aFailingScriptIsAConclusion() {
            ScriptOutcome outcome = Interpreter.create().run("divide 1 0");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("zero-divide");
        }

        @Test
        @DisplayName("a syntax error is a conclusion too, not a different kind of thing")
        void aSyntaxErrorIsAConclusion() {
            ScriptOutcome outcome = Interpreter.create().run("1 + ]");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
        }

        @Test
        @DisplayName("state persists between runs on the same interpreter")
        void stateCarriesBetweenRuns() {
            Interpreter interpreter = Interpreter.create();

            interpreter.run("total: 10");

            assertThat(interpreter.run("multiply total 2").display()).isEqualTo("20");
        }

        @Test
        @DisplayName("two interpreters do not see each other")
        void interpretersAreIsolated() {
            Interpreter first = Interpreter.create();
            Interpreter second = Interpreter.create();

            first.run("shared: \"first\"");
            second.run("shared: \"second\"");

            assertThat(first.run("shared").display()).isEqualTo("\"first\"");
            assertThat(second.run("shared").display()).isEqualTo("\"second\"");
        }
    }

    @Nested
    @DisplayName("bounds, which are enforced rather than advertised")
    class Bounds {

        @Test
        @DisplayName("a script that runs too long is stopped")
        void aRunawayScriptIsStopped() {
            Interpreter interpreter = Interpreter.withBounds(
                    org.jebol.application.Bounds.standard()
                            .withWallClockLimit(Duration.ofMillis(200)));

            long before = System.nanoTime();
            ScriptOutcome outcome = interpreter.run("while [true] [1]");
            Duration took = Duration.ofNanos(System.nanoTime() - before);

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.TIMED_OUT);
            assertThat(took)
                    .as("must stop near its deadline, not run on")
                    .isLessThan(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("and the interpreter is usable afterwards")
        void theInterpreterSurvivesATimeout() {
            Interpreter interpreter = Interpreter.withBounds(
                    org.jebol.application.Bounds.standard()
                            .withWallClockLimit(Duration.ofMillis(200)));

            interpreter.run("while [true] [1]");

            assertThat(interpreter.run("add 1 1").display()).isEqualTo("2");
        }

        @Test
        @DisplayName("a script that nests too deep is stopped")
        void deepNestingIsStopped() {
            Interpreter interpreter = Interpreter.withBounds(
                    org.jebol.application.Bounds.standard().withMaximumNesting(100));

            ScriptOutcome outcome = interpreter.run(
                    "forever: func [n] [forever n] forever 1");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("too-deep");
        }

        @Test
        @DisplayName("an ordinary script is not troubled by the bounds")
        void ordinaryWorkIsUnaffected() {
            Interpreter interpreter = Interpreter.withBounds(
                    org.jebol.application.Bounds.standard()
                            .withWallClockLimit(Duration.ofSeconds(5)));

            ScriptOutcome outcome = interpreter.run(
                    "total: 0 repeat i 1000 [total: add total i] total");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.PRODUCED_A_VALUE);
            assertThat(outcome.display()).isEqualTo("500500");
        }
    }

    @Nested
    @DisplayName("cancellation, so a host can stop a script it no longer wants")
    class Cancellation {

        @Test
        @DisplayName("a host can stop a running script from another thread")
        void aHostCanCancel() throws Exception {
            Interpreter interpreter = Interpreter.create();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<ScriptOutcome> running =
                        worker.submit((Callable<ScriptOutcome>) () ->
                                interpreter.run("while [true] [1]"));

                Thread.sleep(150);
                interpreter.cancel();

                assertThat(running.get().conclusion()).isEqualTo(Conclusion.CANCELLED);
            } finally {
                worker.shutdownNow();
            }
        }

        @Test
        @DisplayName("cancelling when nothing is running is harmless")
        void cancellingIdleIsHarmless() {
            Interpreter interpreter = Interpreter.create();

            interpreter.cancel();

            assertThat(interpreter.run("add 1 1").display()).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("many interpreters at once, which is how concurrency is had")
    class ManyAtOnce {

        @Test
        @DisplayName("one interpreter per task, running in parallel")
        void interpretersRunIndependentlyInParallel() throws Exception {
            ExecutorService workers = Executors.newFixedThreadPool(8);
            try {
                List<Future<String>> answers = workers.invokeAll(
                        java.util.stream.IntStream.rangeClosed(1, 40)
                                .mapToObj(number -> (Callable<String>) () -> {
                                    Interpreter own = Interpreter.create();
                                    own.run("n: " + number);
                                    return own.run("multiply n n").display();
                                })
                                .toList());

                for (int number = 1; number <= 40; number++) {
                    assertThat(answers.get(number - 1).get())
                            .as("interpreter %d", number)
                            .isEqualTo(Long.toString((long) number * number));
                }
            } finally {
                workers.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("output belongs to the host")
    class Output {

        @Test
        void printedTextGoesWhereTheHostSaid() {
            StringBuilder captured = new StringBuilder();
            Interpreter interpreter = Interpreter.writingTo(captured::append);

            interpreter.run("print \"from the script\"");

            assertThat(captured.toString()).contains("from the script");
        }
    }
}
