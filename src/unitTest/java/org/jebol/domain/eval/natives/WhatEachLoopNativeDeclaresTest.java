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

    private Set<Datatype> listWhatRepeatCountsBy() {
        return TypesetValue.NUMBER.membersAnd(
                TypesetValue.SERIES.membersAnd(PairValue.TYPE, NoneValue.TYPE)
                        .toArray(Datatype[]::new));
    }

    Stream<Arguments> listWhatEachDeclares() {
        return Stream.of(
                Arguments.of(new LoopNative(), "loop",
                        List.of(Parameter.of("count", TypesetValue.NUMBER.members()),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new RepeatNative(), "repeat",
                        List.of(Parameter.quoteSoftly("counter"),
                                Parameter.of("count", listWhatRepeatCountsBy()),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new WhileNative(), "while",
                        List.of(Parameter.of("condition", A_BLOCK),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new UntilNative(), "until",
                        List.of(Parameter.of("body", A_BLOCK)), NOTHING),
                Arguments.of(new ForeverNative(), "forever",
                        List.of(Parameter.of("body", A_BLOCK)), NOTHING),
                Arguments.of(new ForNative(), "for",
                        List.of(Parameter.quoteSoftly("counter"),
                                Parameter.of("start"),
                                Parameter.of("end"),
                                Parameter.of("step"),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ForEachNative(), "foreach",
                        List.of(Parameter.quoteSoftly("target"),
                                Parameter.of("series"),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new RemoveEachNative(), "remove-each",
                        List.of(Parameter.quoteSoftly("word"),
                                Parameter.of("series", Set.of(BlockValue.TYPE,
                                        BinaryValue.TYPE, StringValue.TYPE, MapValue.TYPE,
                                        VectorValue.TYPE)),
                                Parameter.of("body", A_BLOCK)),
                        Set.of("count")),
                Arguments.of(new MapEachNative(), "map-each",
                        List.of(Parameter.quoteSoftly("word"),
                                Parameter.of("series", A_BLOCK),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ForSkipNative(), "forskip",
                        List.of(Parameter.quoteSoftly("word"),
                                Parameter.of("size",
                                        Set.of(IntegerValue.TYPE, DecimalValue.TYPE)),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ForAllNative(), "forall",
                        List.of(Parameter.quoteSoftly("word"),
                                Parameter.of("body", A_BLOCK)),
                        NOTHING),
                Arguments.of(new ContinueNative(), "continue", List.of(), NOTHING),
                Arguments.of(new BreakNative(), "break",
                        List.of(Parameter.belongTo("return", "value",
                                TypesetValue.ANY_TYPE.members())),
                        Set.of("return")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("listWhatEachDeclares")
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
        void includeAPlainBreak() {
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
