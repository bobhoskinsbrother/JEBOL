package org.jebol.domain.eval.actions;

import org.jebol.application.Interpreter;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachPositioningActionDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<Datatype> AN_OFFSET =
            Typeset.NUMBER.membersAnd(LogicValue.TYPE, PairValue.TYPE);

    private final GrantedServices granted = new GrantedServices();

    private String ran(String source) {
        Interpreter interpreter = Interpreter.create();
        return interpreter.display(interpreter.run(source));
    }

    private Set<Datatype> somewhereToStand() {
        return Typeset.SERIES.membersAnd(PortValue.TYPE, NoneValue.TYPE, GobValue.TYPE);
    }

    private Set<Datatype> somethingWithATail() {
        return Typeset.SERIES.membersAnd(GobValue.TYPE, PortValue.TYPE, BitsetValue.TYPE,
                TypesetValue.TYPE, MapValue.TYPE);
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new HeadAction(granted), "head",
                        List.of(Parameter.required("series")), NOTHING),
                Arguments.of(new TailAction(granted), "tail",
                        List.of(Parameter.required("series")), NOTHING),
                Arguments.of(new NextAction(granted), "next",
                        List.of(Parameter.required("series")), NOTHING),
                Arguments.of(new BackAction(granted), "back",
                        List.of(Parameter.required("series")), NOTHING),
                Arguments.of(new IsHeadAction(), "head?",
                        List.of(Parameter.required("series")), NOTHING),
                Arguments.of(new IsTailAction(granted), "tail?",
                        List.of(Parameter.required("series", somethingWithATail())), NOTHING),
                Arguments.of(new IsPastAction(), "past?",
                        List.of(Parameter.required("series")), NOTHING),
                Arguments.of(new IndexAction(granted), "index?",
                        List.of(Parameter.required("series", somewhereToStand())),
                        Set.of("xy")),
                Arguments.of(new IndexzAction(granted), "indexz?",
                        List.of(Parameter.required("series", somewhereToStand())),
                        Set.of("xy")),
                Arguments.of(new SkipAction(granted), "skip",
                        List.of(Parameter.required("series"),
                                Parameter.required("offset", AN_OFFSET)), NOTHING),
                Arguments.of(new AtAction(granted), "at",
                        List.of(Parameter.required("series"),
                                Parameter.required("index", AN_OFFSET)), NOTHING),
                Arguments.of(new AtzAction(granted), "atz",
                        List.of(Parameter.required("series"),
                                Parameter.required("position",
                                        Set.of(IntegerValue.TYPE, PairValue.TYPE))), NOTHING));
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
    @DisplayName("head, tail, next and back move along a series and stop at its ends")
    class MovingOneStep {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "index? head next [1 2 3]          | 1",
                "index? tail [1 2 3]               | 4",
                "index? tail []                    | 1",
                "index? next [1 2 3]               | 2",
                "index? next tail [1 2 3]          | 4",
                "index? back tail [1 2 3]          | 3",
                "index? back [1 2 3]               | 1",
                "index? next next next next [1 2]  | 3"})
        void staysInsideTheSeries(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a value that is not a series cannot be moved along")
        void refusesANumber() {
            assertThat(ran("""
                    error? try [next 1]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    error? try [head 1]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("skip, at and atz count from where the series stands, clamped to its ends")
    class MovingByACount {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "index? skip [1 2 3] 0           | 1",
                "index? skip [1 2 3] 1           | 2",
                "index? skip [1 2 3] 3           | 4",
                "index? skip [1 2 3] 4           | 4",
                "index? skip [1 2 3] -1          | 1",
                "index? skip tail [1 2 3] -1     | 3",
                "index? skip [1 2 3] 1.9         | 2",
                "index? skip [1 2 3] true        | 1",
                "index? skip [1 2 3] false       | 2",
                "index? skip [1 2 3] 1.5         | 2",
                "index? at [1 2 3] true          | 1",
                "index? at [1 2 3] false         | 2",
                "index? at [1 2 3] 1             | 1",
                "index? at [1 2 3] 0             | 1",
                "index? at [1 2 3] 3             | 3",
                "index? at [1 2 3] 4             | 4",
                "index? at [1 2 3] 5             | 4",
                "index? at next [1 2 3] 2        | 3",
                "index? atz [1 2 3] 0            | 1",
                "index? atz [1 2 3] 2            | 3",
                "index? atz [1 2 3] 9            | 4"})
        void landsWhereRebolLands(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a pair names a position only in something with a width")
        void aPairNeedsAnImage() {
            assertThat(ran("""
                    error? try [at [1 2 3] 1x1]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    index? at make image! 3x2 2x2""")).isEqualTo("5");
            assertThat(ran("""
                    index? atz make image! 3x2 1x1""")).isEqualTo("5");
        }

        @Test
        @DisplayName("text where a count belongs is refused")
        void refusesTextForACount() {
            assertThat(ran("""
                    error? try [skip [1 2 3] "1"]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("index? counts from one and indexz? from nought")
    class WhereItStands {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "index? [a b]                     | 1",
                "indexz? [a b]                    | 0",
                "index? tail [a b]                | 3",
                "indexz? tail [a b]               | 2",
                "index? next {abc}                | 2",
                "index?/xy next next next make image! 2x2  | 2x2",
                "indexz?/xy next next next make image! 2x2 | 1x1"})
        void countsAsRebolCounts(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("index? of none is none, and indexz? of none is refused")
        void noneStandsNowhere() {
            assertThat(ran("""
                    none? index? none""")).isEqualTo("#(true)");
            assertThat(ran("""
                    error? try [indexz? none]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("head?, tail? and past? ask where a series stands")
    class AskingWhereItStands {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "head? [1 2]                             | #(true)",
                "head? next [1 2]                        | #(false)",
                "tail? [1 2]                             | #(false)",
                "tail? tail [1 2]                        | #(true)",
                "tail? []                                | #(true)",
                "tail? make map! []                      | #(true)",
                "tail? make map! [a 1]                   | #(false)",
                "tail? make bitset! 0                    | #(true)",
                "tail? make bitset! 8                    | #(false)",
                "tail? make typeset! []                  | #(true)",
                "tail? make typeset! [integer!]          | #(false)",
                "past? tail [1 2]                        | #(false)",
                "past? [1 2]                             | #(false)",
                "b: [1 2 3] c: at b 3 clear b past? c    | #(true)"})
        void answersAsRebolDoes(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("tail? refuses none, an object and a module, as Rebol does")
        void tailRefusesWhatItDoesNotDeclare() {
            assertThat(ran("""
                    error? try [tail? none]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    error? try [tail? make object! []]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a number has no head, no tail and nothing to be past")
        void aNumberIsNotASeries() {
            assertThat(ran("""
                    error? try [head? 1]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    error? try [tail? 1]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    error? try [past? 1]""")).isEqualTo("#(true)");
        }
    }
}
