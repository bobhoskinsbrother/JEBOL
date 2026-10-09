package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.ContinueSignal;
import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachLoopNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<Datatype> A_BLOCK = Set.of(BlockValue.TYPE);

    private void call(DefaultNative definition, Set<String> refinements,
                      Value... arguments) {
        definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private Set<Datatype> whatRepeatCountsBy() {
        return TypesetValue.NUMBER.membersAnd(
                TypesetValue.SERIES.membersAnd(PairValue.TYPE, NoneValue.TYPE)
                        .toArray(Datatype[]::new));
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new LoopNative(), "loop",
                        List.of(Parameter.required("count", TypesetValue.NUMBER.members()),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new RepeatNative(), "repeat",
                        List.of(Parameter.softQuoted("counter"),
                                Parameter.required("count", whatRepeatCountsBy()),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new WhileNative(), "while",
                        List.of(Parameter.required("condition", A_BLOCK),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new UntilNative(), "until",
                        List.of(Parameter.required("body", A_BLOCK)), NOTHING),
                Arguments.of(new ForeverNative(), "forever",
                        List.of(Parameter.required("body", A_BLOCK)), NOTHING),
                Arguments.of(new ForNative(), "for",
                        List.of(Parameter.softQuoted("counter"),
                                Parameter.required("start"),
                                Parameter.required("end"),
                                Parameter.required("step"),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ForEachNative(), "foreach",
                        List.of(Parameter.softQuoted("target"),
                                Parameter.required("series"),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new RemoveEachNative(), "remove-each",
                        List.of(Parameter.softQuoted("word"),
                                Parameter.required("series", Set.of(BlockValue.TYPE,
                                        BinaryValue.TYPE, StringValue.TYPE, MapValue.TYPE,
                                        VectorValue.TYPE)),
                                Parameter.required("body", A_BLOCK)),
                        Set.of("count")),
                Arguments.of(new MapEachNative(), "map-each",
                        List.of(Parameter.softQuoted("word"),
                                Parameter.required("series", A_BLOCK),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ForSkipNative(), "forskip",
                        List.of(Parameter.softQuoted("word"),
                                Parameter.required("size",
                                        Set.of(IntegerValue.TYPE, DecimalValue.TYPE)),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ForAllNative(), "forall",
                        List.of(Parameter.softQuoted("word"),
                                Parameter.required("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ContinueNative(), "continue", List.of(), NOTHING),
                Arguments.of(new BreakNative(), "break",
                        List.of(Parameter.belongingTo("return", "value",
                                TypesetValue.ANY_TYPE.members())),
                        Set.of("return")));
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
    @DisplayName("continue and break leave the round or the loop by raising a signal")
    class ContinueAndBreak {

        @Test
        @DisplayName("continue raises the signal that ends this round only")
        void continueEndsTheRound() {
            assertThatThrownBy(() -> call(new ContinueNative(), NOTHING))
                    .isInstanceOf(ContinueSignal.class);
        }

        @Test
        @DisplayName("a plain break leaves the loop answering unset")
        void aPlainBreak() {
            assertThatThrownBy(() -> call(new BreakNative(), NOTHING))
                    .isInstanceOf(LoopSignal.class)
                    .extracting(signal -> ((LoopSignal) signal).answer())
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("break/return leaves the loop answering the value it was given")
        void breakReturningAValue() {
            assertThatThrownBy(() -> call(new BreakNative(), Set.of("return"),
                    IntegerValue.of(5)))
                    .isInstanceOf(LoopSignal.class)
                    .extracting(signal -> ((LoopSignal) signal).answer())
                    .isEqualTo(IntegerValue.of(5));
        }

        @Test
        @DisplayName("break/return with nothing behind it answers unset, as a plain break does")
        void breakReturningNothing() {
            assertThatThrownBy(() -> call(new BreakNative(), Set.of("return")))
                    .isInstanceOf(LoopSignal.class)
                    .extracting(signal -> ((LoopSignal) signal).answer())
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("break without /return ignores a value that happens to be there")
        void breakIgnoresAValueWithoutReturn() {
            assertThatThrownBy(() -> call(new BreakNative(), NOTHING, IntegerValue.of(5)))
                    .isInstanceOf(LoopSignal.class)
                    .extracting(signal -> ((LoopSignal) signal).answer())
                    .isInstanceOf(UnsetValue.class);
        }
    }
}
