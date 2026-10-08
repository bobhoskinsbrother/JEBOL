package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ReturnSignal;
import org.jebol.domain.eval.ThrownSignal;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachExitNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private void call(DefaultNative definition, Set<String> refinements,
                      Value... arguments) {
        definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new ReturnNative(), "return",
                        List.of(Parameter.required("value", Typeset.ANY_TYPE.members())),
                        NOTHING),
                Arguments.of(new ExitNative(), "exit", List.of(), NOTHING),
                Arguments.of(new ThrowNative(), "throw",
                        List.of(Parameter.required("value", Typeset.ANY_TYPE.members()),
                                Parameter.belongingTo("name", "word", Set.of(Datatype.WORD))),
                        Set.of("name")),
                Arguments.of(new CatchNative(), "catch",
                        List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                                Parameter.belongingTo("name", "word",
                                        Set.of(Datatype.WORD, Datatype.BLOCK)),
                                Parameter.belongingTo("with", "callback", Set.of())),
                        Set.of("name", "all", "quit", "with")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("whatEachDeclares")
    @DisplayName("each declares exactly what the inline definition declared")
    void declaresWhatItDeclaredInline(DefaultNative definition, String name,
            List<Parameter> parameters, Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.parametersAsWritten()).isEqualTo(parameters);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("return and exit leave the function by raising a return")
    class ReturnAndExit {

        @Test
        @DisplayName("return carries the value it was given")
        void returnCarriesItsValue() {
            assertThatThrownBy(() -> call(new ReturnNative(), NOTHING, IntegerValue.of(7)))
                    .isInstanceOf(ReturnSignal.class)
                    .extracting(signal -> ((ReturnSignal) signal).value())
                    .isEqualTo(IntegerValue.of(7));
        }

        @Test
        @DisplayName("return of unset carries unset, which is what exit carries too")
        void returnOfUnsetIsExit() {
            assertThatThrownBy(() -> call(new ReturnNative(), NOTHING, UnsetValue.unset()))
                    .isInstanceOf(ReturnSignal.class)
                    .extracting(signal -> ((ReturnSignal) signal).value())
                    .isInstanceOf(UnsetValue.class);
            assertThatThrownBy(() -> call(new ExitNative(), NOTHING))
                    .isInstanceOf(ReturnSignal.class)
                    .extracting(signal -> ((ReturnSignal) signal).value())
                    .isInstanceOf(UnsetValue.class);
        }
    }

    @Nested
    @DisplayName("throw hands its value to the nearest catch that answers")
    class Throw {

        @Test
        @DisplayName("a plain throw carries its value and no name")
        void aPlainThrow() {
            assertThatThrownBy(() -> call(new ThrowNative(), NOTHING, IntegerValue.of(3)))
                    .isInstanceOf(ThrownSignal.class)
                    .satisfies(signal -> {
                        assertThat(((ThrownSignal) signal).value()).isEqualTo(IntegerValue.of(3));
                        assertThat(((ThrownSignal) signal).name()).isEmpty();
                    });
        }

        @Test
        @DisplayName("throw/name carries the name, in the case Rebol compares words in")
        void aNamedThrow() {
            assertThatThrownBy(() -> call(new ThrowNative(), Set.of("name"),
                    IntegerValue.of(5), WordValue.of("Foo")))
                    .isInstanceOf(ThrownSignal.class)
                    .extracting(signal -> ((ThrownSignal) signal).name())
                    .isEqualTo(Optional.of("foo"));
        }

        @Test
        @DisplayName("throw/name with no name argument behind it throws unnamed")
        void aNamedThrowMissingItsName() {
            assertThatThrownBy(() -> call(new ThrowNative(), Set.of("name"), IntegerValue.of(5)))
                    .isInstanceOf(ThrownSignal.class)
                    .extracting(signal -> ((ThrownSignal) signal).name())
                    .isEqualTo(Optional.empty());
        }
    }
}
