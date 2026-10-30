package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheEasingModuleFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("before it is imported")
    class BeforeImport {

        @Test
        @DisplayName("none of its words is in lib")
        void leavesLibAlone() {
            assertThat(answerTo("""
                    reduce [in lib 'tween in lib 'in-quad in lib 'out-bounce]""")).isEqualTo("[_ _ _]");
        }

        @Test
        @DisplayName("the module table holds it, waiting to be made")
        void waitsInTheTable() {
            assertThat(answerTo("""
                    entry: select system/modules 'easing
                    reduce [type? entry entry/1/name entry/1/title]""")).isEqualTo("""
                    [#(block!) easing "Rebol Easing Extension"]""");
        }
    }

    @Nested
    @DisplayName("importing it")
    class Importing {

        @Test
        @DisplayName("answers the module, which the table then holds, and a second import answers the same one")
        void answersTheModule() {
            assertThat(answerTo("""
                    m: import 'easing
                    reduce [module? m same? m system/modules/easing same? m import 'easing]""")).isEqualTo("""
                    [#(true) #(true) #(true)]""");
        }

        @Test
        @DisplayName("gives the module the words R3's extension module has, in its order")
        void hasTheExtensionsWords() {
            assertThat(answerTo("""
                    words-of import 'easing""")).isEqualTo("""
                    [lib-base lib-file lib-local words tween linear in-quad in-cubic in-quart in-quint in-sine \
                    in-circ in-expo in-elastic in-back in-bounce out-quad out-cubic out-quart out-quint out-sine \
                    out-circ out-expo out-elastic out-back out-bounce in-out-quad in-out-cubic in-out-quart \
                    in-out-sine in-out-circ in-out-expo in-out-elastic in-out-back in-out-bounce]""");
        }

        @Test
        @DisplayName("has no in-out-quint, which the extension's C defines and its spec leaves out")
        void hasNoInOutQuint() {
            assertThat(answerTo("""
                    in import 'easing 'in-out-quint""")).isEqualTo("_");
        }

        @Test
        @DisplayName("exports TWEEN alone: the curves stay in the module")
        void exportsTweenAlone() {
            assertThat(answerTo("""
                    import 'easing
                    reduce [type? :tween type? :in-quad type? :lib/tween]""")).isEqualTo("""
                    [#(function!) #(unset!) #(function!)]""");
        }

        @Test
        @DisplayName("carries the extension's own header, named as IMPORT names it")
        void carriesTheHeader() {
            assertThat(answerTo("""
                    h: spec-of import 'easing
                    reduce [h/name h/title h/exports h/version h/options]""")).isEqualTo("""
                    [easing "Rebol Easing Extension" [tween] 0.0.0 [extension]]""");
        }
    }

    @Nested
    @DisplayName("a curve")
    class ACurve {

        @Test
        @DisplayName("is a command taking one decimal, with the extension's help")
        void isACommand() {
            assertThat(answerTo("""
                    m: import 'easing
                    reduce [type? :m/in-quad spec-of :m/in-quad]""")).isEqualTo("""
                    [#(command!) ["Modeled after the parabola y = x^^2" x [decimal!]]]""");
        }

        @Test
        @DisplayName("answers what R3 answers with the extension loaded")
        void answersAsR3Does() {
            assertThat(answerTo("""
                    m: import 'easing
                    reduce [m/in-quad 0.5 m/out-elastic 0.3 m/in-out-bounce 0.7 m/linear 0.25]""")).isEqualTo("""
                    [0.25 0.876538957425608 0.946 0.25]""");
        }

        @Test
        @DisplayName("refuses an integer, as its spec says")
        void refusesAnInteger() {
            assertThat(answerTo("""
                    m: import 'easing
                    e: try [m/in-quad 1]
                    reduce [e/id e/arg1 e/arg2 e/arg3]""")).isEqualTo("""
                    [expect-arg in-quad x #(integer!)]""");
        }

        @Test
        @DisplayName("is not checked for its range, as the extension does not check it")
        void takesAnyDecimal() {
            assertThat(answerTo("""
                    m: import 'easing
                    reduce [m/in-quad 2.0 m/in-quad -1.0 m/linear 7.5]""")).isEqualTo("[4.0 1.0 7.5]");
        }
    }

    @Nested
    @DisplayName("TWEEN")
    class Tween {

        @Test
        @DisplayName("interpolates a number, a pair and a tuple and answers the type it was given")
        void interpolates() {
            assertThat(answerTo("""
                    import 'easing
                    reduce [
                        tween 0 10 0.5 'in-quad
                        tween 0.0 10.0 0.5 'out-quad
                        tween 1x1 11x21 0.5 'linear
                        tween 0.0.0 255.255.255 0.5 'in-out-sine
                    ]""")).isEqualTo("[2 7.5 6x11 127.127.127]");
        }

        @Test
        @DisplayName("names a curve the module lacks as an invalid path, as R3 does")
        void refusesAnUnknownCurve() {
            assertThat(answerTo("""
                    import 'easing
                    e: try [tween 0 10 0.5 'no-such-curve]
                    reduce [e/id e/arg1 e/arg2]""")).isEqualTo("[invalid-path self/:ease :ease]");
        }
    }
}
