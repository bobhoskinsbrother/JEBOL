package org.jebol.domain.value;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AUserDefinedTypeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        String shown = interpreter.display(interpreter.run(source));
        boolean quoted = shown.length() >= 2
                && ((shown.charAt(0) == '"' && shown.endsWith("\""))
                        || (shown.charAt(0) == '{' && shown.endsWith("}")));
        return quoted ? shown.substring(1, shown.length() - 1) : shown;
    }

    private static String errorFields(String source) {
        return answerTo("e: try [" + source + "] mold reduce [e/id e/arg1 e/arg2]");
    }

    @Nested
    @DisplayName("make utype! reads a specification and a body")
    class BuildingOne {

        @Test
        @DisplayName("two blocks are enough, and the answer is a utype")
        void twoBlocksAreEnough() {
            assertThat(answerTo("""
                    u: make utype! [[x] [x]]
                    mold reduce [type? u utype? u]"""))
                    .isEqualTo("[#(utype!) #(true)]");
        }

        @ParameterizedTest(name = "{0} builds")
        @ValueSource(strings = {
            "[[x] [x] extra]", "[[x] [x] 1 2]", "[[] []]", "[['a] [1 2]]",
        })
        @DisplayName("and anything past the first two elements is not looked at")
        void anythingPastTheFirstTwoIsNotLookedAt(String definition) {
            assertThat(answerTo("utype? make utype! " + definition)).isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "{0} is refused")
        @ValueSource(strings = {
            "[]", "1", "-1", "1.5", "\"12\"", "[1 2]", "#{01020304}",
            "#\"a\"", "1x2", "10%", "1.2.3", "12:00", "$1", "%f", "[a: 1]",
            "#abc", "a@b", "http://x", "[[x]]", "[[x] 1]", "[1 [x]]",
        })
        @DisplayName("and everything else is an invalid argument naming the whole offer")
        void everythingElseIsAnInvalidArgument(String offered) {
            assertThat(errorFields("make utype! " + offered))
                    .as("Trap_Arg names the argument itself, whatever shape it was")
                    .isEqualTo("[invalid-arg " + offered + " _]");
        }

        @Test
        @DisplayName("a lit-word arrives as the word it names, and a none as a none")
        void arrivesAsWhatTheEvaluatorMade() {
            assertThat(errorFields("make utype! 'a"))
                    .as("the quote is spent by the time MAKE sees the argument, "
                            + "so ARG1 is the word and not the lit-word")
                    .isEqualTo("[invalid-arg a _]");
            assertThat(errorFields("make utype! none"))
                    .isEqualTo("[invalid-arg _ _]");
            assertThat(errorFields("make utype! true"))
                    .isEqualTo("[invalid-arg #(true) _]");
            assertThat(errorFields("make utype! integer!"))
                    .isEqualTo("[invalid-arg #(integer!) _]");
        }

        @Test
        @DisplayName("to utype! is refused as an invalid type, whatever it is given")
        void toUtypeIsRefused() {
            assertThat(errorFields("to utype! 1"))
                    .isEqualTo("[invalid-type #(utype!) _]");
            assertThat(errorFields("to utype! [[x] [x]]"))
                    .isEqualTo("[invalid-type #(utype!) _]");
        }

        @Test
        @DisplayName("and the construction syntax is a malconstruct")
        void theConstructionSyntaxIsAMalconstruct() {
            assertThat(answerTo("""
                    e: try [load {#(utype! [[x] [x]])}]
                    mold reduce [e/id e/arg1]"""))
                    .isEqualTo("[malconstruct [utype! [[x] [x]]]]");
        }
    }

    @Nested
    @DisplayName("what it built does nothing")
    class WhatItDoes {

        @Test
        @DisplayName("the type is all it has, and the three ways of writing it")
        void theTypeIsAllItHas() {
            assertThat(answerTo("mold make utype! [[x] [x]]"))
                    .isEqualTo("utype!");
            assertThat(answerTo("mold/all make utype! [[x] [x]]"))
                    .as("under /ALL it is a construct, the way every value with "
                            + "no literal form is")
                    .isEqualTo("#(utype!)");
            assertThat(answerTo("form make utype! [[x] [x]]"))
                    .as("the C's Emit gives the question-marked spelling")
                    .isEqualTo("?utype!?");
        }

        @Test
        @DisplayName("it is not equal to itself, in any mode")
        void isNotEqualToItself() {
            assertThat(answerTo("""
                    u: make utype! [[x] [x]]
                    mold reduce [u = u u == u same? u u u <> u not same? u u]"""))
                    .as("CT_Utype returns FALSE whatever mode it is asked, so this "
                            + "is the one value REBOL has that is not equal to itself")
                    .isEqualTo("[#(false) #(false) #(false) #(true) #(true)]");
        }

        @Test
        @DisplayName("two of them are not equal either, however alike")
        void twoOfThemAreNotEqual() {
            assertThat(answerTo("""
                    mold reduce [
                        (make utype! [[x] [x]]) = (make utype! [[x] [x]])
                        (make utype! [[x] [x]]) = (make utype! [[y] [y]])]"""))
                    .isEqualTo("[#(false) #(false)]");
        }

        @Test
        @DisplayName("every action traps, because the object behind it holds no function")
        void everyActionTraps() {
            assertThat(answerTo("""
                    e: try [length? make utype! [[x] [x]]]
                    mold reduce [e/id e/arg1 e/arg2 e/arg3]"""))
                    .as("the action's own parameter check refuses it, naming the "
                            + "action, the parameter and the datatype")
                    .isEqualTo("[expect-arg length? series #(utype!)]");
            assertThat(answerTo("""
                    e: try [append make utype! [[x] [x]] 1]
                    mold reduce [e/id e/arg1 e/arg2 e/arg3]"""))
                    .isEqualTo("[expect-arg append series #(utype!)]");
            assertThat(answerTo("""
                    u: make utype! [[x] [x]]
                    e: try [u + 1]
                    mold reduce [e/id e/arg1 e/arg2]"""))
                    .isEqualTo("[cannot-use add: #(utype!)]");
        }

        @Test
        @DisplayName("it can still be held and walked over, like any value")
        void canBeHeldLikeAnyValue() {
            assertThat(answerTo("""
                    u: make utype! [[x] [x]]
                    mold reduce [length? reduce [u u] pick reduce [u] 1]"""))
                    .isEqualTo("[2 utype!]");
        }
    }
}
