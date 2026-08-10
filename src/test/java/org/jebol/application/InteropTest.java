package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Two-way interop between REBOL and the host.
 *
 * <p>Written before it exists. What a host may reach is a policy the host
 * sets, not a paragraph of documentation saying to be careful: the shape is
 * taken from GraalVM's {@code HostAccess}, because "your problem, expressed
 * in code" is a better place for it to live than "your problem, mentioned in
 * a file".
 *
 * <p>Mutability of host objects remains the caller's concern. An interpreter
 * owns its REBOL values; a Java object it holds may be changed by anyone, and
 * closing that would mean copying or freezing everything crossing the
 * boundary, which would make interop useless for what people want it for.
 */
class InteropTest {

    @Nested
    @DisplayName("values crossing into REBOL")
    class IntoRebol {

        @Test
        @DisplayName("an integer crosses as an integer!")
        void integersCross() {
            Interpreter interpreter = Interpreter.create();
            interpreter.define("supplied", 42L);

            assertThat(interpreter.run("add supplied 1").display()).isEqualTo("43");
        }

        @Test
        @DisplayName("a String crosses as a string!")
        void stringsCross() {
            Interpreter interpreter = Interpreter.create();
            interpreter.define("greeting", "hello");

            assertThat(interpreter.run("uppercase greeting").display()).isEqualTo("\"HELLO\"");
        }

        @Test
        @DisplayName("a List crosses as a block!")
        void listsCross() {
            Interpreter interpreter = Interpreter.create();
            interpreter.define("numbers", List.of(1L, 2L, 3L));

            assertThat(interpreter.run("length? numbers").display()).isEqualTo("3");
        }

        @Test
        @DisplayName("a boolean crosses as a logic!")
        void booleansCross() {
            Interpreter interpreter = Interpreter.create();
            interpreter.define("flag", true);

            assertThat(interpreter.run("if flag [\"taken\"]").display()).isEqualTo("\"taken\"");
        }

        @Test
        @DisplayName("anything else stays a host value REBOL can hold but not read")
        void otherThingsStayOpaque() {
            Interpreter interpreter = Interpreter.create();
            interpreter.define("thing", new StringBuilder("mutable"));

            assertThat(interpreter.run("type? thing").display()).isEqualTo("#(java-object!)");
        }

        @Test
        @DisplayName("a host null is not none, and says so")
        void hostNullIsNotNone() {
            Interpreter interpreter = Interpreter.create();
            interpreter.defineNull("absent", String.class);

            assertThat(interpreter.run("none? absent").display())
                    .as("a Java null is the host's absence, not REBOL's nothing")
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("values crossing back out")
    class OutOfRebol {

        @Test
        void anIntegerComesBackAsALong() {
            Interpreter interpreter = Interpreter.create();

            assertThat(interpreter.run("add 20 22").asHostValue()).isEqualTo(42L);
        }

        @Test
        void aStringComesBackAsAString() {
            Interpreter interpreter = Interpreter.create();

            assertThat(interpreter.run("uppercase \"hello\"").asHostValue()).isEqualTo("HELLO");
        }

        @Test
        void aBlockComesBackAsAList() {
            Interpreter interpreter = Interpreter.create();

            assertThat(interpreter.run("reduce [1 2 3]").asHostValue())
                    .isEqualTo(List.of(1L, 2L, 3L));
        }

        @Test
        void aLogicComesBackAsABoolean() {
            Interpreter interpreter = Interpreter.create();

            assertThat(interpreter.run("greater? 2 1").asHostValue()).isEqualTo(true);
        }

        @Test
        @DisplayName("none comes back as an empty optional, not as null")
        void noneComesBackAsAbsence() {
            Interpreter interpreter = Interpreter.create();

            assertThat(interpreter.run("if false [1]").asOptionalHostValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("calling the host")
    class CallingTheHost {

        private static Interpreter allowingCalls() {
            return Interpreter.withBounds(
                    Bounds.standard().withHostAccess(HostAccess.READING_AND_CALLING));
        }

        @Test
        @DisplayName("REBOL can call something the host handed it")
        void rebolCanCallAHostFunction() {
            Interpreter interpreter = allowingCalls();
            interpreter.defineFunction("shout", 1, arguments ->
                    arguments.get(0).toString().toUpperCase(java.util.Locale.ROOT));

            assertThat(interpreter.run("shout \"quiet\"").display()).isEqualTo("\"QUIET\"");
        }

        @Test
        @DisplayName("a host function can be given several arguments")
        void hostFunctionsTakeSeveralArguments() {
            Interpreter interpreter = allowingCalls();
            interpreter.defineFunction("combine", 2, arguments ->
                    arguments.get(0).toString() + "|" + arguments.get(1).toString());

            assertThat(interpreter.run("combine \"a\" \"b\"").display()).isEqualTo("\"a|b\"");
        }

        @Test
        @DisplayName("a host function that throws becomes a catchable error")
        void hostThrowablesBecomeErrors() {
            Interpreter interpreter = allowingCalls();
            interpreter.defineFunction("explode", 1, arguments -> {
                throw new IllegalStateException("the host gave up");
            });

            ScriptOutcome outcome = interpreter.run("explode 1");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("host-error");
        }

        @Test
        @DisplayName("and the script can catch it, because it is an ordinary error")
        void aScriptCanCatchAHostThrowable() {
            Interpreter interpreter = allowingCalls();
            interpreter.defineFunction("explode", 1, arguments -> {
                throw new IllegalStateException("the host gave up");
            });

            assertThat(interpreter.run("error? try [explode 1]").display()).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("host access is a policy, not a warning in a file")
    class AccessPolicy {

        @Test
        @DisplayName("by default a script reaches nothing of the host")
        void nothingIsReachableByDefault() {
            Interpreter interpreter = Interpreter.create();

            assertThat(interpreter.bounds().hostAccess()).isEqualTo(HostAccess.NONE_AT_ALL);
        }

        @Test
        @DisplayName("with no access, a host value handed in cannot be called")
        void callingIsRefusedWithoutAccess() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().withHostAccess(HostAccess.NONE_AT_ALL));
            interpreter.defineFunction("shout", 1, arguments -> "never");

            ScriptOutcome outcome = interpreter.run("shout \"quiet\"");

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.RAISED);
            assertThat(outcome.errorId()).contains("host-access");
        }

        @Test
        @DisplayName("reading is allowed without calling being allowed")
        void readingWithoutCalling() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().withHostAccess(HostAccess.READING));
            interpreter.define("supplied", 42L);
            interpreter.defineFunction("shout", 1, arguments -> "never");

            assertThat(interpreter.run("supplied").display()).isEqualTo("42");
            assertThat(interpreter.run("shout \"quiet\"").conclusion())
                    .isEqualTo(Conclusion.RAISED);
        }
    }

    @Nested
    @DisplayName("the hole in the isolation story, which is deliberate")
    class HostObjectMutability {

        @Test
        @DisplayName("a host object handed in can be changed by the host afterwards")
        void hostObjectsAreNotFrozen() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().withHostAccess(HostAccess.READING_AND_CALLING));
            List<String> shared = new ArrayList<>(List.of("first"));
            interpreter.define("shared", shared);
            interpreter.defineFunction("count-of", 1, arguments -> (long) shared.size());

            assertThat(interpreter.run("count-of 0").display()).isEqualTo("1");
            shared.add("second");

            assertThat(interpreter.run("count-of 0").display())
                    .as("JEBOL makes no promise about a host object; that is the caller's")
                    .isEqualTo("2");
        }
    }
}
