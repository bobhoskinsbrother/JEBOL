package org.jebol.application;

import org.jebol.domain.value.IntegerValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InteropTest {

    @Nested
    @DisplayName("values crossing into REBOL")
    class IntoRebol {

        @Test
        @DisplayName("an integer crosses as an integer!")
        void integersCross() {
            Interpreter interpreter = AnInterpreter.create();
            interpreter.define("supplied", 42L);

            assertThat(interpreter.run("add supplied 1").display()).isEqualTo("43");
        }

        @Test
        @DisplayName("a String crosses as a string!")
        void stringsCross() {
            Interpreter interpreter = AnInterpreter.create();
            interpreter.define("greeting", "hello");

            assertThat(interpreter.run("uppercase greeting").display()).isEqualTo("\"HELLO\"");
        }

        @Test
        @DisplayName("a List crosses as a block!")
        void listsCross() {
            Interpreter interpreter = AnInterpreter.create();
            interpreter.define("numbers", List.of(1L, 2L, 3L));

            assertThat(interpreter.run("length? numbers").display()).isEqualTo("3");
        }

        @Test
        @DisplayName("a boolean crosses as a logic!")
        void booleansCross() {
            Interpreter interpreter = AnInterpreter.create();
            interpreter.define("flag", true);

            assertThat(interpreter.run("if flag [\"taken\"]").display()).isEqualTo("\"taken\"");
        }

        @Test
        @DisplayName("anything else stays a host value REBOL can hold but not read")
        void keepsOtherThingsOpaque() {
            Interpreter interpreter = AnInterpreter.create();
            interpreter.define("thing", new StringBuilder("mutable"));

            assertThat(interpreter.run("type? thing").display()).isEqualTo("#(java-object!)");
        }

        @Test
        @DisplayName("a host null is not none, and says so")
        void keepsHostNullFromBeingNone() {
            Interpreter interpreter = AnInterpreter.create();
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
        void returnsAnIntegerAsALong() {
            Interpreter interpreter = AnInterpreter.create();

            assertThat(interpreter.run("add 20 22").asHostValue()).isEqualTo(42L);
        }

        @Test
        void returnsAStringAsAString() {
            Interpreter interpreter = AnInterpreter.create();

            assertThat(interpreter.run("uppercase \"hello\"").asHostValue()).isEqualTo("HELLO");
        }

        @Test
        void returnsABlockAsAList() {
            Interpreter interpreter = AnInterpreter.create();

            assertThat(interpreter.run("reduce [1 2 3]").asHostValue())
                    .isEqualTo(List.of(1L, 2L, 3L));
        }

        @Test
        void returnsALogicAsABoolean() {
            Interpreter interpreter = AnInterpreter.create();

            assertThat(interpreter.run("greater? 2 1").asHostValue()).isEqualTo(true);
        }

        @Test
        @DisplayName("none comes back as an empty optional, not as null")
        void returnsNoneAsAbsence() {
            Interpreter interpreter = AnInterpreter.create();

            assertThat(interpreter.run("if false [1]").asOptionalHostValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("the hole in the isolation story, which is deliberate")
    class HostObjectMutability {

        @Test
        @DisplayName("a host object handed in can be changed by the host afterwards")
        void leavesHostObjectsUnfrozen() {
            List<String> shared = new ArrayList<>(List.of("first"));
            Extension counting = new Extension("counting", "1.0.0", List.of(new ExtensionCommand(
                    "count-of", "anything", call -> IntegerValue.of(shared.size()))));
            Interpreter interpreter = AnInterpreter.withBounds(Bounds.standard().add(counting));
            interpreter.define("shared", shared);

            assertThat(interpreter.run("count-of 0").display()).isEqualTo("1");
            shared.add("second");

            assertThat(interpreter.run("count-of 0").display())
                    .as("JEBOL makes no promise about a host object; that is the caller's")
                    .isEqualTo("2");
        }
    }
}
