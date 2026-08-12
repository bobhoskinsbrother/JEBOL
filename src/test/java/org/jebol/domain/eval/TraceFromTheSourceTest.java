package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TRACE, read out of {@code REBNATIVE(trace)} and the three hooks around it in
 * {@code c-do.c}.
 *
 * <p>The hooks are where the C puts them, and where matters:
 * {@code Trace_Line} fires before a value is evaluated, {@code Trace_Func} as a
 * call is made, {@code Trace_Return} as one answers. So a trace shows what the
 * evaluator is about to do rather than what it did.
 *
 * <p>Three things here are not guessable from the name.
 *
 * <p>The argument is a <em>limit</em> and not a switch. {@code Trace_Level =
 * IS_TRUE(arg) ? 100000 : 0;} for a logic, and the number itself for an integer,
 * so {@code trace 1} shows the block it was called in and nothing nested inside.
 *
 * <p>A function value is never reported by the line hook -- {@code if
 * (ANY_FUNC(value)) return;} -- because the call hook reports it instead. That
 * is why tracing {@code 1 + 2} shows positions 1 and 3 and skips 2.
 *
 * <p>And /FUNCTION is the shorter output that shows <em>more</em> about each
 * call: it silences the line hook entirely and adds the argument values to the
 * call line, which is the opposite of what the name suggests.
 */
class TraceFromTheSourceTest {

    /** What a script printed, which for TRACE is the whole of its behaviour. */
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
            // `Debug_Fmt_(BOOT_STR(RS_TRACE,1), index+1, value)` -- the position
            // is one-based and it is the position in the block being walked, not
            // a count of what has been traced. The block here is the whole
            // script, so `1` sits at position 3 behind `trace` and `on`.
            String printed = outputOf("trace on 1 2 trace off");
            assertThat(printed).contains("3 : 1").contains("4 : 2");
        }

        @Test
        @DisplayName("and a word is followed by what it holds")
        void aWordAndItsValue() {
            // The word alone says nothing about what is about to happen, so the
            // C prints a second field: `" : %50r"` for a plain value.
            assertThat(outputOf("a: 7 trace on a trace off")).contains(": 7");
        }

        @Test
        @DisplayName("a word holding a function names its datatype instead")
        void aWordHoldingAFunction() {
            // `" : %s %50m"` -- the type name and then the function, because
            // molding a function body into a trace line would bury the trace.
            assertThat(outputOf("f: func [] [1] trace on f trace off"))
                    .contains("function!");
        }

        @Test
        @DisplayName("a function value itself is not reported, because the call is")
        void aFunctionValueIsSkipped() {
            // `if (ANY_FUNC(value)) return;` in Trace_Line. In `1 + 2` the
            // operator is skipped and the two numbers are not, so the positions
            // reported jump over it.
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
            // `if (depth < 0 || depth >= Trace_Level) return -1;` -- so a level
            // of one admits depth zero alone. The body of F is depth one.
            String printed = outputOf("f: func [x] [x * 2] trace 1 f 5 trace off");
            assertThat(printed).contains("--> f");
            assertThat(printed).doesNotContain(": x");
        }

        @Test
        @DisplayName("two shows one level of nesting, indented three spaces")
        void levelTwoReachesInside() {
            // `Debug_Space(3 * depth)` -- three spaces a level.
            String printed = outputOf("f: func [x] [x * 2] trace 2 f 5 trace off");
            assertThat(printed).contains("   1 : x : 5");
        }

        @Test
        @DisplayName("ON is a level of a hundred thousand rather than a flag")
        void onIsALevel() {
            // `Trace_Level = IS_TRUE(arg) ? 100000 : 0;`. Nothing in a real
            // script nests that deep, so ON reads as "everything" -- but it is
            // the same mechanism, and `trace 100000` is the same request.
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
            // `if (GET_FLAG(Trace_Flags, 1)) return; // function` is the first
            // line of Trace_Line.
            String printed = outputOf(
                    "f: func [x] [x * 2] trace/function on f 5 trace off");
            assertThat(printed).doesNotContain(" : ");
        }

        @Test
        @DisplayName("and showing each call's arguments, which the plain form does not")
        void theArgumentsAreShown() {
            // `if (GET_FLAG(Trace_Flags, 1)) Debug_Values(DS_GET(DS_ARG_BASE+1),
            // DS_ARGC, 20); else Debug_Line();` -- the shorter output carries
            // more about the call.
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
            // `if (D_REF(2)) { if (IS_LOGIC(arg)) Enable_Backtrace(IS_TRUE(arg)); }`
            // and then the level is set from the same argument, so
            // `trace/back on` is the whole request: trace everything, keep it.
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
            // `Trace_Flags = 0; Display_Backtrace(Int32(arg));` -- so a caller
            // that wants both has to ask for the level again afterwards.
            assertThat(outputOf("trace/back on 1 + 2 trace/back 10 9 + 9"))
                    .doesNotContain(" : 9");
        }

        @Test
        @DisplayName("and a plain TRACE afterwards turns the keeping off")
        void aPlainTraceTurnsKeepingOff() {
            // `else Enable_Backtrace(FALSE);` on the branch with no /BACK. So
            // the two cannot both be on: asking to trace plainly is asking for
            // the lines as they happen.
            assertThat(outputOf("trace/back on trace on 1 + 2")).contains(" : 1");
        }
    }
}
