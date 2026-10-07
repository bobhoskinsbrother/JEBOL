package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachInterpreterStateNativeDeclaresTest {

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.create().run(source).value());
    }

    private String errorIdOf(String source) {
        return answerTo("select try [" + source + "] 'id");
    }

    private String firstArgumentOf(String source) {
        return answerTo("select try [" + source + "] 'arg1");
    }

    private String secondArgumentOf(String source) {
        return answerTo("select try [" + source + "] 'arg2");
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        GrantedServices granted = new GrantedServices();
        return Stream.of(
                Arguments.of(new VersionNative(), "version", Set.of("data")),
                Arguments.of(new PokezNative(), "pokez", Set.of()),
                Arguments.of(new ToRealFileNative(granted), "to-real-file", Set.of()),
                Arguments.of(new RecycleNative(), "recycle", Set.of("off", "on", "ballast", "torture", "pools")),
                Arguments.of(new StatsNative(), "stats", Set.of("show", "profile", "timer", "evals", "dump-series")),
                Arguments.of(new EchoNative(granted), "echo", Set.of()),
                Arguments.of(new IsTerminalNative(), "tty?", Set.of()),
                Arguments.of(new WaitNative(), "wait", Set.of("all", "only")),
                Arguments.of(new ReadKeyNative(granted), "read-key", Set.of()),
                Arguments.of(new HaltNative(), "halt", Set.of()),
                Arguments.of(new DoCodecNative(), "do-codec", Set.of()),
                Arguments.of(new ReleaseNative(), "release", Set.of()),
                Arguments.of(new MapEventNative(), "map-event", Set.of()),
                Arguments.of(new WakeUpNative(), "wake-up", Set.of()),
                Arguments.of(new MapGobOffsetNative(), "map-gob-offset", Set.of("reverse")),
                Arguments.of(new AsColorNative(), "as-color", Set.of()),
                Arguments.of(new GrayscaleNative(), "grayscale", Set.of()),
                Arguments.of(new LuminosityNative(), "luminosity", Set.of("luma")),
                Arguments.of(new HsvToRgbNative(), "hsv-to-rgb", Set.of()),
                Arguments.of(new RgbToHsvNative(), "rgb-to-hsv", Set.of()),
                Arguments.of(new ColorDistanceNative(), "color-distance", Set.of()),
                Arguments.of(new TintNative(), "tint", Set.of()),
                Arguments.of(new LimitUsageNative(), "limit-usage", Set.of()),
                Arguments.of(new DsNative(), "ds", Set.of()),
                Arguments.of(new DumpNative(), "dump", Set.of("fmt")),
                Arguments.of(new CheckNative(), "check", Set.of()),
                Arguments.of(new EvokeNative(), "evoke", Set.of()),
                Arguments.of(new StackNative(), "stack",
                        Set.of("block", "word", "func", "args", "size", "depth", "limit")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to its Rebol name with the refinements Rebol declares")
    void declaresItsNameAndRefinements(NativeDefinition definition, String name,
            Set<String> refinements) {
        assertThat(definition.name()).isEqualTo(name);
        assertThat(definition.refinements()).isEqualTo(refinements);
    }

    @ParameterizedTest(name = "{0} is refused with {1}, naming {2} and {3}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            b: [a b c] pokez b 3 'x b               | out-of-range | 4              | _
            b: [a b c] pokez b -1 'x b              | out-of-range | -1             | _
            pokez 1.2.3 0 9                         | cannot-use   | poke:          | #(tuple!)
            pokez 1 0 0                             | expect-arg   | pokez          | series
            color-distance 1.2 3.4.5                | expect-arg   | color-distance | a
            check head insert "abc" #"^@"           | bad-series   | _              | _
            check skip head insert "abc" #"^@" 2    | bad-series   | _              | _
            check #{0001}                           | bad-series   | _              | _
            evoke 'crash                            | feature-na   | _              | _
            evoke 'crash-dump                       | feature-na   | _              | _
            release 1                               | expect-arg   | release        | handle
            do-codec 1 'decode #{}                  | expect-arg   | do-codec       | handle
            append none 1                           | expect-arg   | append         | series
            append 1 2                              | expect-arg   | append         | series
            append make error! "x" 1                | expect-arg   | append         | series
            append make typeset! [] integer!        | expect-arg   | append         | series
            append 1.2.3 4                          | expect-arg   | append         | series
            insert 1 2                              | expect-arg   | insert         | series
            poke "abc" 4 #"x"                       | out-of-range | 4              | _
            poke [a] 2 1                            | out-of-range | 2              | _
            """)
    void refusesAsRebolDoes(String source, String id, String firstArgument, String secondArgument) {
        assertThat(errorIdOf(source)).isEqualTo(id);
        assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        assertThat(secondArgumentOf(source)).isEqualTo(secondArgument);
    }

    @Nested
    @DisplayName("pokez, check, dump, wait, version and the counters")
    class TheSmallerNatives {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                b: [a b c] pokez b 0 'x b                   | [x b c]
                b: [a b c] pokez b 2 'x b                   | [a b x]
                s: "abc" pokez s 1 #"z" s                   | "azc"
                bs: make bitset! 8 pokez bs 3 true bs       | #(bitset! #{10})
                check "abc"                                 | "abc"
                check [a b]                                 | [a b]
                dump 1                                      | 1
                dump/fmt "a"                                | "a"
                type? recycle                               | #(integer!)
                type? stats                                 | #(integer!)
                type? stats/timer                           | #(time!)
                type? stats/evals                           | #(integer!)
                type? stats/profile                         | #(object!)
                stats/dump-series 1                         | _
                wait 0                                      | _
                wait []                                     | _
                wait [0 none]                               | _
                string? version                             | #(true)
                string? version/data                        | #(true)
                length? load/as version/data 'unbound       | 15
                third load/as version/data 'unbound         | 3.22.5
                append object [a: 1] [b 2]                  | make object! [a: 1 b: 2]
                append make map! [] [a 1]                   | #[a: 1]
                append make bitset! 8 "a"                   | #(bitset! #{00000000000000000000000040})
                insert make bitset! 8 "a"                   | #(bitset! #{00000000000000000000000040})
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("the colour natives")
    class Colours {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                as-color 1 2 3                                          | 1.2.3
                as-color 1.4 1.5 255.6                                  | 1.2.255
                as-color 50% 100% 0%                                    | 128.255.0
                as-color -5 300 2                                       | 0.255.2
                as-color 1.5% 0.2% 99.9%                                | 4.1.255
                grayscale 10.20.30                                      | 20
                grayscale 10.20.30.40                                   | 20
                grayscale 255.255.254                                   | 254
                i: make image! [1x1 10.20.30] grayscale i i/1           | 20.20.20.255
                luminosity 10.20.30                                     | 18
                luminosity/luma 10.20.30                                | 18
                luminosity 255.255.255                                  | 254
                i: make image! [1x1 10.20.30] luminosity i i/1          | 18.18.18.255
                hsv-to-rgb 0.255.255                                    | 255.0.0
                hsv-to-rgb 100.200.150                                  | 32.150.73
                hsv-to-rgb 0.0.77                                       | 77.77.77
                hsv-to-rgb 255.255.255                                  | 255.0.255
                hsv-to-rgb 1.2.3.4                                      | 3.2.2.4
                rgb-to-hsv 255.0.0                                      | 0.255.255
                rgb-to-hsv 10.200.30                                    | 89.242.200
                rgb-to-hsv 0.0.0                                        | 0.0.0
                rgb-to-hsv 200.10.100                                   | 236.242.200
                rgb-to-hsv 1.2.3.4                                      | 148.170.3.4
                color-distance 0.0.0 255.255.255                        | 764.833315173967
                color-distance 10.20.30 10.20.30                        | 0.0
                color-distance 255.0.0 0.0.255                          | 569.973683603024
                tint 100.100.100 200.0.0 0.5                            | 150.50.50
                tint 100.100.100 200.0.0 0                              | 100.100.100
                tint 100.100.100 200.0.0 1                              | 200.0.0
                tint 100.100.100 200.0.0 2                              | 200.0.0
                tint 100.100.100 200.0.0 50%                            | 150.50.50
                tint 100.100.100.7 200.0.0 0.25                         | 125.75.75.7
                i: make image! [1x1 100.100.100] tint i 200.0.0 0.5 i/1 | 150.50.50.255
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("map-gob-offset walks into the topmost child holding the point, and back out")
    class GobOffsets {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                g: make gob! [offset: 10x10 size: 100x100] c: make gob! [offset: 5x5 size: 20x20] append g c second map-gob-offset g 20x20         | 15x15
                g: make gob! [offset: 10x10 size: 100x100] c: make gob! [offset: 5x5 size: 20x20] append g c c = first map-gob-offset g 20x20      | #(true)
                g: make gob! [offset: 10x10 size: 100x100] c: make gob! [offset: 5x5 size: 20x20] append g c second map-gob-offset g 1x1           | 1x1
                g: make gob! [offset: 10x10 size: 100x100] c: make gob! [offset: 5x5 size: 20x20] append g c second map-gob-offset/reverse c 1x1   | 6x6
                g: make gob! [offset: 10x10 size: 100x100] c: make gob! [offset: 5x5 size: 20x20] append g c g = first map-gob-offset/reverse c 1x1 | #(true)
                second map-gob-offset make gob! [] 3.5x4                                                                                          | 3.5x4
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("stack and an error's where name every call entered, natives and nameless ones too")
    class Backtraces {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                stack/word 0                                                       | stack
                stack 0                                                            | [stack]
                stack -1                                                           | _
                stack/depth 0                                                      | 1
                f: func [] [stack 0] f                                             | [stack f]
                do [stack 0]                                                       | [stack do]
                try [stack 0]                                                      | [stack try]
                f: func [] [stack/word 1] g: func [] [do reduce [:f]] g            | -unnamed-
                k: func [] [stack 0] m: func [] [do reduce [:k]] m                 | [stack -unnamed- do m]
                k: func [] [stack 0] apply :k []                                   | [stack -apply- apply]
                g: func [n] [either n = 0 [stack 0] [g n - 1]] g 2                 | [stack either g either g either g]
                h: func [] [reduce [stack/depth 0 stack/word 2]] h                 | [3 h]
                select try [1 / 0] 'where                                          | [/ try]
                c: func [a b] [1 / 0] select try [sort/compare [2 1] :c] 'where    | [/ -apply- sort try]
                c: func [a b] [1 / 0] select try [do reduce [:c 1 2]] 'where       | [/ -unnamed- do try]
                c: func [a b] [1 / 0] select try [do [c 1 2]] 'where               | [/ c do try]
                k: func [] [1 / 0] m: func [] [do reduce [:k]] select try [m] 'where | [/ -unnamed- do m try]
                k: func [] [1 / 0] select try [apply :k []] 'where                 | [/ -apply- apply try]
                o: object [f: func [/r] [1 / 0] p: object [g: func [] [1 / 0]]] select try [o/f/r] 'where | [/ f try]
                o: object [f: func [/r] [1 / 0] p: object [g: func [] [1 / 0]]] select try [o/p/g] 'where | [/ g try]
                h: func [/r] [1 / 0] select try [h/r] 'where                       | [/ h try]
                select try [append/only 1 2] 'where                                | [try]
                select try [1 + none] 'where                                       | [try]
                w: func [a [integer!]] [a] select try [w "a"] 'where               | [try]
                w: func [a [integer!]] [a] w2: func [] [w "a"] select try [w2] 'where | [w2 try]
                select try [foreach x [1] [1 / 0]] 'where                          | [/ foreach try]
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }
}
