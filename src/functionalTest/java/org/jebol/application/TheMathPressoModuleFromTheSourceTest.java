package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheMathPressoModuleFromTheSourceTest {

    private static final String IMPORTED = """
            m: import 'mathpresso
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String printedBy(String source) {
        StringBuilder printed = new StringBuilder();
        Interpreter interpreter = AnInterpreter.createWithOutput(printed::append);
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return printed.toString();
    }

    @Nested
    @DisplayName("importing it")
    class Importing {

        @Test
        @DisplayName("answers the module, which the table then holds")
        void answersTheModule() {
            assertThat(answerTo("""
                    before: type? select system/modules 'mathpresso
                    m: import 'mathpresso
                    reduce [before module? m same? m system/modules/mathpresso]""")).isEqualTo("""
                    [#(block!) #(true) #(true)]""");
        }

        @Test
        @DisplayName("gives the module its three commands, hides WORDS as its script does, and exports nothing")
        void exportsNothing() {
            assertThat(answerTo(IMPORTED + """
                    reduce [words-of m same? :lib/context :m/context native? :context]""")).isEqualTo("""
                    [[lib-base lib-file lib-local context compile eval] #(false) #(true)]""");
        }

        @Test
        @DisplayName("carries the extension's own header and specs")
        void carriesTheSpecs() {
            assertThat(answerTo(IMPORTED + """
                    reduce [select spec-of m 'title select spec-of m 'version spec-of :m/eval]""")).isEqualTo("""
                    ["Rebol Mathpresso Extension" 0.1.0 [{Evaluate precompiled math expressions using given variables} \
                    expression [handle!] "MPExpression" variables [vector!] "Variables in a double format"]]""");
        }
    }

    @Nested
    @DisplayName("CONTEXT")
    class MakingAContext {

        @Test
        @DisplayName("answers an MPContext handle")
        void answersAHandle() {
            assertThat(answerTo(IMPORTED + """
                    reduce [mold m/context [x y]]""")).isEqualTo("""
                    ["#(handle! MPContext)"]""");
        }

        @Test
        @DisplayName("takes only words, so a set-word, a lit-word, a string and an issue take no place in the vector")
        void takesOnlyWords() {
            assertThat(answerTo(IMPORTED + """
                    c: m/context [x 'y z: {s} #w w]
                    reduce [m/eval m/compile c {w} #(double! [1 2]) m/compile c {y}]""")).isEqualTo("""
                    [2.0 _]""");
        }

        @Test
        @DisplayName("reads the block from its index")
        void readsFromTheIndex() {
            assertThat(answerTo(IMPORTED + """
                    c: m/context next [x y]
                    reduce [m/eval m/compile c {y} #(double! [5]) m/compile c {x}]""")).isEqualTo("""
                    [5.0 _]""");
        }

        @Test
        @DisplayName("names a variable by its word's canonical spelling, the first spelling Rebol saw")
        void namesByTheCanonicalSpelling() {
            assertThat(answerTo(IMPORTED + """
                    c: m/context [X FreshMathpressoWord]
                    reduce [
                        m/eval m/compile c {x} #(double! [3 4])
                        m/compile c {X}
                        m/eval m/compile c {FreshMathpressoWord} #(double! [3 4])
                    ]""")).isEqualTo("""
                    [3.0 _ 4.0]""");
        }
    }

    @Nested
    @DisplayName("COMPILE")
    class Compiling {

        @Test
        @DisplayName("answers an MPExpression handle")
        void answersAHandle() {
            assertThat(answerTo(IMPORTED + """
                    reduce [mold m/compile m/context [x] {x + 1}]""")).isEqualTo("""
                    ["#(handle! MPExpression)"]""");
        }

        @Test
        @DisplayName("answers none for an expression MathPresso refuses, and prints its message as the C prints it")
        void printsTheRefusal() {
            assertThat(answerTo(IMPORTED + """
                    m/compile m/context [x] {x @ 1}""")).isEqualTo("_");
            assertThat(printedBy(IMPORTED + """
                    m/compile m/context [x] {x @ 1}""")).isEqualTo("ERROR: Unexpected token." + System.lineSeparator());
        }

        @Test
        @DisplayName("prints a warning and still compiles, because the extension asks MathPresso to be verbose")
        void printsAWarning() {
            assertThat(printedBy(IMPORTED + """
                    m/compile m/context [x] {var x = 2; x}""")).isEqualTo(
                    "WARNING: Variable 'x' shadows a variable of the same name." + System.lineSeparator());
            assertThat(answerTo(IMPORTED + """
                    m/eval m/compile m/context [x] {var x = 2; x} #(double! [9])""")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("answers none and prints nothing for an empty expression")
        void refusesNothingQuietly() {
            assertThat(answerTo(IMPORTED + """
                    m/compile m/context [x] {   }""")).isEqualTo("_");
            assertThat(printedBy(IMPORTED + """
                    m/compile m/context [x] {// only a comment}""")).isEmpty();
        }

        @Test
        @DisplayName("answers false for a handle that is not a context")
        void refusesAnotherHandle() {
            assertThat(answerTo(IMPORTED + """
                    c: m/context [x]
                    reduce [m/compile m/compile c {1} {1} m/compile rc4/key #{00} {1}]""")).isEqualTo("""
                    [#(false) #(false)]""");
        }

        @Test
        @DisplayName("reads the string from its head, as SERIES_DATA does, and stops at a zero, as strlen does")
        void readsFromTheHead() {
            assertThat(answerTo(IMPORTED + """
                    c: m/context [x]
                    reduce [
                        m/eval m/compile c next {12} #(double! [0])
                        m/eval m/compile c {1 + 2^(00)junk} #(double! [0])
                    ]""")).isEqualTo("""
                    [12.0 3.0]""");
        }

        @Test
        @DisplayName("refuses a character past ASCII whether the string is stored narrow or wide")
        void refusesPastAscii() {
            assertThat(answerTo(IMPORTED + """
                    c: m/context [x]
                    reduce [m/compile c {x + é} m/compile c {x + ☺}]""")).isEqualTo("""
                    [_ _]""");
        }
    }

    @Nested
    @DisplayName("EVAL")
    class Evaluating {

        @Test
        @DisplayName("runs the extension's own CI script and answers what the C answers, all sixty-two of them, closely")
        void runsTheCiScript() {
            assertThat(answerTo(IMPORTED + """
                    close?: func [got want] [
                        if (length? got) <> length? want [return false]
                        repeat i length? want [if 1e-9 < abs got/:i - want/:i [return false]]
                        true
                    ]
                    ctx: m/context [x y step amplitude result]
                    expr: m/compile :ctx "y=sin(x); x=x+step; result=round(y*amplitude)/100"
                    data: #(double! [0 0 0 10000 0])
                    data/3: pi / 30
                    first-run: collect [loop 31 [keep m/eval :expr :data]]
                    after-first: collect [repeat i 5 [keep data/:i]]
                    expr2: m/compile :ctx {
                        y=sin(x)+cos(x/2);
                        x=x+step;
                        result=round(y*amplitude)/100
                    }
                    second-run: collect [loop 31 [keep m/eval :expr2 :data]]
                    after-second: collect [repeat i 5 [keep data/:i]]
                    reduce [
                        close? first-run [
                            0.0 10.45 20.79 30.9 40.67 50.0 58.78 66.91 74.31 80.9 86.6 91.35 95.11
                            97.81 99.45 100.0 99.45 97.81 95.11 91.35 86.6 80.9 74.31 66.91 58.78
                            50.0 40.67 30.9 20.79 10.45 0.0
                        ]
                        close? after-first [3.246312408709454 0.0 0.10471975511965977 10000.0 0.0]
                        close? second-run [
                            -15.69 -31.24 -46.55 -61.46 -75.88 -89.68 -102.75 -114.99 -126.3 -136.6
                            -145.82 -153.88 -160.75 -166.37 -170.71 -173.77 -175.53 -176.01 -175.22
                            -173.21 -170.0 -165.67 -160.27 -153.88 -146.59 -138.49 -129.67 -120.24
                            -110.32 -100.0 -89.41
                        ]
                        close? after-second [
                            6.492624817418901 -0.8941010714869255 0.10471975511965977 10000.0 -89.41
                        ]
                    ]""")).isEqualTo("""
                    [#(true) #(true) #(true) #(true)]""");
        }

        @Test
        @DisplayName("reads and writes the vector from its index")
        void writesFromTheIndex() {
            assertThat(answerTo(IMPORTED + """
                    v: #(double! [9 1 2])
                    reduce [m/eval m/compile m/context [x y] {x = x + y} next v v]""")).isEqualTo("""
                    [3.0 #(float64! [9.0 3.0 2.0])]""");
        }

        @Test
        @DisplayName("writes into a protected vector, because the C writes the memory without asking")
        void writesThroughProtection() {
            assertThat(answerTo(IMPORTED + """
                    v: protect #(double! [1])
                    m/eval m/compile m/context [x] {x = 7} v
                    v""")).isEqualTo("#(float64! [7.0])");
        }

        @Test
        @DisplayName("answers a decimal, NaN included, for an expression with no result of its own")
        void answersADecimal() {
            assertThat(answerTo(IMPORTED + """
                    r: m/eval m/compile m/context [x] {var a} #(double! [0])
                    reduce [decimal? r mold r]""")).isEqualTo("""
                    [#(true) "1.#NaN"]""");
        }

        @Test
        @DisplayName("refuses a handle that is not an expression, with the C's message")
        void refusesAnotherHandle() {
            assertThat(answerTo(IMPORTED + """
                    e: try [m/eval m/context [x] #(double! [1])]
                    reduce [e/id e/arg1]""")).isEqualTo("""
                    [command-fail "Command needs a valid MPExpression handle!"]""");
        }

        @Test
        @DisplayName("refuses a vector of anything but doubles, float32 included")
        void refusesAnyOtherVector() {
            assertThat(answerTo(IMPORTED + """
                    e: m/compile m/context [x] {x}
                    reduce [
                        select try [m/eval e #(int32! [1])] 'arg1
                        select try [m/eval e #(float32! [1])] 'arg1
                    ]""")).isEqualTo("""
                    ["Command needs a vector with double values!" "Command needs a vector with double values!"]""");
        }

        @Test
        @DisplayName("refuses a vector with fewer doubles from its index than the context has words")
        void refusesTooFewDoubles() {
            assertThat(answerTo(IMPORTED + """
                    e: m/compile m/context [x y] {x}
                    reduce [
                        select try [m/eval e #(double! [1])] 'arg1
                        select try [m/eval e next #(double! [1 2])] 'arg1
                        m/eval e #(double! [1 2 3])
                    ]""")).isEqualTo("""
                    ["Insufficient size of input data!" "Insufficient size of input data!" 1.0]""");
        }

        @Test
        @DisplayName("counts a word the context could not add, so a repeated name still wants its double")
        void countsARepeatedName() {
            assertThat(answerTo(IMPORTED + """
                    e: m/compile m/context [x x PI] {x}
                    reduce [select try [m/eval e #(double! [1 2])] 'arg1 m/eval e #(double! [1 2 3])]""")).isEqualTo("""
                    ["Insufficient size of input data!" 1.0]""");
        }
    }
}
