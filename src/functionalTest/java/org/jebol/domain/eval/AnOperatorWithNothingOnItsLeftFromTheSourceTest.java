package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnOperatorWithNothingOnItsLeftFromTheSourceTest {

    private static final String ASKING = """
            set/any (quote e) try [%s]
            print mold/flat either error? :e [reduce [e/id e/arg1 type? e/arg1 e/arg2]] [reduce [(quote value) :e]]
            """;

    private static final String ASKING_WITH_NEAR = """
            set/any (quote e) try [%s]
            print mold/flat either error? :e [reduce [e/id e/arg1 type? e/arg1 e/arg2 e/near]] [reduce [(quote value) :e]]
            """;

    private static String answerTo(String asking, String source) {
        StringBuilder printed = new StringBuilder();
        OutputPort output = printed::append;
        AnInterpreter.createWithOutput(output).run(asking.formatted(source));
        return printed.toString().strip();
    }

    @Nested
    @DisplayName("at the head of its series")
    class AtTheHead {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                + 1                                    | [no-op-arg + #(word!) _]
                (+ 1)                                  | [no-op-arg + #(word!) _]
                1 (+ 1)                                | [no-op-arg + #(word!) _]
                [1] (+ 1)                              | [no-op-arg + #(word!) _]
                1 + (+ 1)                              | [no-op-arg + #(word!) _]
                <> 0                                   | [no-op-arg <> #(word!) _]
                +                                      | [no-op-arg + #(word!) _]
                if true [+ 1]                          | [no-op-arg + #(word!) _]
                reduce [+ 1]                           | [no-op-arg + #(word!) _]
                compose [(+ 1)]                        | [no-op-arg + #(word!) _]
                any [+ 1]                              | [no-op-arg + #(word!) _]
                case [true [+ 1]]                      | [no-op-arg + #(word!) _]
                do skip [+ 1] 0                        | [no-op-arg + #(word!) _]
                do head next [+ 1]                     | [no-op-arg + #(word!) _]
                f: func [a b] [reduce [a b]] f 4 (+ 2) | [no-op-arg + #(word!) _]
                do next [5 (<> 0)]                     | [no-op-arg <> #(word!) _]
                compose next [5 (<> 0)]                | [no-op-arg <> #(word!) _]
                f: function [] next [5 <> 0] f         | [no-op-arg <> #(word!) _]
                f: closure [] next [5 <> 0] f          | [no-op-arg <> #(word!) _]
                """)
        void raisesNoOpArgNamingTheWordAsWritten(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("further in, where its action refuses none")
    class FurtherIn {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                1 + + 3                                   | [cannot-use add: #(set-word!) #(none!) [+ 3]]
                1 + * 3                                   | [cannot-use multiply: #(set-word!) #(none!) [* 3]]
                1 + - 2                                   | [cannot-use subtract: #(set-word!) #(none!) [- 2]]
                x: + 1                                    | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                x: 1 + + 3                                | [cannot-use add: #(set-word!) #(none!) [+ 3]]
                x: 1 y: + 2                               | [cannot-use add: #(set-word!) #(none!) [+ 2]]
                a: object [b: 0] a/b: + 1                 | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                o: object [a: 1] o/a: + 1                 | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                print + 1                                 | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                negate + 1                                | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                first + 1                                 | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                if + 1 [2]                                | [cannot-use add: #(set-word!) #(none!) [+ 1 [2]]]
                f: func [a] [a] f + 1                     | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                f: func [a b] [reduce [a b]] f 1 + + 2    | [cannot-use add: #(set-word!) #(none!) [+ 2]]
                f: func [a /r b] [b] f/r 1 + + 2          | [cannot-use add: #(set-word!) #(none!) [+ 2]]
                f: func [a b c] [c + + 1] f 7 8 9         | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                f: func [a b] [a] f + 1 2                 | [cannot-use add: #(set-word!) #(none!) [+ 1 2]]
                1 = + 1                                   | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                1 and + 1                                 | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                1 ** + 2                                  | [cannot-use add: #(set-word!) #(none!) [+ 2]]
                1 <> 0 = + 1                              | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                1 + 2 + + 3                               | [cannot-use add: #(set-word!) #(none!) [+ 3]]
                reduce [1 + 1 + + 1]                      | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                all [1 + 1 + + 1]                         | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                o: object [a: 1] 1 + o/a + + 1            | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                do next [5 + 1]                           | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                do next [5 - 1]                           | [cannot-use subtract: #(set-word!) #(none!) [- 1]]
                do next [[a] + 1]                         | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                do at [1 2 + 3] 3                         | [cannot-use add: #(set-word!) #(none!) [+ 3]]
                do at [7 8 - 1] 3                         | [cannot-use subtract: #(set-word!) #(none!) [- 1]]
                b: next [9 + 1] do b                      | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                7 do next [5 + 1]                         | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                1 + do next [0 + 1]                       | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                reduce [1 2 do next [5 + 1]]              | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                f: func [a b c] [do next [5 + 1]] f 7 8 9 | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                (1 2) do next [5 + 1]                     | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                reduce next [5 + 1]                       | [cannot-use add: #(set-word!) #(none!) [+ 1]]
                do next [5 and 1]                         | [cannot-use and~: #(set-word!) #(none!) [and 1]]
                do next [5 or 1]                          | [cannot-use or~: #(set-word!) #(none!) [or 1]]
                do next [5 > 0]                           | [invalid-compare #(none!) #(datatype!) #(integer!) [> 0]]
                do next [5 * 0]                           | [cannot-use multiply: #(set-word!) #(none!) [* 0]]
                """)
        void runsWithNoneOnItsLeftAndIsLocatedAtItself(String source, String expected) {
            assertThat(answerTo(ASKING_WITH_NEAR, source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("further in, where its action takes none")
    class FurtherInWhereNoneIsFine {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                x: <> 0                 | [value #(true)]
                1 = <> 0                | [value #(false)]
                reduce [1 = <> 0]       | [value [#(false)]]
                f: func [a] [a] f <> 0  | [value #(true)]
                x: <> 0 = false         | [value #(false)]
                1 = <> 0 = false        | [value #(true)]
                do next [x: 5 <> 0]     | [value #(true)]
                all next [5 <> 0]       | [value #(true)]
                """)
        void answersWhatItsActionAnswersForNone(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("with no argument slot open, the block that ran it ends in missing-arg")
    class WithNoArgumentSlotOpen {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                do next [1 <> 0]                | [missing-arg _ #(none!) _ [do next [1 <> 0]]]
                do [x: <> 0]                    | [missing-arg _ #(none!) _ [do [x: <> 0]]]
                do [1 x: <> 0]                  | [missing-arg _ #(none!) _ [do [1 x: <> 0]]]
                do [x: y: <> 0]                 | [missing-arg _ #(none!) _ [do [x: y: <> 0]]]
                do [o: object [a: 1] o/a: <> 0] | [missing-arg _ #(none!) _ [do [o: object [a: 1] o/a: <> 0]]]
                do next [5 <> 0 7]              | [missing-arg _ #(none!) _ [do next [5 <> 0 7]]]
                do next [5 x: <> 0]             | [missing-arg _ #(none!) _ [do next [5 x: <> 0]]]
                do [x: <> 0 y: 3] y             | [missing-arg _ #(none!) _ [do [x: <> 0 y: 3] y]]
                if true [x: <> 0]               | [missing-arg _ #(none!) _ [if true [x: <> 0]]]
                if true next [5 <> 0]           | [missing-arg _ #(none!) _ [if true next [5 <> 0]]]
                either true next [5 <> 0] [2]   | [missing-arg _ #(none!) _ [either true next [5 <> 0] [2]]]
                loop 1 next [5 <> 0]            | [missing-arg _ #(none!) _ [loop 1 next [5 <> 0]]]
                until next [5 <> 0]             | [missing-arg _ #(none!) _ [until next [5 <> 0]]]
                forever next [5 <> 0]           | [missing-arg _ #(none!) _ [forever next [5 <> 0]]]
                case [true next [5 <> 0]]       | [missing-arg _ #(none!) _ [case [true next [5 <> 0]]]]
                catch next [5 <> 0]             | [missing-arg _ #(none!) _ [catch next [5 <> 0]]]
                compose [(x: <> 0)]             | [missing-arg _ #(none!) _ [compose [(x: <> 0)]]]
                f: does [x: <> 0] f             | [missing-arg _ #(none!) _ [f]]
                f: func [] [x: <> 0] f          | [missing-arg _ #(none!) _ [f]]
                f: func [] [x: <> 0 1] f        | [missing-arg _ #(none!) _ [f]]
                do next [5 <> 0 = false]        | [missing-arg _ #(none!) _ [do next [5 <> 0 = false]]]
                do next [5 = 0]                 | [missing-arg _ #(none!) _ [do next [5 = 0]]]
                do next [5 // 1]                | [missing-arg _ #(none!) _ [do next [5 // 1]]]
                do next [5 %% 1]                | [missing-arg _ #(none!) _ [do next [5 %% 1]]]
                do [x: // 1]                    | [missing-arg _ #(none!) _ [do [x: // 1]]]
                do next [5 <> 0] 9              | [missing-arg _ #(none!) _ [do next [5 <> 0] 9]]
                x: do next [5 <> 0]             | [missing-arg _ #(none!) _ [do next [5 <> 0]]]
                do next [5 <> 0 + 1]            | [expect-val #(logic!) #(datatype!) #(integer!) [+ 1]]
                do [x: <> 0 + 1]                | [expect-val #(logic!) #(datatype!) #(integer!) [+ 1]]
                while [false] next [5 <> 0]     | [value _]
                repeat i 1 next [5 <> 0]        | [no-op-arg <> #(word!) _ [repeat i 1 next [5 <> 0]]]
                foreach i [1] next [5 <> 0]     | [no-op-arg <> #(word!) _ [foreach i [1] next [5 <> 0]]]
                """)
        void raisesMissingArgOnceTheBlockEnds(String source, String expected) {
            assertThat(answerTo(ASKING_WITH_NEAR, source)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                x: <> 0                    | [value #(true)]
                try next [5 <> 0]          | [value #(true)]
                try next [5 <> 0 7]        | [value 7]
                attempt next [5 <> 0]      | [value #(true)]
                all next [5 <> 0]          | [value #(true)]
                any next [5 <> 0]          | [value #(true)]
                do/next next [5 <> 0] 'p   | [value #(true)]
                """)
        void answersWhereTheWalkerNeverChecks(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }

        @Test
        @DisplayName("the rest of the block runs before the error")
        void runsTheRestFirst() {
            assertThat(answerTo(ASKING, "do [x: <> 0 print 3]"))
                    .isEqualTo("3\n[missing-arg _ #(none!) _]");
        }
    }

    @Nested
    @DisplayName("where r3 reads a cell it does not own, decision 66")
    class WhereR3ReadsACellItDoesNotOwn {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                reduce next [5 <> 0 7]      | [value [#(true) 7]]
                reduce [x: <> 0]            | [value [#(true)]]
                reduce/into next [5 + 1] [] | [cannot-use add: #(set-word!) #(none!)]
                do/next [x: + 1] 'p         | [cannot-use add: #(set-word!) #(none!)]
                """)
        void answersAsTheOperatorDoesWithNone(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }

        @Test
        @DisplayName("print <> 0 prints true")
        void printsWhatTheOperatorAnswers() {
            assertThat(answerTo(ASKING, "print <> 0")).startsWith("true\n");
        }
    }

    @Nested
    @DisplayName("with nothing on its right either")
    class NothingOnTheRight {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                1 + + | [no-arg + #(word!) value2]
                1 +   | [no-arg + #(word!) value2]
                """)
        void raisesNoArgNamingTheInnerOperator(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("an operator reached as a real infix call")
    class RealInfix {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                do at [9 9 + 1] 2               | [value 10]
                set 'g :+ 10 g 1                | [value 11]
                set 'g :+ {s} g 1               | [cannot-use add: #(set-word!) #(string!)]
                set 'g :+ g 1                   | [cannot-use add: #(set-word!) #(op!)]
                f: func ['a] [a] f x + 2        | [cannot-use add: #(set-word!) #(word!)]
                f: func [:a] [a] f x + 2        | [cannot-use add: #(set-word!) #(word!)]
                quote + 1                       | [value 1]
                f: func [a /r b] [a] f/r 1 + 2  | [no-arg f #(word!) b]
                f: func [a b] [b] f 1 + 2 + 3   | [no-arg f #(word!) b]
                """)
        void answersAsItAlreadyDid(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("after an argument taken as written")
    class AfterAQuotedArgument {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                f: func ['a b] [b] f x + 2                   | [cannot-use add: #(set-word!) #(none!)]
                f: func [:a b] [b] f x + 2                   | [cannot-use add: #(set-word!) #(none!)]
                f: func [:a b] [reduce [a b]] f (5) + 2      | [cannot-use add: #(set-word!) #(none!)]
                f: func [:a b] [reduce [a b]] f :x + 2       | [cannot-use add: #(set-word!) #(none!)]
                f: func ['a b] [b] f (x) + 2                 | [no-value x #(word!) _]
                f: func ['a b] [b] x: 5 f :x + 2             | [no-arg f #(word!) b]
                f: func ['a b] [reduce [a b]] f (5) + 2      | [no-arg f #(word!) b]
                f: func ['a] [a] f (5) + 2                   | [value 7]
                f: func ['a] [a] x: 5 f :x + 2               | [value 7]
                f: func ['a] [a] o: object [x: 5] f :o/x + 2 | [value 7]
                f: func ['a b] [reduce [a b]] f x 2 + 3      | [value [x 5]]
                """)
        void looksAheadOnlyWhenTheArgumentWasEvaluated(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("do of a word that holds an operator")
    class DoOfAnOperatorWord {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                do quote +        | [invalid-type #(op!) #(datatype!) _]
                do quote + 1      | [invalid-type #(op!) #(datatype!) _]
                do '+             | [invalid-type #(op!) #(datatype!) _]
                do first [+]      | [invalid-type #(op!) #(datatype!) _]
                type? do quote +  | [invalid-type #(op!) #(datatype!) _]
                do quote and      | [invalid-type #(op!) #(datatype!) _]
                """)
        void refusesTheOperatorsType(String source, String expected) {
            assertThat(answerTo(ASKING, source)).isEqualTo(expected);
        }
    }
}
