package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
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
class WhatEachConversionNativeDeclaresTest {

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.create().run(source).value());
    }

    private String errorIdOf(String source) {
        return answerTo("select try [" + source + "] 'id");
    }

    private String firstArgumentOf(String source) {
        return answerTo("select try [" + source + "] 'arg1");
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        return Stream.of(
                Arguments.of(new ToAction(), "to", Set.of()),
                Arguments.of(new AsPairNative(), "as-pair", Set.of()),
                Arguments.of(new ToHexNative(), "to-hex", Set.of("size")),
                Arguments.of(new EntabNative(), "entab", Set.of("size")),
                Arguments.of(new DetabNative(), "detab", Set.of("size")),
                Arguments.of(new DelineNative(), "deline", Set.of("lines")),
                Arguments.of(new EnlineNative(), "enline", Set.of()),
                Arguments.of(new AsNative(), "as", Set.of()));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to its Rebol name with the refinements Rebol declares")
    void declaresItsNameAndRefinements(NativeDefinition definition, String name,
            Set<String> refinements) {
        assertThat(definition.name()).isEqualTo(name);
        assertThat(definition.refinements()).isEqualTo(refinements);
    }

    @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
    @CsvSource(delimiter = '|', textBlock = """
            as-pair "1" 2                  | expect-arg   | as-pair
            to-hex "a"                     | expect-arg   | to-hex
            to-hex/size 255 0              | invalid-arg  | 0
            to-hex/size 255 -1             | invalid-arg  | -1
            detab 1                        | expect-arg   | detab
            entab/size "a" 0               | out-of-range | 0
            entab/size "a" -1              | out-of-range | -1
            detab/size "a" 9999999999      | out-of-range | 9999999999
            enline ["a"]                   | not-done     | _
            as integer! 1                  | expect-arg   | as
            as string! [a]                 | not-same-class | #(block!)
            as block! "a"                  | not-same-class | #(string!)
            """)
    void refusesAsRebolDoes(String source, String id, String firstArgument) {
        assertThat(errorIdOf(source)).isEqualTo(id);
        assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
    }

    @Nested
    @DisplayName("to converts through the same engine make uses")
    class Converting {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                to integer! "12"               | 12
                to integer! 1.9                | 1
                to integer! -1.9               | -1
                to integer! #FF                | 255
                to integer! "1'000"            | 1000
                to integer! "1.9"              | 1
                to integer! "3000000000.5"     | 3000000000
                to integer! "-3000000000.5"    | -3000000000
                to integer! "3.02961E+11"      | 302961000000
                to string! 12                  | "12"
                to string! #{FEFF0041}         | "A"
                to block! "a b"                | ["a b"]
                to word! "abc"                 | abc
                to word! #"a"                  | a
                to issue! "abc"                | #abc
                to decimal! 3                  | 3.0
                to decimal! "1e2"              | 100.0
                to percent! 0.5                | 50%
                to pair! [1 2]                 | 1x2
                to tuple! [1 2 3]              | 1.2.3
                to tuple! "1.2.3"              | 1.2.3
                to tuple! #010203              | 1.2.3
                to char! 65                    | #"A"
                to char! 1.5                   | #"^A"
                to char! #41                   | #"A"
                to logic! 0                    | #(true)
                to logic! none                 | #(false)
                to binary! "ab"                | #{6162}
                to binary! 1.2.3               | #{010203}
                to time! 3600                  | 1:00
                to time! 61                    | 0:01:01
                to time! "1:02:03"             | 1:02:03
                to money! 1                    | $1
                to money! "-$1"                | -$1
                to file! "a"                   | %a
                to email! [a b c]              | a@b.c
                to url! [http a b]             | http://a/b
                to 1 "5"                       | 5
                to "" 5                        | "5"
                to [] 1                        | [1]
                """)
        void convertsWhatRebolConverts(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', textBlock = """
                to integer! "x"               | bad-make-arg | #(integer!)
                to integer! none              | bad-make-arg | #(integer!)
                to map! 3                     | invalid-arg  | 3
                to object! 1                  | bad-make-arg | #(object!)
                to module! 1                  | bad-make-arg | #(module!)
                to module! [1]                | invalid-arg  | 1
                to binary! [1 2 300]          | out-of-range | 300
                to binary! [-1]               | out-of-range | -1
                to binary! [1.5]              | invalid-arg  | 1.5
                to char! -1                   | invalid-char | 4294967295
                to char! -2                   | invalid-char | 4294967294
                to char! 2147483647           | invalid-char | 2147483647
                to char! 2147483648           | out-of-range | 2147483648
                to char! -2147483649          | out-of-range | -2147483649
                to char! 1114112              | invalid-char | 1114112
                """)
        void refusesWhatRebolRefuses(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("make, now reached through the same object")
    class Making {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                make integer! "12"               | 12
                make string! 10                  | ""
                make block! 3                    | []
                make binary! 2                   | #{}
                make pair! [1 2]                 | 1x2
                make time! [1 2 3]               | 1:02:03
                make time! "1:02"                | 1:02
                make date! [2020 1 2]            | 2-Jan-2020
                make char! 65                    | #"A"
                make tuple! [1 2 3]              | 1.2.3
                make logic! 0                    | #(false)
                make money! "$1.50"              | $1.50
                make decimal! [1 2]              | 100.0
                make percent! "50%"              | 50%
                type? make gob! []               | #(gob!)
                """)
        void makesWhatRebolMakes(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0} is refused with {1}, naming {2}")
        @CsvSource(delimiter = '|', textBlock = """
                make struct! [a [int8]]             | invalid-arg  | [int8]
                make struct! [a [23]]               | invalid-arg  | [23]
                make struct! [a [int8! foo]]        | invalid-arg  | [int8! foo]
                make struct! [a [struct!]]          | invalid-arg  | [struct!]
                make struct! [a [struct! nope]]     | invalid-arg  | nope
                make struct! [a [struct! 1]]        | invalid-arg  | 1
                make struct! [a []]                 | invalid-arg  | []
                make struct! [a]                    | malconstruct | a
                make struct! [a 1]                  | malconstruct | a
                make struct! [1 [int8!]]            | malconstruct | 1
                make struct! []                     | malconstruct | []
                """)
        void aRefusedStructNamesThePieceThatOffended(String source, String id, String firstArgument) {
            assertThat(errorIdOf(source)).isEqualTo(id);
            assertThat(firstArgumentOf(source)).isEqualTo(firstArgument);
        }
    }

    @Nested
    @DisplayName("as-pair and to-hex")
    class PairsAndHex {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                as-pair 1 2                 | 1x2
                as-pair 1.5 2               | 1.5x2
                as-pair 0 0                 | 0x0
                as-pair -1 -2               | -1x-2
                as-pair 50% 1               | 0.5x1
                to-hex 255                  | #00000000000000FF
                to-hex 0                    | #0000000000000000
                to-hex -1                   | #FFFFFFFFFFFFFFFF
                to-hex/size 255 2           | #FF
                to-hex/size 255 1           | #F
                to-hex/size 255 16          | #00000000000000FF
                to-hex/size 255 20          | #00000000000000FF
                to-hex #"A"                 | #41
                to-hex to char! 255         | #FF
                to-hex to char! 256         | #0100
                to-hex to char! 65535       | #FFFF
                to-hex to char! 65536       | #010000
                to-hex/size #"A" 4          | #0041
                to-hex 1.2.3                | #010203
                to-hex 1.2.3.4              | #01020304
                to-hex/size 1.2.3 2         | #01
                to-hex/size 1.2.3 6         | #010203
                to-hex/size 1.2.3 20        | #010203
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("entab, detab, deline, enline and as")
    class TextAndCoercion {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                entab {    a}               | "^-a"
                entab {        a}           | "^-^-a"
                entab {   a}                | "   a"
                entab/size {  a} 2          | "^-a"
                entab/size { a} 1           | "^-a"
                detab {^-a}                 | "    a"
                detab/size {^-a} 2          | "  a"
                detab/size {^-a} 1          | " a"
                detab {^-^-a}               | "        a"
                detab #{0961}               | #{2020202061}
                entab #{2020202061}         | #{0961}
                detab {}                    | ""
                deline {a^M^/b}             | "a^/b"
                deline {a^Mb}               | "a^/b"
                deline {a^/^Mb}             | "a^/b"
                deline {}                   | ""
                deline/lines {a^/b^/}       | ["a" "b"]
                deline/lines {a^/b}         | ["a" "b"]
                deline/lines {}             | []
                deline/lines {^/}           | [""]
                deline/lines {a^M^/b^M^/}   | ["a" "b"]
                enline {a^M^/b}             | "a^/b"
                enline {}                   | ""
                as string! %a               | "a"
                as file! {a}                | %a
                as block! quote (1 2)       | [1 2]
                as paren! [1 2]             | (1 2)
                as path! [a b]              | a/b
                as {} %a                    | "a"
                as [] quote (1)             | [1]
                as tag! {a}                 | <a>
                as ref! {a}                 | @a
                """)
        void answersWhatRebolAnswers(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }
}
