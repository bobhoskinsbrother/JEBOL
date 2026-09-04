package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIMIT-USAGE, and the two lines elsewhere that decide what it is worth.
 *
 * <p>The native itself is four lines: record a number for `eval` or for
 * `memory`, once each, and answer unset. `if (Eval_Limit == 0) Eval_Limit =
 * Int64(...)` is the whole rule, so a second call for the same field writes
 * nothing, and a field that is neither falls out of the bottom untouched.
 *
 * <p>Two things elsewhere make it do less than it looks.
 *
 * <p>Nothing enforces the number. It is read once, in {@code Do_Signals}, and
 * handed to {@code Check_Security(SYM_EVAL, POL_EXEC, 0)}; every policy in
 * {@code boot/sysobj.reb} starts at {@code 0.0.0}, which is ALLOW, and an allowed
 * policy does nothing at all.
 *
 * <p>And no script can call it. {@code mezz-secure.reb} ends the boot with
 * {@code unset in lib 'limit-usage}, so the word is gone by the time a script
 * runs. That is Rebol's own file doing it, borrowed here verbatim, and it leaves
 * SECURE broken in Rebol as well: SECURE's body is bound to the slot that was
 * unset, so `secure [eval 100]` raises no-value there exactly as it does here.
 *
 * <p>So the native exists for the surface and for the caller Rebol intended, and
 * its whole observable behaviour from a script is that the word is not there.
 * Writing an enforcement would mean JEBOL stopping a script Rebol would let run;
 * writing a way to reach it would mean adding a word Rebol takes away.
 *
 * <p>Specified in {@code spec/natives.allium} under "Recording a limit that
 * nothing yet enforces".
 */
class LimitUsageFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("what a script can see of it")
    class TheScriptSide {

        @Test
        @DisplayName("nothing: the boot takes the word away")
        void theWordIsGoneByTheTimeAScriptRuns() {
            assertThat(answerTo("value? 'limit-usage")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and its only caller is left broken, in Rebol too")
        void secureCannotReachItEither() {
            assertThat(answerTo(
                    "e: try [secure [eval 100]] either error? e [e/id] ['no-error]"))
                    .isEqualTo("no-value");
        }

        @Test
        @DisplayName("while PROTECT-SYSTEM removes itself the same way")
        void protectSystemGoesToo() {
            assertThat(answerTo("value? 'protect-system")).isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("what the native does when it is reached")
    class TheNativeItself {

        /** Reached through the registry, because the library word is gone. */
        private static Value called(Evaluator evaluator, String field, Value limit) {
            return Natives.standard(java.util.Set.of()).behaviours()
                    .get("limit-usage")
                    .call(java.util.List.of(WordValue.of(field), limit),
                            evaluator, null, java.util.Set.of());
        }

        private static Evaluator anEvaluator() {
            Natives natives = Natives.standard(java.util.Set.of());
            return new Evaluator(natives.behaviours(), natives.asContext(),
                    line -> { });
        }

        @Test
        @DisplayName("it answers nothing")
        void itAnswersUnset() {
            assertThat(called(anEvaluator(), "eval", IntegerValue.of(1000)))
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("records the first limit for a field")
        void theFirstCallIsRecorded() {
            Evaluator evaluator = anEvaluator();
            called(evaluator, "eval", IntegerValue.of(4321));
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).hasValue(4321L);
        }

        @Test
        @DisplayName("and not the second, because the C writes only into a zero")
        void theSecondCallIsNot() {
            Evaluator evaluator = anEvaluator();
            called(evaluator, "eval", IntegerValue.of(100));
            called(evaluator, "eval", IntegerValue.of(999999));
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).hasValue(100L);
        }

        @Test
        @DisplayName("the two fields are recorded apart")
        void theTwoFieldsAreSeparate() {
            Evaluator evaluator = anEvaluator();
            called(evaluator, "memory", IntegerValue.of(65536));
            assertThat(evaluator.limitRecorded(UsageLimit.MEMORY_BYTES)).hasValue(65536L);
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).isEmpty();
        }

        @Test
        @DisplayName("a field it does not know records nothing and still answers unset")
        void anUnknownFieldIsIgnored() {
            Evaluator evaluator = anEvaluator();
            assertThat(called(evaluator, "nonsense", IntegerValue.of(5)))
                    .isInstanceOf(UnsetValue.class);
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).isEmpty();
            assertThat(evaluator.limitRecorded(UsageLimit.MEMORY_BYTES)).isEmpty();
        }

        @Test
        @DisplayName("nothing is recorded until something asks")
        void nothingIsRecordedToStartWith() {
            Evaluator evaluator = anEvaluator();
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).isEmpty();
            assertThat(evaluator.limitRecorded(UsageLimit.MEMORY_BYTES)).isEmpty();
        }

        @Test
        @DisplayName("and a decimal has its fraction cut off, as Int64 does")
        void aDecimalIsCut() {
            Evaluator evaluator = anEvaluator();
            called(evaluator, "eval", DecimalValue.of(1000.9));
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).hasValue(1000L);
        }
    }
}
