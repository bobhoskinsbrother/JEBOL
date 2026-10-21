package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.jebol.application.JavaMachine;
import org.jebol.domain.eval.natives.LimitUsageNative;
import org.jebol.domain.host.JvmDigests;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LimitUsageFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("what a script can see of it")
    class TheScriptSide {

        @Test
        @DisplayName("nothing: the boot takes the word away")
        void removesTheWordBeforeAScriptRuns() {
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

        private static Value call(Evaluator evaluator, String field, Value limit) {
            return new LimitUsageNative().behaviour()
                    .call(List.of(WordValue.of(field), limit),
                            evaluator, null, Set.of());
        }

        private static Evaluator makeAnEvaluator() {
            Boot boot = Boot.makeStandard(
                    new JavaMachine(), new JvmDigests(), BootSources.none(), Set.of());
            return new Evaluator(boot.start().lib(), line -> { });
        }

        @Test
        @DisplayName("it answers nothing")
        void answersUnset() {
            assertThat(call(makeAnEvaluator(), "eval", IntegerValue.of(1000)))
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("records the first limit for a field")
        void recordsTheFirstCall() {
            Evaluator evaluator = makeAnEvaluator();
            call(evaluator, "eval", IntegerValue.of(4321));
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).hasValue(4321L);
        }

        @Test
        @DisplayName("and not the second, because the C writes only into a zero")
        void ignoresTheSecondCall() {
            Evaluator evaluator = makeAnEvaluator();
            call(evaluator, "eval", IntegerValue.of(100));
            call(evaluator, "eval", IntegerValue.of(999999));
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).hasValue(100L);
        }

        @Test
        @DisplayName("the two fields are recorded apart")
        void keepsTheTwoFieldsSeparate() {
            Evaluator evaluator = makeAnEvaluator();
            call(evaluator, "memory", IntegerValue.of(65536));
            assertThat(evaluator.limitRecorded(UsageLimit.MEMORY_BYTES)).hasValue(65536L);
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).isEmpty();
        }

        @Test
        @DisplayName("a field it does not know records nothing and still answers unset")
        void ignoresAnUnknownField() {
            Evaluator evaluator = makeAnEvaluator();
            assertThat(call(evaluator, "nonsense", IntegerValue.of(5)))
                    .isInstanceOf(UnsetValue.class);
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).isEmpty();
            assertThat(evaluator.limitRecorded(UsageLimit.MEMORY_BYTES)).isEmpty();
        }

        @Test
        @DisplayName("nothing is recorded until something asks")
        void recordsNothingToStartWith() {
            Evaluator evaluator = makeAnEvaluator();
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).isEmpty();
            assertThat(evaluator.limitRecorded(UsageLimit.MEMORY_BYTES)).isEmpty();
        }

        @Test
        @DisplayName("and a decimal has its fraction cut off, as Int64 does")
        void cutsADecimal() {
            Evaluator evaluator = makeAnEvaluator();
            call(evaluator, "eval", DecimalValue.of(1000.9));
            assertThat(evaluator.limitRecorded(UsageLimit.EVALUATIONS)).hasValue(1000L);
        }
    }
}
