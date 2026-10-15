package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AFailedPathNamesThePathFromTheSourceTest {

    private String whatComesBackFrom(String code) {
        Interpreter interpreter = Interpreter.create();
        String asking = "e: try [" + code + "] either error? e [reduce [e/id e/arg1 e/arg2]] [e]";
        interpreter.defineFreshWordsIn(asking);
        return interpreter.display(interpreter.run(asking));
    }

    @Nested
    @DisplayName("an object, a block and a missing value along the way")
    class AlongTheWay {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                o: object [a: 1] o/b                | [invalid-path o/b b]
                o: object [a: 1] o/b/c              | [invalid-path o/b/c b]
                o: object [a: 1] o/b/c: 1           | [invalid-path o/b/c: b]
                o: object [a: [1]] o/a/9/x: 1       | [invalid-path o/a/9/x: 9]
                o: object [a: [1]] o/a/9: 1         | [invalid-path o/a/9: 9]
                o: object [b: none] o/b/c           | [invalid-path o/b/c b]
                o: object [b: none] o/b/c: 1        | [invalid-path o/b/c: b]
                o: object [b: 1] o/b/c              | [invalid-path o/b/c b]
                b: [1] b/9/x                        | [invalid-path b/9/x 9]
                b: [[1]] b/1/1/x                    | [invalid-path b/1/1/x 1]
                n: none n/x                         | [bad-path-type n/x #(none!)]
                i: 5 i/x: 1                         | [bad-path-type i/x: #(integer!)]
                o: object [a: 1] o/b: 2             | [invalid-path o/b: b]
                e: make error! {x} e/foo: 1         | [invalid-path e/foo: foo]
                """)
        void namesThePathAsWrittenAndTheSegment(String code, String expected) {
            assertThat(whatComesBackFrom(code)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("a block, a string, a binary and a vector")
    class Series {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                b: [1 2] b/0: 1                         | 1
                b: [1 2] b/-1: 1                        | [invalid-path b/-1: -1]
                b: [1 2] b/3: 1                         | [invalid-path b/3: 3]
                b: [a 1] b/a: 2 b                       | [a 2]
                b: [a 1] b/z: 2                         | [invalid-path b/z: z]
                s: {ab} s/0: #"x"                       | #"x"
                s: {ab} s/3: #"x"                       | [out-of-range 3 _]
                s: {ab} s/-1: #"x"                      | [out-of-range -1 _]
                s: {ab} s/1: 1                          | 1
                s: {ab} s/1: 1 s                        | "^Ab"
                s: {ab} s/1: {x} s                      | "xb"
                s: {ab} s/1: none                       | [invalid-path s/1: 1]
                s: {ab} s/1: {}                         | [bad-path-set s/1: 1]
                s: next {ab} s/0: #"x" head s           | "ab"
                s: next {ab} s/-1: #"x" head s          | "xb"
                s: {ab} s/x: 1                          | [bad-path-set s/x: x]
                x: #{0102} x/3: 1                       | [out-of-range 3 _]
                x: #{0102} x/1: 256                     | [out-of-range 256 _]
                x: #{0102} x/1: 255 x                   | #{FF02}
                x: #{0102} x/1: -1                      | [bad-path-set x/1: 1]
                x: #{0102} x/1: {a} x                   | #{6102}
                x: #{0102} x/0: 1 x                     | #{0102}
                v: make vector! [integer! 8 2] v/3: 1   | [out-of-range 3 _]
                v: make vector! [integer! 8 2] v/0: 1   | [out-of-range 0 _]
                v: make vector! [integer! 8 2] v/1: {a} | [invalid-arg "a" _]
                """)
        void writesAsPdStringAndPdBlockDo(String code, String expected) {
            assertThat(whatComesBackFrom(code)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("a gob, an event, a pair, a tuple, a date and a time")
    class Scalars {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                make event! [type: {x}]                 | [bad-field-set type: #(string!)]
                ev: make event! [] ev/type: {x}         | [bad-path-set ev/type: type]
                ev: make event! [] ev/nothing: 1        | [bad-path-set ev/nothing: nothing]
                make event! [offset: {x}]               | [bad-field-set offset: #(string!)]
                make gob! [size: {x}]                   | [bad-field-set size: #(string!)]
                make gob! [size:]                       | [need-value size: _]
                make gob! [text: 1]                     | [bad-field-set text: #(integer!)]
                g: make gob! [] g/size: {x}             | [bad-path-set g/size: size]
                g: make gob! [] g/offset: {x}           | [bad-path-set g/offset: offset]
                g: make gob! [] g/size/x: {x}           | [bad-path-set g/size/x: x]
                g: make gob! [] g/size/z: 1             | [invalid-path g/size/z: z]
                p: 1x2 p/z: 3                           | [invalid-path p/z: z]
                p: 1x2 p/x: {a}                         | [bad-path-set p/x: x]
                p: 1x2 p/area: 3                        | [bad-path-set p/area: area]
                t: 1.2.3 t/9: 1                         | 1
                t: 1.2.3 t/1: {a}                       | [bad-path-set t/1: 1]
                t: 1.2.3 t/13: 1                        | [invalid-path t/13: 13]
                d: 1-Jan-2000/10:00 d/zone: {x}         | [bad-field-set zone #(string!)]
                d: 1-Jan-2000/10:00 d/zone: 1.5.6       | [bad-field-set zone #(tuple!)]
                d: 1-Jan-2000/10:00 d/time: {x}         | [bad-field-set time #(string!)]
                d: 1-Jan-2000/10:00 d/weekday: 1        | [bad-path-set d/weekday: weekday]
                d: 1-Jan-2000/10:00 d/nothing: 1        | [invalid-path d/nothing: nothing]
                x: 1:2:3 x/hour: {a}                    | [bad-path-set x/hour: hour]
                x: 1:2:3 x/foo: 1                       | [invalid-path x/foo: foo]
                x: 1:2:3 x/4: 1                         | [invalid-path x/4: 4]
                x: 1:2:3 x/second: -1.5                 | [out-of-range -1.5 _]
                x: 1:2:3 x/hour: -1                     | [out-of-range -1 _]
                w: a@b.c w/foo: 1                       | [bad-path-set w/foo: foo]
                u: http://a.b u/foo: 1                  | [bad-path-set u/foo: foo]
                i: make image! 2x2 i/9: 1.2.3           | [bad-path-set i/9: 9]
                """)
        void refusesAsTheirHandlersDo(String code, String expected) {
            assertThat(whatComesBackFrom(code)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("a time's parts written through a path")
    class WritingATime {

        @ParameterizedTest(name = "{0} gives {1}")
        @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                x: 1:2:3 x/hour: 5 x                    | 5:02:03
                x: 1:2:3 x/2: 7 x                       | 1:07:03
                x: 1:2:3 x/second: 1.5 x                | 1:02:01.5
                x: 1:2:3 x/3: none x                    | 1:02
                x: 1:2:3 x/minute: 2.7 x                | 1:02:03
                x: 1:2:3 x/hour: 0 x                    | 0:02:03
                y: -1:2:3 y/hour: 4 y                   | 4:02:03
                x: 1:2:3 x/1: 2147483647 x              | 487600:40:38.697872896
                x: 1:2:3 x/1: 2147483648                | [out-of-range 2147483648 _]
                """)
        void splitsReplacesAndJoins(String code, String expected) {
            assertThat(whatComesBackFrom(code)).isEqualTo(expected);
        }
    }
}
