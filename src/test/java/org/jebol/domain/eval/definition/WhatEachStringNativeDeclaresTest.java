package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.UnicodeCases;
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
class WhatEachStringNativeDeclaresTest {

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.create().run(source).value());
    }

    private String errorIdOf(String source) {
        return answerTo("select try [" + source + "] 'id");
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        return Stream.of(
                Arguments.of(new FindScriptNative(), "find-script", Set.of()),
                Arguments.of(new SplitLinesNative(), "split-lines", Set.of()),
                Arguments.of(new IsWildcardNative(), "wildcard?", Set.of()),
                Arguments.of(new UppercaseNative(), "uppercase", Set.of("part")),
                Arguments.of(new LowercaseNative(), "lowercase", Set.of("part")),
                Arguments.of(new TrimAction(), "trim",
                        Set.of("head", "tail", "auto", "lines", "all", "with")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to its Rebol name with the refinements Rebol declares")
    void declaresItsNameAndRefinements(NativeDefinition definition, String name,
            Set<String> refinements) {
        assertThat(definition.name()).isEqualTo(name);
        assertThat(definition.refinements()).isEqualTo(refinements);
    }

    @ParameterizedTest(name = "{0} is refused with {1}")
    @CsvSource(delimiter = '|', value = {
            "uppercase 1                      | expect-arg",
            "lowercase [a]                    | expect-arg",
            "uppercase/part {abcd} 1x2        | expect-arg",
            "uppercase/part {abcd} $2         | expect-arg",
            "uppercase/part {abcd} none       | expect-arg",
            "uppercase/part {abcd} 50%        | invalid-part",
            "uppercase/part {abcd} {ab}       | invalid-part",
            "split-lines %a                   | expect-arg",
            "split-lines #{00}                | expect-arg",
            "wildcard? {a*}                   | expect-arg",
            "find-script {rebol []}           | expect-arg",
            "trim 1                           | expect-arg",
            "trim make vector! [integer! 8 2] | cannot-use",
            "trim make image! 1x1             | cannot-use"})
    void refusesWhatItWasNotDeclaredToTake(String source, String id) {
        assertThat(errorIdOf(source)).isEqualTo(id);
    }

    @Nested
    @DisplayName("uppercase and lowercase change case through Rebol's own table")
    class ChangingCase {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                uppercase {abc}                       | "ABC"
                uppercase {}                          | ""
                lowercase {ABC}                       | "abc"
                lowercase %ABC                        | %abc
                uppercase {straße}                    | "STRAßE"
                head uppercase next {abc}             | "aBC"
                """)
        void changesTheText(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "to integer! uppercase to char! 97     | 65",
                "to integer! uppercase to char! 49     | 49",
                "to integer! lowercase to char! 937    | 969",
                "to integer! uppercase to char! 223    | 223",
                "to integer! uppercase to char! 953    | 837",
                "to integer! uppercase to char! 255    | 376",
                "to integer! lowercase to char! 304    | 304",
                "to integer! first uppercase to string! to char! 454 | 452",
                "char? uppercase to char! 97           | #(true)"})
        void changesACharacterOneToOne(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                uppercase/part {abcd} 2                    | "ABcd"
                uppercase/part {abcd} 0                    | "abcd"
                uppercase/part {abcd} 1                    | "Abcd"
                uppercase/part {abcd} 4                    | "ABCD"
                uppercase/part {abcd} 10                   | "ABCD"
                uppercase/part {abcd} 2.7                  | "ABcd"
                uppercase/part {abcd} -1                   | "abcd"
                uppercase/part {abcd} -2.5                 | "abcd"
                head uppercase/part skip {abcd} 2 -1       | "aBcd"
                head uppercase/part skip {abcd} 2 -2       | "ABcd"
                head uppercase/part skip {abcd} 2 -5       | "ABcd"
                head uppercase/part skip {abcd} 3 -10      | "ABCd"
                lowercase/part {ABCD} 1                    | "aBCD"
                s: {abcd} head uppercase/part s skip s 3   | "ABCd"
                s: {abcd} head uppercase/part skip s 3 s   | "ABCd"
                """)
        void partLimitsHowMuchChanges(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }
    }

    @Nested
    @DisplayName("Rebol's case table, ported from s-cases.c")
    class TheCaseTable {

        @ParameterizedTest(name = "{0} goes up to {1} and {1} comes down to {2}")
        @CsvSource({
                "0x0061, 0x0041, 0x0061",
                "0x007A, 0x005A, 0x007A",
                "0x00FF, 0x0178, 0x00FF",
                "0x00DF, 0x00DF, 0x00DF",
                "0x03C9, 0x03A9, 0x03C9",
                "0x01C6, 0x01C4, 0x01C6",
                "0x2CE3, 0x2CE2, 0x2CE3",
                "0x2D00, 0x10A0, 0x2D00"})
        void mapsOneToOne(int small, int capital, int smallAgain) {
            assertThat(UnicodeCases.TABLES.upper(small)).isEqualTo(capital);
            assertThat(UnicodeCases.TABLES.lower(capital)).isEqualTo(smallAgain);
        }

        @Test
        @DisplayName("when two capitals share a small letter, the first one in the table wins going up")
        void theFirstMappingWinsGoingUp() {
            assertThat(UnicodeCases.TABLES.upper(0x03B9)).isEqualTo(0x0345);
            assertThat(UnicodeCases.TABLES.lower(0x0399)).isEqualTo(0x03B9);
            assertThat(UnicodeCases.TABLES.lower(0x1FBE)).isEqualTo(0x03B9);
            assertThat(UnicodeCases.TABLES.upper(0x0073)).isEqualTo(0x0053);
            assertThat(UnicodeCases.TABLES.lower(0x017F)).isEqualTo(0x0073);
            assertThat(UnicodeCases.TABLES.lower(0x212A)).isEqualTo(0x006B);
        }

        @ParameterizedTest(name = "{0} is left alone")
        @CsvSource({"-1", "0", "0x0030", "0x0130", "0x2DFF", "0x2E00", "0xFF21", "0x1F600"})
        void leavesAloneWhatItDoesNotMap(int codepoint) {
            assertThat(UnicodeCases.TABLES.upper(codepoint)).isEqualTo(codepoint);
            assertThat(UnicodeCases.TABLES.lower(codepoint)).isEqualTo(codepoint);
        }

        @Test
        @DisplayName("the table ends where Rebol's does, at 2E00")
        void theTableEndsWhereRebolsDoes() {
            assertThat(UnicodeCases.TABLE_SIZE).isEqualTo(0x2E00);
        }
    }

    @Nested
    @DisplayName("trim")
    class Trimming {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                trim {  a b  }                       | "a b"
                trim/head {  a  }                    | "a  "
                trim/tail {  a  }                    | "  a"
                trim/head/tail {  a  }               | "a"
                trim/all { a b }                     | "ab"
                trim/lines {  a ^/  b  }             | "a b"
                trim/auto {  a^/  b}                 | "a^/b"
                trim/with {abca} {a}                 | "bc"
                trim/with {abca} #{61}               | "bc"
                trim/with {abca} first {a}           | "bc"
                trim/with {abc} {}                   | "abc"
                trim {  a  ^/  b  ^/}                | "a^/b^/"
                trim {}                              | ""
                s: {  a  } trim s s                  | "a"
                s: { x  a  } trim next s head s      | " x  a"
                """)
        void trimsText(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "trim reduce [none 1 none]            | [1]",
                "trim/head reduce [none 1 none]       | [1 _]",
                "trim/tail reduce [none 1 none]       | [_ 1]",
                "trim/head/tail reduce [none 1 none]  | [1]",
                "trim/tail/head reduce [none 1 none]  | [1]",
                "trim/all reduce [none 1 none 2 none] | [1 2]",
                "trim []                              | []",
                "trim #{0001000200}                   | #{010002}",
                "trim/head #{000100}                  | #{0100}",
                "trim/tail #{000100}                  | #{0001}",
                "trim/all #{0001000200}               | #{0102}",
                "trim #{}                             | #{}"})
        void trimsBlocksAndBinaries(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "words-of trim make object! [a: none b: 1]  | [b]",
                "values-of trim make object! [a: none b: 1] | [1]",
                "words-of trim make object! []              | []"})
        void trimsAnObjectOfTheFieldsHoldingNothing(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "trim/head make object! [a: 1]",
                "trim/head/all {a}",
                "trim/tail/with {a} {a}",
                "trim/with [1] 1",
                "trim/lines #{00}",
                "trim/auto [1]",
                "trim/with #{00} 0"})
        void refusesRefinementsThatContradict(String source) {
            assertThat(errorIdOf(source)).isEqualTo("bad-refines");
            assertThat(answerTo("none? select try [" + source + "] 'arg1")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("split-lines walks the text the way the C does")
    class SplittingLines {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                split-lines {a^/b}              | ["a" "b"]
                split-lines {}                  | []
                split-lines {a}                 | ["a"]
                split-lines {a^M^/b}            | ["a" "b"]
                split-lines {a^Mb}              | ["a" "b"]
                split-lines {a^M}               | ["a"]
                split-lines {a^/}               | ["a"]
                split-lines {a^/^/}             | ["a" ""]
                split-lines {^/}                | [""]
                split-lines {^/^/}              | ["" ""]
                split-lines {a^M^Mb}            | ["a" "" "b"]
                split-lines next {ab^/cd}       | ["b" "c"]
                split-lines skip {ab^/cd} 3     | []
                """)
        void splitsOnEveryLineEnding(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @Test
        @DisplayName("each line starts a new line in the block")
        void eachLineStartsANewLine() {
            assertThat(answerTo("""
                    b: split-lines {a^/b} reduce [new-line? b new-line? next b]""")).isEqualTo(
                    "[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("wildcard? and find-script")
    class LookingInside {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "wildcard? %a*         | #(true)",
                "wildcard? %a?         | #(true)",
                "wildcard? %*          | #(true)",
                "wildcard? %abc        | #(false)",
                "wildcard? to file! {} | #(false)"})
        void wildcardLooksForAStarOrAQuestionMark(String source, String wanted) {
            assertThat(answerTo(source)).isEqualTo(wanted);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "find-script #{}                          | none",
                "find-script to-binary {rebol []}         | 1",
                "find-script to-binary {junk^/REBOL [] 1} | 6",
                "find-script to-binary {  rebol  [ ]}     | 3",
                "find-script to-binary {x rebol []}       | none",
                "find-script to-binary {rebol}            | none",
                "find-script to-binary {rebol 1}          | none"})
        void findScriptAnswersWhereTheHeaderStarts(String source, String wanted) {
            assertThat(answerTo("either none? s: " + source + " ['none] [index? s]")).isEqualTo(wanted);
        }
    }
}
