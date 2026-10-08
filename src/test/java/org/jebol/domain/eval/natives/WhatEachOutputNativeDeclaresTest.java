package org.jebol.domain.eval.natives;

import org.jebol.application.Interpreter;
import org.jebol.domain.eval.OutputPort;
import org.jebol.domain.value.*;
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
class WhatEachOutputNativeDeclaresTest {

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.create().run(source).value());
    }

    private String argumentsOfTheErrorFrom(String source) {
        return answerTo("e: try [" + source + "] reduce [e/id e/arg1 e/arg2 e/arg3]");
    }

    private String writtenBy(String source) {
        StringBuilder written = new StringBuilder();
        OutputPort output = written::append;
        Interpreter.writingTo(output).run(source);
        return written.toString();
    }

    Stream<Arguments> eachNameAndItsRefinements() {
        return Stream.of(
                Arguments.of(new MoldNative(), "mold", Set.of("only", "all", "flat", "part")),
                Arguments.of(new FormNative(), "form", Set.of()),
                Arguments.of(new PrintNative(), "print", Set.of()),
                Arguments.of(new PrinNative(), "prin", Set.of()),
                Arguments.of(new QuitNative(), "quit", Set.of("return", "now")));
    }

    Stream<Arguments> eachNativeTakingOneValueOfAnyType() {
        return Stream.of(
                Arguments.of(new MoldNative()),
                Arguments.of(new FormNative()),
                Arguments.of(new PrintNative()),
                Arguments.of(new PrinNative()));
    }

    @ParameterizedTest(name = "{1} declares {2}")
    @MethodSource("eachNameAndItsRefinements")
    @DisplayName("each answers to the name boot/natives.reb gives it, with the refinements it declares")
    void answersToItsNameWithItsRefinements(DefaultNative definition, String name,
                                            Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @ParameterizedTest(name = "{0} takes value of any type, unset included")
    @MethodSource("eachNativeTakingOneValueOfAnyType")
    @DisplayName("mold, form, print and prin each take one value of any type")
    void takesOneValueOfAnyType(DefaultNative definition) {
        assertThat(definition.parametersAsWritten()).hasSize(definition instanceof MoldNative ? 2 : 1);
        Parameter value = definition.parametersAsWritten().getFirst();
        assertThat(value.name()).isEqualTo("value");
        assertThat(value.owningRefinement()).isEmpty();
        for (Datatype datatype : Typeset.ANY_TYPE.members()) {
            assertThat(value.accepts(datatype)).as(datatype.literalSpelling()).isTrue();
        }
        assertThat(value.accepts(Datatype.UNSET)).isTrue();
    }

    @Nested
    @DisplayName("each declaration matches Rebol's")
    class TheDeclarations {

        @Test
        @DisplayName("mold's limit belongs to /part and takes only an integer")
        void moldsLimitBelongsToPart() {
            List<Parameter> declared = new MoldNative().parametersAsWritten();
            assertThat(declared).extracting(Parameter::name).containsExactly("value", "limit");
            Parameter limit = declared.get(1);
            assertThat(limit.owningRefinement()).contains("part");
            assertThat(limit.accepts(Datatype.INTEGER)).isTrue();
            assertThat(limit.accepts(Datatype.DECIMAL)).isFalse();
            assertThat(limit.accepts(Datatype.PERCENT)).isFalse();
            assertThat(limit.accepts(Datatype.NONE)).isFalse();
        }

        @Test
        @DisplayName("quit's value belongs to /return")
        void quitsValueBelongsToReturn() {
            List<Parameter> declared = new QuitNative().parametersAsWritten();
            assertThat(declared).extracting(Parameter::name).containsExactly("value");
            assertThat(declared.getFirst().owningRefinement()).contains("return");
        }

        @ParameterizedTest(name = "spec-of :{0} is r3's")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                mold  | ["Converts a value to a REBOL-readable string." value [any-type!] "The value to mold" /only {For a block value, mold only its contents, no outer []} /all "Use construction syntax" /flat "No indentation" /part "Limit the length of the result" limit [integer!]]
                form  | ["Converts a value to a human-readable string." value [any-type!] "The value to form"]
                print | ["Outputs a value followed by a line break." value [any-type!] "The value to print"]
                prin  | ["Outputs a value with no line break." value [any-type!]]
                quit  | ["Stops evaluation and exits the interpreter." /return "Returns a value (to prior script or command shell)" value "Note: use integers for command shell" /now "Quit immediately"]
                """)
        void specOfShowsRebolsSpec(String name, String spec) {
            assertThat(answerTo("spec-of :" + name)).isEqualTo(spec);
        }

        @ParameterizedTest(name = "{0} is a native")
        @CsvSource({"mold", "form", "print", "prin", "quit"})
        void eachIsANative(String name) {
            assertThat(answerTo("type? :" + name)).isEqualTo("#(native!)");
        }

        @Test
        @DisplayName("make-error is not a word Rebol has, and JEBOL no longer has it either")
        void makeErrorIsGone() {
            assertThat(argumentsOfTheErrorFrom("make-error 1 2"))
                    .isEqualTo("[no-value make-error _ _]");
        }
    }

    @Nested
    @DisplayName("mold answers what r3 answers")
    class Molding {

        @ParameterizedTest(name = "{0} answers {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                mold []                                        | "[]"
                mold ""                                        | {""}
                mold #(unset)                                  | "#(unset)"
                mold none                                      | "_"
                mold/only [a [b] "c"]                          | {a [b] "c"}
                mold/only "a"                                  | {"a"}
                mold/only quote (a b)                          | "(a b)"
                mold/only make hash! [a 1]                     | "make hash! [a 1]"
                mold/all next [a b]                            | "#(block! [a b] 2)"
                mold/all [a b]                                 | "[a b]"
                mold/flat new-line [a b] true                  | "[a b]"
                mold new-line [a b] true                       | "[^/    a b^/]"
                system/options/binary-base: 2 mold #{05}       | "2#{00000101}"
                system/options/binary-base: 64 mold #{05}      | "64#{BQ==}"
                system/options/binary-base: 16 mold #{05}      | "#{05}"
                system/options/binary-base: none mold #{05}    | "#{05}"
                system/options/binary-base: 3 mold #{05}       | "#{05}"
                system/options/binary-base: 8 mold #{05}       | "#{05}"
                system/options/binary-base: "2" mold #{05}     | "#{05}"
                system/options/binary-base: 2.0 mold #{05}     | "#{05}"
                system/options/binary-base: 2 mold/all #{05}   | "2#{00000101}"
                system/options/binary-base: 2 mold/flat #{05}  | "2#{00000101}"
                system/options/binary-base: 2 mold/part #{05} 4 | "2#{0"
                """)
        void answersAsRebolDoes(String source, String answer) {
            assertThat(answerTo(source)).isEqualTo(answer);
        }

        @ParameterizedTest(name = "mold/part {0} answers {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                "abc" -9223372036854775808       | ""
                "abc" -1                         | ""
                "abc" 0                          | ""
                "abc" 1                          | {"}
                "abc" 4                          | {"abc}
                "abc" 5                          | {"abc"}
                "abc" 6                          | {"abc"}
                "abc" 2147483647                 | {"abc"}
                "abc" 2147483648                 | {"abc"}
                "abc" 4294967295                 | {"abc"}
                "abc" 4294967296                 | {"abc"}
                "abc" 9223372036854775807        | {"abc"}
                [a b c] 3                        | "[a "
                /only [a b c] 3                  | "a b"
                /all next "abc" 8                | "#(string"
                """)
        void clampsItsLimit(String arguments, String answer) {
            String source = arguments.startsWith("/")
                    ? "mold/part" + arguments
                    : "mold/part " + arguments;
            assertThat(answerTo(source)).isEqualTo(answer);
        }

        @Test
        @DisplayName("the string the limits are measured against molds to five characters")
        void theMeasuredStringMoldsToFiveCharacters() {
            assertThat(answerTo("length? mold {abc}")).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("form answers what r3 answers")
    class Forming {

        @ParameterizedTest(name = "{0} answers {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                form quote (1 2)              | "1 2"
                form [(1 2) [3]]              | "1 2 3"
                form [[(1 2)] 3]              | "1 2 3"
                form [a [(b [c])]]            | "a b c"
                form quote (a (b) [c])        | "a b c"
                form [1 ()]                   | "1 "
                form [() 1]                   | " 1"
                form quote ()                 | ""
                form next quote (1 2)         | "2"
                form []                       | ""
                form ""                       | ""
                form none                     | "none"
                form #(unset)                 | ""
                form [a "b" #"c"]             | "a b c"
                form make hash! [a 1]         | "a 1"
                form 'a/b                     | "a/b"
                form first [a/b:]             | "a/b"
                form first [:a/b]             | "a/b"
                form quote 'a/b               | "a/b"
                form [a/b: :c/d]              | "a/b c/d"
                b: [1 2] append/only b b form b | "1 2 [...]"
                b: [1 2] append/only b b mold b | "[1 2 [...]]"
                mold first [a/b:]             | "a/b:"
                mold first [:a/b]             | ":a/b"
                mold quote 'a/b               | "'a/b"
                """)
        void answersAsRebolDoes(String source, String answer) {
            assertThat(answerTo(source)).isEqualTo(answer);
        }
    }

    @Nested
    @DisplayName("print and prin reduce a block and nothing else, then write what form gives")
    class Printing {

        @ParameterizedTest(name = "{0} writes {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                print [1 + 2 "x"]                     | `3 x`
                print [[1 2] "x"]                     | `1 2 x`
                print [a: 1]                          | `1`
                print "a"                             | `a`
                print none                            | `none`
                print 'a/b                            | `a/b`
                print first [a/b:]                    | `a/b`
                print first [:a/b]                    | `a/b`
                print quote 'a/b                      | `a/b`
                print [quote 'a/b]                    | `a/b`
                print quote (1 + 2)                   | `1 + 2`
                print [quote (1 2)]                   | `1 2`
                print make hash! [a 1]                | `a 1`
                f: func [a] [print [a "x"]] f 1       | `1 x`
                prin "a"                              | `a`
                prin [1 + 2 "x"]                      | `3 x`
                prin 'a/b                             | `a/b`
                prin quote (1 + 2)                    | `1 + 2`
                prin [quote (1 2)]                    | `1 2`
                prin make hash! [a 1]                 | `a 1`
                prin make object! [a: 1 b: [2 3]]     | `a: 1^/b: [2 3]`
                """)
        void writesWhatFormGives(String source, String written) {
            String expected = written.replace("^/", "\n");
            String lineEnding = source.startsWith("print") || source.startsWith("f:") ? "\n" : "";
            assertThat(writtenBy(source)).isEqualTo(expected + lineEnding);
        }

        @ParameterizedTest(name = "{0} writes only a line break")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                print []
                print [()]
                print #(unset)
                """)
        void writesOnlyALineBreak(String source) {
            assertThat(writtenBy(source)).isEqualTo("\n");
        }

        @ParameterizedTest(name = "{0} writes nothing")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                prin []
                prin #(unset)
                prin ""
                """)
        void writesNothing(String source) {
            assertThat(writtenBy(source)).isEmpty();
        }

        @ParameterizedTest(name = "{0} answers unset")
        @CsvSource({"print 1", "prin 1"})
        void answersUnset(String source) {
            assertThat(answerTo("type? " + source)).isEqualTo("#(unset!)");
        }
    }

    @Nested
    @DisplayName("quit carries what r3's quit carries")
    class Quitting {

        @ParameterizedTest(name = "catch/quit [{0}] answers {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                quit/return 7           | 7
                quit/return 0           | 0
                quit/return false       | #(false)
                quit/return true        | #(true)
                quit/return "a"         | "a"
                quit/return ""          | ""
                quit/return [1]         | [1]
                quit/now/return 3       | 3
                quit/return none        | #(unset)
                quit                    | #(unset)
                quit/now                | #(unset)
                """)
        void carriesTheValue(String quitting, String answer) {
            assertThat(answerTo("catch/quit [" + quitting + "]")).isEqualTo(answer);
        }

        @ParameterizedTest(name = "{0} is refused as {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                catch/quit [quit/return #(unset)]    | [expect-arg quit value #(unset!)]
                catch/quit [quit/return]             | [no-arg quit value _]
                quit/now/x                           | [no-refine quit x _]
                """)
        void refusesWithRebolsArguments(String source, String arguments) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(arguments);
        }
    }

    @Nested
    @DisplayName("what the output natives refuse, they refuse with the arguments r3 names")
    class Refusals {

        @ParameterizedTest(name = "{0} is refused as {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                mold                         | [no-arg mold value _]
                form                         | [no-arg form value _]
                print                        | [no-arg print value _]
                prin                         | [no-arg prin value _]
                mold/part "a"                | [no-arg mold limit _]
                mold/part "a" 2.5            | [expect-arg mold limit #(decimal!)]
                mold/part "a" "2"            | [expect-arg mold limit #(string!)]
                mold/part "a" none           | [expect-arg mold limit #(none!)]
                mold/part "a" [2]            | [expect-arg mold limit #(block!)]
                mold/part "a" true           | [expect-arg mold limit #(logic!)]
                mold/part "a" 2%             | [expect-arg mold limit #(percent!)]
                mold/x 1                     | [no-refine mold x _]
                form/only 1                  | [no-refine form only _]
                print/x 1                    | [no-refine print x _]
                prin/x 1                     | [no-refine prin x _]
                print [1 / 0]                | [zero-divide _ _ _]
                print [no-such-word-here]    | [no-value no-such-word-here _ _]
                prin [no-such-word-here]     | [no-value no-such-word-here _ _]
                """)
        void refusesWithRebolsArguments(String source, String arguments) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(arguments);
        }
    }
}
