package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceFromTheSourceTest {

    private static String outputOf(String source) {
        StringBuilder captured = new StringBuilder();
        Interpreter interpreter = Interpreter.writingTo(captured::append);
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
        return captured.toString();
    }

    @Nested
    @DisplayName("what each hook reports")
    class TheHooks {

        @Test
        @DisplayName("a value is reported with the position it sits at")
        void aValueAndItsPosition() {
            String printed = outputOf("trace on 1 2 trace off");
            assertThat(printed).contains("3 : 1").contains("4 : 2");
        }

        @Test
        @DisplayName("and a word is followed by what it holds")
        void aWordAndItsValue() {
            assertThat(outputOf("a: 7 trace on a trace off")).contains(": 7");
        }

        @Test
        @DisplayName("a word holding a function names its datatype instead")
        void aWordHoldingAFunction() {
            assertThat(outputOf("f: func [] [1] trace on f trace off"))
                    .contains("function!");
        }

        @Test
        @DisplayName("a function value itself is not reported, because the call is")
        void aFunctionValueIsSkipped() {
            String printed = outputOf("trace on 1 + 2 trace off");
            assertThat(printed).contains("3 : 1").contains("5 : 2");
            assertThat(printed).doesNotContain(" : +");
        }

        @Test
        @DisplayName("a call is reported as it is made, and again as it answers")
        void aCallAndItsAnswer() {
            String printed = outputOf("trace on 1 + 2 trace off");
            assertThat(printed).contains("--> ");
            assertThat(printed).contains("<-- ");
        }
    }

    @Nested
    @DisplayName("the level is a limit on the nesting")
    class TheLevel {

        @Test
        @DisplayName("one shows the block it was called in and nothing inside")
        void levelOneStaysAtTheTop() {
            String printed = outputOf("f: func [x] [x * 2] trace 1 f 5 trace off");
            assertThat(printed).contains("--> f");
            assertThat(printed).doesNotContain(": x");
        }

        @Test
        @DisplayName("two shows one level of nesting, indented three spaces")
        void levelTwoReachesInside() {
            String printed = outputOf("f: func [x] [x * 2] trace 2 f 5 trace off");
            assertThat(printed).contains("   1 : x : 5");
        }

        @Test
        @DisplayName("ON is a level of a hundred thousand rather than a flag")
        void onIsALevel() {
            String deep = outputOf("f: func [x] [x * 2] trace on f 5 trace off");
            String counted = outputOf("f: func [x] [x * 2] trace 100000 f 5 trace off");
            assertThat(deep).isEqualTo(counted);
        }

        @Test
        @DisplayName("and OFF prints nothing afterwards")
        void offStops() {
            assertThat(outputOf("trace off 1 + 2")).isEmpty();
        }

        @Test
        @DisplayName("TRACE answers nothing at all, whichever way it is called")
        void itAnswersUnset() {
            Interpreter interpreter = Interpreter.create();
            String source = "reduce [unset? trace off unset? trace/back false]";
            interpreter.defineFreshWordsIn(source);
            assertThat(interpreter.display(interpreter.run(source)))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("/FUNCTION traces the calls alone")
    class FunctionsOnly {

        @Test
        @DisplayName("silencing the line hook")
        void theLinesAreSilenced() {
            String printed = outputOf(
                    "f: func [x] [x * 2] trace/function on f 5 trace off");
            assertThat(printed).doesNotContain(" : ");
        }

        @Test
        @DisplayName("and showing each call's arguments, which the plain form does not")
        void theArgumentsAreShown() {
            assertThat(outputOf("f: func [x] [x * 2] trace/function on f 5 trace off"))
                    .contains("--> f 5");
            assertThat(outputOf("f: func [x] [x * 2] trace on f 5 trace off"))
                    .contains("--> f")
                    .doesNotContain("--> f 5");
        }
    }

    @Nested
    @DisplayName("/BACK keeps the lines instead of printing them")
    class TheBacktrace {

        @Test
        @DisplayName("one call turns on both the tracing and the keeping")
        void oneCallDoesBoth() {
            assertThat(outputOf("trace/back on 1 + 2")).isEmpty();
        }

        @Test
        @DisplayName("and asking for them prints what was kept")
        void askingPrintsThem() {
            assertThat(outputOf("trace/back on 1 + 2 trace/back 10"))
                    .contains(" : 1");
        }

        @Test
        @DisplayName("asking also turns tracing off, which is one line of the C")
        void askingTurnsTracingOff() {
            assertThat(outputOf("trace/back on 1 + 2 trace/back 10 9 + 9"))
                    .doesNotContain(" : 9");
        }

        @Test
        @DisplayName("and a plain TRACE afterwards turns the keeping off")
        void aPlainTraceTurnsKeepingOff() {
            assertThat(outputOf("trace/back on trace on 1 + 2")).contains(" : 1");
        }
    }
}
