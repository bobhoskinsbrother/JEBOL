package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.DefaultNative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachSeriesActionDeclaresTest {

    private static final String YES = "#(true)";

    private final GrantedServices granted = new GrantedServices();

    private String ran(String source) {
        Interpreter interpreter = Interpreter.create();
        return interpreter.display(interpreter.run(source));
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        return Stream.of(
                Arguments.of(new LengthAction(granted), "length?", Set.of()),
                Arguments.of(new FirstNative(), "first", Set.of()),
                Arguments.of(new SecondNative(), "second", Set.of()),
                Arguments.of(new ThirdNative(), "third", Set.of()),
                Arguments.of(new FourthNative(), "fourth", Set.of()),
                Arguments.of(new FifthNative(), "fifth", Set.of()),
                Arguments.of(new SixthNative(), "sixth", Set.of()),
                Arguments.of(new SeventhNative(), "seventh", Set.of()),
                Arguments.of(new EighthNative(), "eighth", Set.of()),
                Arguments.of(new NinthNative(), "ninth", Set.of()),
                Arguments.of(new TenthNative(), "tenth", Set.of()),
                Arguments.of(new PickAction(), "pick", Set.of()),
                Arguments.of(new PickzNative(), "pickz", Set.of()),
                Arguments.of(new LastNative(), "last", Set.of()),
                Arguments.of(new FirstPlusNative(), "first+", Set.of()),
                Arguments.of(new SwapAction(), "swap", Set.of()),
                Arguments.of(new IncrementNative(), "++", Set.of()),
                Arguments.of(new DecrementNative(), "--", Set.of()),
                Arguments.of(new TruncateNative(), "truncate", Set.of("part")),
                Arguments.of(new AppendAction(granted), "append", Set.of("part", "only", "dup")),
                Arguments.of(new InsertAction(granted), "insert", Set.of("part", "only", "dup")),
                Arguments.of(new ChangeAction(), "change", Set.of("part", "only", "dup")),
                Arguments.of(new ClearAction(granted), "clear", Set.of()),
                Arguments.of(new RemoveAction(), "remove", Set.of("part", "key")),
                Arguments.of(new ReverseAction(), "reverse", Set.of("part")),
                Arguments.of(new CopyAction(), "copy", Set.of("part", "deep", "types")),
                Arguments.of(new FindAction(), "find", Set.of("tail", "last", "only", "case",
                        "any", "same", "part", "with", "skip", "reverse", "match")),
                Arguments.of(new SortAction(), "sort", Set.of("case", "compare", "skip",
                        "reverse", "all", "part", "unstable")),
                Arguments.of(new IntersectNative(), "intersect", Set.of("case", "skip")),
                Arguments.of(new UnionNative(), "union", Set.of("case", "skip")),
                Arguments.of(new ExcludeNative(), "exclude", Set.of("case", "skip")),
                Arguments.of(new UniqueNative(), "unique", Set.of("case", "skip")),
                Arguments.of(new ReduceNative(), "reduce", Set.of("into", "only", "no-set")),
                Arguments.of(new ComposeNative(), "compose", Set.of("only", "deep", "into")),
                Arguments.of(new TranscodeNative(), "transcode",
                        Set.of("one", "error", "next", "part", "line", "only")),
                Arguments.of(new RoundAction(), "round", Set.of("to", "down", "even",
                        "half-down", "floor", "ceiling", "half-ceiling")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to its Rebol name with the refinements it always had")
    void declaresItsNameAndRefinements(DefaultNative definition, String name,
                                       Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("picking reads a position, and answers none outside the series")
    class Picking {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "first [a b c]                | a",
                "third [a b c]                | c",
                "tenth [1 2 3 4 5 6 7 8 9 10] | 10",
                "none? fourth [a b c]         | #(true)",
                "pick [a b c] 1               | a",
                "pick [a b c] 3               | c",
                "none? pick [a b c] 4         | #(true)",
                "none? pick [a b c] 0         | #(true)",
                "pick next [a b c] -1         | a",
                "none? pick next [a b c] -2   | #(true)",
                "pick [a b] true              | a",
                "pick [a b] false             | b",
                "pickz [a b c] 0              | a",
                "pickz next [a b c] -1        | a",
                "first 1.2.3                  | 1",
                "none? pick 1.2.3 4           | #(true)",
                "pick 3x4 2                   | 4.0",
                "last [a b c]                 | c",
                "last 1.2.3                   | 3",
                "pick 12:34:56 2              | 34",
                "pick 12:34:56 'hour          | 12",
                "pick make map! [a 1] 'a      | 1",
                "pick make bitset! [97] 97    | #(true)"})
        void picksWhatRebolPicks(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a number has no position to pick from")
        void refusesANumber() {
            assertThat(ran("""
                    error? try [first 1]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [pick 1 1]""")).isEqualTo(YES);
        }

        @Test
        @DisplayName("first+ answers the first and steps the word along")
        void firstPlusSteps() {
            assertThat(ran("""
                    b: [1 2] reduce [first+ b first+ b first+ b index? b]""")).isEqualTo(
                    "[1 2 _ 3]");
        }
    }

    @Nested
    @DisplayName("length?, ++, --, swap and truncate")
    class MeasuringAndStepping {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "length? []                       | 0",
                "length? [a b c]                  | 3",
                "length? next [a b c]             | 2",
                "length? {héllo}                  | 5",
                "length? 1.2.3                    | 3",
                "length? 'hello                   | 5",
                "none? length? none               | #(true)",
                "n: 1 ++ n n                      | 2",
                "n: 1 -- n n                      | 0",
                "c: to char! 97 ++ c c = to char! 98 | #(true)",
                "b: [1 2] ++ b index? b           | 2",
                "b: tail [1 2] ++ b index? b      | 3",
                "b: [1 2] -- b index? b           | 1",
                "a: [1 2] b: [3 4] swap a b a     | [3 2]",
                "a: [] b: [3 4] swap a b b        | [3 4]",
                "b: next [1 2 3] truncate b       | [2 3]",
                "b: [1 2 3 4] truncate/part b 2   | [1 2]"})
        void answersAsRebolDoes(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a number has no length, and swap wants two of the same series")
        void refusesWhatIsNotASeries() {
            assertThat(ran("""
                    error? try [length? 1]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [swap [1] {a}]""")).isEqualTo(YES);
        }
    }

    @Nested
    @DisplayName("append, insert, change, remove and clear edit a series in place")
    class Editing {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "append [1] 2                          | [1 2]",
                "append [1] [2 3]                      | [1 2 3]",
                "append/only [1] [2 3]                 | [1 [2 3]]",
                "append/dup [] 0 3                     | [0 0 0]",
                "append/dup [] 0 0                     | []",
                "append/part [] [1 2 3] 2              | [1 2]",
                "head insert [2] 1                     | [1 2]",
                "b: [1 2 3] change b 9 b               | [9 2 3]",
                "b: [1 2 3] change/part b 9 2 b        | [9 3]",
                "b: [1 2 3] change/dup b 9 2 b         | [9 9 3]",
                "b: tail [1 2] change b 9 head b       | [1 2 9]",
                "b: [1 2 3] remove b b                 | [2 3]",
                "b: [1 2 3] remove/part b 2 b          | [3]",
                "b: [1 2 3] remove/part b 0 b          | [1 2 3]",
                "b: [a 1 b 2] remove/key b 'a b        | [b 2]",
                "b: [] remove b b                      | []",
                "none? remove none                     | #(true)",
                "b: [1 2 3] clear next b b             | [1]",
                "none? clear none                      | #(true)"})
        void editsAsRebolDoes(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("text where a block belongs is refused rather than coerced")
        void refusesTheWrongSubject() {
            assertThat(ran("""
                    error? try [append 1 2]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [remove 1]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [remove/key {abc} 1]""")).isEqualTo(YES);
        }
    }

    @Nested
    @DisplayName("reverse, copy and find")
    class ReversingCopyingFinding {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "reverse [1 2 3]                       | [3 2 1]",
                "reverse/part [1 2 3] 2                | [2 1 3]",
                "reverse/part [1 2 3] 0                | [1 2 3]",
                "reverse/part [1 2 3] 9                | [3 2 1]",
                "reverse []                            | []",
                "equal? reverse {abc} {cba}            | #(true)",
                "reverse #{0102}                       | #{0201}",
                "reverse 1.2.3                         | 3.2.1",
                "reverse/part 1.2.3 2                  | 2.1.3",
                "reverse 1x2                           | 2x1",
                "copy/part [1 2 3] 2                   | [1 2]",
                "copy/part [1 2 3] 0                   | []",
                "copy/part [1 2 3] 9                   | [1 2 3]",
                "copy/part tail [1 2 3] -2             | [2 3]",
                "equal? copy/part {abc} 2 {ab}         | #(true)",
                "b: [[1]] c: copy/deep b same? first b first c | #(false)",
                "b: [[1]] c: copy b same? first b first c      | #(true)",
                "index? find [a b c] 'b                | 2",
                "none? find [a b c] 'd                 | #(true)",
                "index? find/tail [a b c] 'b           | 3",
                "none? find/match [a b c] 'b           | #(true)",
                "index? find/last [a b a] 'a           | 3",
                "index? find/skip [a 1 b 2] 'b 2       | 3",
                "find make object! [a: 1] 'a           | #(true)",
                "none? find none 'a                    | #(true)"})
        void answersAsRebolDoes(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("an object has no order to copy part of, and a gob cannot be reversed by part")
        void refusesWhatHasNoOrder() {
            assertThat(ran("""
                    error? try [copy/part make object! [a: 1] 1]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [find/skip [a b] 'a 0]""")).isEqualTo(YES);
        }
    }

    @Nested
    @DisplayName("sort and the set operations")
    class SortingAndSets {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "sort [3 1 2]                          | [1 2 3]",
                "sort/reverse [3 1 2]                  | [3 2 1]",
                "sort []                               | []",
                "sort [1]                              | [1]",
                "sort/skip [b 1 a 2] 2                 | [a 2 b 1]",
                "sort/part [3 2 1] 2                   | [2 3 1]",
                "sort/compare [1 3 2] func [a b] [a > b] | [3 2 1]",
                "equal? sort {cab} {abc}               | #(true)",
                "union [1 2] [2 3]                     | [1 2 3]",
                "intersect [1 2] [2 3]                 | [2]",
                "exclude [1 2] [2 3]                   | [1]",
                "unique [1 2 1 2]                      | [1 2]",
                "union/skip [a 1 b 2] [a 3] 2          | [a 1 b 2]"})
        void answersAsRebolDoes(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("a record width that does not divide the series is out of range")
        void refusesABadRecordWidth() {
            assertThat(ran("""
                    error? try [sort/skip [1 2 3] 2]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [sort/skip [1 2] 0]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [union/skip [1 2] [3 4] 0]""")).isEqualTo(YES);
        }
    }

    @Nested
    @DisplayName("reduce, compose, transcode and round")
    class EvaluatingAndRounding {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "reduce [1 + 1 2 * 3]                    | [2 6]",
                "reduce 5                                | 5",
                "b: [] reduce/into [1 + 1] b b           | [2]",
                "a: 1 reduce/only [a b] [b]              | [1 b]",
                "compose [1 (1 + 1)]                     | [1 2]",
                "compose [(reduce [1 2])]                | [1 2]",
                "compose/only [(reduce [1 2])]           | [[1 2]]",
                "compose/deep [[(1 + 1)]]                | [[2]]",
                "compose 5                               | 5",
                "transcode {1 a}                         | [1 a]",
                "transcode/one {1 a}                     | 1",
                "round 2.5                               | 3.0",
                "round -2.5                              | -3.0",
                "round/down 2.7                          | 2.0",
                "round/floor -2.5                        | -3.0",
                "round/ceiling 2.1                       | 3.0",
                "round/even 2.5                          | 2.0",
                "round/half-down 2.5                     | 2.0",
                "round 2                                 | 2",
                "round/to 7 5                            | 5",
                "round/to 8 5                            | 10",
                "round 3x4.6                             | 3x5"})
        void answersAsRebolDoes(String source, String wanted) {
            assertThat(ran(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("rounding a whole number to a step of nought divides by zero")
        void roundingToNothingIsADivisionByZero() {
            assertThat(ran("""
                    error? try [round/to 7 0]""")).isEqualTo(YES);
            assertThat(ran("""
                    error? try [transcode 1]""")).isEqualTo(YES);
        }
    }
}
