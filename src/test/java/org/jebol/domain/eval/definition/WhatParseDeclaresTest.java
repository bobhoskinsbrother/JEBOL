package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatParseDeclaresTest {

    private final ParseNative parse = new ParseNative();

    private String answerTo(String source) {
        return Molder.moldFlat(Interpreter.create().run(source).value());
    }

    private String argumentsOfTheErrorFrom(String source) {
        return answerTo("e: try [" + source + "] reduce [e/id e/arg1 e/arg2 e/arg3]");
    }

    @Nested
    @DisplayName("the declaration matches Rebol's")
    class TheDeclaration {

        @Test
        @DisplayName("it answers to parse, with /case its only refinement")
        void answersToItsNameWithOnlyCase() {
            assertThat(parse.nativeName()).isEqualTo("parse");
            assertThat(parse.refinementsDeclaredApart()).isEqualTo(Set.of("case"));
        }

        @Test
        @DisplayName("input takes every series and rules takes only a block")
        void inputTakesSeriesAndRulesTakesABlock() {
            assertThat(parse.parametersAsWritten()).extracting(Parameter::name)
                    .containsExactly("input", "rules");
            assertThat(parse.parametersAsWritten().getFirst().accepts(Datatype.STRING)).isTrue();
            assertThat(parse.parametersAsWritten().getFirst().accepts(Datatype.BLOCK)).isTrue();
            assertThat(parse.parametersAsWritten().getFirst().accepts(Datatype.ISSUE)).isFalse();
            assertThat(parse.parametersAsWritten().get(1).accepts(Datatype.BLOCK)).isTrue();
            assertThat(parse.parametersAsWritten().get(1).accepts(Datatype.PAREN)).isFalse();
            assertThat(parse.parametersAsWritten().get(1).accepts(Datatype.STRING)).isFalse();
        }

        @Test
        @DisplayName("spec-of shows the spec r3 shows")
        void specOfShowsRebolsSpec() {
            assertThat(answerTo("spec-of :parse")).isEqualTo("""
                    [{Parses a string or block series according to grammar rules.} \
                    input [series!] "Input series to parse" \
                    rules [block!] "Rules to parse" \
                    /case "Uses case-sensitive comparison"]""");
        }
    }

    @Nested
    @DisplayName("every kind of series is parsed")
    class EverySeries {

        @ParameterizedTest(name = "{0} answers {1}")
        @CsvSource(delimiter = '|', textBlock = """
                parse "abc" ["abc"]                   | #(true)
                parse #{0102} [#{0102}]               | #(true)
                parse [a b] ['a 'b]                   | #(true)
                parse %abc ["abc"]                    | #(true)
                parse <abc> ["abc"]                   | #(true)
                parse 'a/b ['a 'b]                    | #(true)
                parse quote (a b) ['a 'b]             | #(true)
                parse email@x.com [thru "@" to end]   | #(true)
                parse http://a.b [to end]             | #(true)
                parse [1 2] [integer! integer!]       | #(true)
                parse "" []                           | #(true)
                parse [] []                           | #(true)
                parse tail "ab" []                    | #(true)
                parse next "ab" ["b"]                 | #(true)
                parse "a" []                          | #(false)
                parse "ab" [skip]                     | #(false)
                parse "a" [end "a"]                   | #(false)
                parse "abc" [return "x"]              | #(false)
                parse "abc" [some]                    | #(false)
                parse "abc" [accept]                  | #(false)
                parse "abc" [reject]                  | #(false)
                parse "abc" [break]                   | #(false)
                """)
        void answersAsRebolDoes(String source, String answer) {
            assertThat(answerTo(source)).isEqualTo(answer);
        }
    }

    @Nested
    @DisplayName("/case decides whether letters must match in case")
    class MindingCase {

        @ParameterizedTest(name = "{0} answers {1}")
        @CsvSource(delimiter = '|', textBlock = """
                parse "ABC" ["abc"]         | #(true)
                parse/case "ABC" ["abc"]    | #(false)
                parse/case "abc" ["abc"]    | #(true)
                parse/case "abc" ["ABC"]    | #(false)
                case: true parse/:case "A" ["a"]    | #(false)
                case: false parse/:case "A" ["a"]   | #(true)
                """)
        void caseIsMindedOnlyWhenAsked(String source, String answer) {
            assertThat(answerTo(source)).isEqualTo(answer);
        }
    }

    @Nested
    @DisplayName("what parse refuses, it refuses with the arguments r3 names")
    class Refusals {

        @ParameterizedTest(name = "{0} is refused as {1}")
        @CsvSource(delimiter = '|', textBlock = """
                parse 1 ["a"]                 | [expect-arg parse input #(integer!)]
                parse none ["a"]              | [expect-arg parse input #(none!)]
                parse 2x3 []                  | [expect-arg parse input #(pair!)]
                parse #issue ["issue"]        | [expect-arg parse input #(issue!)]
                parse make bitset! #{00} []   | [expect-arg parse input #(bitset!)]
                parse "abc" "abc"             | [expect-arg parse rules #(string!)]
                parse "abc" 1                 | [expect-arg parse rules #(integer!)]
                parse "abc" none              | [expect-arg parse rules #(none!)]
                parse "abc" #"a"              | [expect-arg parse rules #(char!)]
                parse "abc" 'word             | [expect-arg parse rules #(word!)]
                parse "a" first [(["a"])]     | [expect-arg parse rules #(paren!)]
                parse "abc" [foo]             | [parse-rule foo _ _]
                parse [a] [(1 / 0)]           | [zero-divide _ _ _]
                """)
        void refusesWithRebolsArguments(String source, String arguments) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(arguments);
        }
    }

    @Nested
    @DisplayName("an undeclared refinement names the word that called and the refinement as written")
    class UndeclaredRefinements {

        @ParameterizedTest(name = "{0} is refused as {1}")
        @CsvSource(delimiter = '|', textBlock = """
                parse/All "a" []                          | [no-refine parse All _]
                parse/all "a" []                          | [no-refine parse all _]
                parse/case/x "a" []                       | [no-refine parse x _]
                h: :parse h/x "a" []                      | [no-refine h x _]
                f: func [/a] [1] f/x                      | [no-refine f x _]
                f: func [/a] [1] f/a/X                    | [no-refine f X _]
                o: object [g: func [] [1]] o/g/x          | [no-refine g x _]
                r: true parse/:r "a" []                   | [no-refine parse :r _]
                r: false parse/:r "a" []                  | [no-refine parse :r _]
                r: none parse/:r "a" []                   | [no-refine parse :r _]
                """)
        void namesTheCallerAndTheRefinement(String source, String arguments) {
            assertThat(argumentsOfTheErrorFrom(source)).isEqualTo(arguments);
        }

        @Test
        @DisplayName("both arguments of a plain refinement are words, not prose")
        void bothArgumentsAreWords() {
            assertThat(answerTo("""
                    e: try [parse/All "a" []] reduce [type? e/arg1 type? e/arg2]"""))
                    .isEqualTo("[#(word!) #(word!)]");
        }
    }
}
