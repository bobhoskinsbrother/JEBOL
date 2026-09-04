package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TO given something that is not a datatype.
 *
 * <p>Its first argument is spelled {@code type [any-type!] "The datatype or
 * example value"}, and MAKE says the same. So {@code to "" #{6162}} is
 * {@code to string!}: the example is read for its type and then thrown away.
 * JEBOL took a datatype and nothing else, which is what stopped Rebol's own
 * quoted-printable codec -- it ends on {@code to data output}, where DATA is
 * whatever the caller passed in, and handing back the kind of thing it was
 * given is the whole point of the form.
 *
 * <p>The other half of this is where TO and MAKE are the same code.
 * {@code case A_MAKE: case A_TO:} with nothing between the labels is how
 * {@code t-object.c}, {@code t-function.c} and {@code t-struct.c} are written,
 * so an error, a function, a closure and a struct are built the same way
 * whichever word asked. Objects and modules are the exception: they have a TO
 * of their own, a few lines below, that takes only one thing each.
 */
class ToAnExampleValueFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] failure/id");
    }

    @Nested
    @DisplayName("an example value stands for its datatype")
    class TheExample {

        @Test
        @DisplayName("a string, a binary and a block each convert as their type would")
        void theSeriesTypes() {
            assertThat(answerTo("""
                    reduce [to "abc" 1 to "" #{6162} to #{} "ab" to [] "a b"]"""))
                    .isEqualTo("""
                    ["1" "ab" #{6162} ["a b"]]""");
        }

        @Test
        @DisplayName("a number, a file and a pair too")
        void theScalarTypes() {
            assertThat(answerTo("""
                    reduce [to 0 "42" to 0.0 "4.5" to %x 1 to 1x1 [2 3]]"""))
                    .isEqualTo("[42 4.5 %1 2x3]");
        }

        @Test
        @DisplayName("and a word keeps the kind of word the example was")
        void theWordTypes() {
            assertThat(answerTo("""
                    reduce [to 'a "b" to first [a:] "b" to first [:a] "b"]"""))
                    .isEqualTo("[b b: :b]");
        }

        @Test
        @DisplayName("none and a logic answer what their datatypes answer")
        void noneAndLogic() {
            assertThat(answerTo("""
                    reduce [to none 1 to true 0]""")).isEqualTo("[_ #(true)]");
        }

        @Test
        @DisplayName("a date example reads a date out of a string")
        void aDateExample() {
            assertThat(answerTo("""
                    to 1-Jan-2000 "2-Feb-2001\"""")).isEqualTo("2-Feb-2001");
        }

        @Test
        @DisplayName("naming the datatype still works, which is the off point")
        void namingTheDatatypeStillWorks() {
            assertThat(answerTo("""
                    reduce [to string! 1 to integer! "42"]""")).isEqualTo("[\"1\" 42]");
        }
    }

    @Nested
    @DisplayName("the datatypes whose TO is their MAKE")
    class TheSharedBranch {

        @Test
        @DisplayName("TO ERROR! builds the error, as MAKE ERROR! does")
        void toErrorBuildsTheError() {
            assertThat(answerTo("""
                    failure: to error! [type: 'Math id: 'overflow]
                    reduce [failure/type failure/id failure/code]"""))
                    .isEqualTo("[Math overflow 401]");
        }

        @Test
        @DisplayName("TO FUNCTION! builds one from a spec and a body")
        void toFunctionBuildsOne() {
            assertThat(answerTo("""
                    doubling: to function! [[n][n * 2]]
                    doubling 21""")).isEqualTo("42");
        }

        @Test
        @DisplayName("and a closure the same way, which is a closure and not a function")
        void toClosureBuildsOne() {
            assertThat(answerTo("""
                    doubling: to closure! [[n][n * 2]]
                    reduce [
                        closure? :doubling
                        function? :doubling
                        any-function? :doubling
                        type? :doubling
                        doubling 21
                    ]""")).isEqualTo("[#(true) #(false) #(true) #(closure!) 42]");
        }

        @Test
        @DisplayName("and so is one written with CLOSURE, which said function! before")
        void oneWrittenWithClosure() {
            assertThat(answerTo("""
                    doubling: closure [n][n * 2]
                    plain: func [n][n * 2]
                    reduce [type? :doubling type? :plain closure? :plain]"""))
                    .isEqualTo("[#(closure!) #(function!) #(false)]");
        }

        @Test
        @DisplayName("what none of them can be built from is bad-make-arg")
        void whatTheyCannotBeBuiltFrom() {
            assertThat(errorIdFrom("to closure! 1")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("to struct! 1")).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("TO OBJECT!, which is not MAKE OBJECT! and takes only an error")
    class ToAnObject {

        @Test
        @DisplayName("an error becomes the eight-field object it already is")
        void anErrorBecomesItsObject() {
            assertThat(answerTo("""
                    mold/flat to object! make error! [type: 'Math id: 'overflow]"""))
                    .isEqualTo("{make object! [code: 401 type: 'Math id: 'overflow"
                            + " arg1: _ arg2: _ arg3: _ near: _ where: _]}");
        }

        @Test
        @DisplayName("and it is an object afterwards, not an error")
        void andItIsAnObjectAfterwards() {
            assertThat(answerTo("""
                    converted: to object! make error! [type: 'Note id: 'exited]
                    reduce [object? converted error? converted converted/code]"""))
                    .isEqualTo("[#(true) #(false) 101]");
        }

        @Test
        @DisplayName("anything else is bad-make-arg, where MAKE OBJECT! would build one")
        void anythingElseIsBadMakeArg() {
            assertThat(errorIdFrom("to object! [b 2]")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("to object! 1")).isEqualTo("bad-make-arg");
            assertThat(answerTo("""
                    object? make object! [b: 2]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("TO MODULE!, which joins a spec object to a body object")
    class ToAModule {

        @Test
        @DisplayName("two objects in a block, the first the spec and the second the body")
        void twoObjectsInABlock() {
            assertThat(answerTo("""
                    made: to module! reduce [make object! [name: 'x] make object! [a: 1]]
                    reduce [module? made mold/flat made]"""))
                    .isEqualTo("[#(true) \"make module! [a: 1]\"]");
        }

        @Test
        @DisplayName("a block of things that are not objects is invalid-arg")
        void notObjectsIsInvalidArg() {
            assertThat(errorIdFrom("to module! [1 2]")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and an empty block, or no block at all, is bad-make-arg")
        void anEmptyBlockIsBadMakeArg() {
            assertThat(errorIdFrom("to module! []")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("to module! 1")).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("a port and a module name themselves when molded")
    class TheyNameThemselves {

        @Test
        @DisplayName("a module says module, not object")
        void aModuleSaysModule() {
            assertThat(answerTo("""
                    mold/flat to module! reduce [make object! [name: 'x] make object! [a: 1]]"""))
                    .isEqualTo("\"make module! [a: 1]\"");
        }

        @Test
        @DisplayName("and a port says port, so that what is molded reads back as one")
        void aPortSaysPort() {
            assertThat(answerTo("""
                    find mold/flat open checksum://md5 "make port!\""""))
                    .isNotEqualTo("_");
        }
    }
}
