package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDatatypeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    private static final String A_MODULE =
            "make module! [[Title: \"t\" Name: probe-module Exports: [shown]] "
            + "[shown: 1 hidden-one: 2]]";

    @Nested
    @DisplayName("what a module answers about itself")
    class TheDatatype {

        @Test
        @DisplayName("MODULE? answers true and TYPE? names the datatype")
        void isModule() {
            assertThat(answerTo("module? " + A_MODULE)).isEqualTo(TRUE);
            assertThat(answerTo("type? " + A_MODULE)).isEqualTo("#(module!)");
        }

        @Test
        @DisplayName("a module is not an object, because the datatype is the difference")
        void isNotAnObject() {
            assertThat(answerTo("object? " + A_MODULE)).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("but it is an ANY-OBJECT, which is the typeset types.reb puts it in")
        void isAnAnyObject() {
            assertThat(answerTo("any-object? " + A_MODULE)).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("it molds as an object, because that is its mold typeclass")
        void moldsAsAnObject() {
            assertThat(answerTo("find mold " + A_MODULE + " \"shown\"")).isNotEqualTo("_");
        }

        @Test
        @DisplayName("the words its body defined are reachable through a path")
        void reachesTheBodysWords() {
            assertThat(answerTo("m: " + A_MODULE + " m/shown")).isEqualTo("1");
        }

        @Test
        @DisplayName("a private word is still the module's own, just not the library's")
        void stillOwnsAPrivateWord() {
            assertThat(answerTo("m: " + A_MODULE + " m/hidden-one")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("MAKE MODULE! hands the work to the library")
    class Making {

        @Test
        @DisplayName("MAKE on a spec and a body answers a module")
        void makeAnswersAModule() {
            assertThat(answerTo("module? make module! [[Title: \"t\"] [a: 1]]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the body runs, proved by a side effect that leaves the module")
        void runsTheBody() {
            assertThat(answerTo(
                    "b: copy [] make module! [[Title: \"t\"] [append b 9]] length? b"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("and an assignment in the body writes to the module, not outside it")
        void keepsAnAssignmentInTheModule() {
            assertThat(answerTo(
                    "n: 0 m: make module! [[Title: \"t\"] [n: 9]] "
                    + "reduce [n m/n]")).isEqualTo("[0 9]");
        }

        @Test
        @DisplayName("the EXPORT keyword in a body works, which only MAKE-MODULE* implements")
        void honoursTheExportKeyword() {
            assertThat(answerTo(
                    "m: make module! [[Title: \"t\"] [export shown: 1 private: 2]] "
                    + "find mold spec-of m \"shown\"")).isNotEqualTo("_");
        }

        @Test
        @DisplayName("a name in the header is kept as a word")
        void takesAWordAsTheName() {
            assertThat(answerTo(
                    "m: make module! [[Title: \"t\" Name: jots] [a: 1]] "
                    + "'jots = select spec-of m 'name")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a name that is not a word is refused, not quietly converted")
        void refusesANameThatIsNotAWord() {
            assertThat(readTheErrorIdFrom("make module! [[Title: \"t\" Name: \"jots\"] [a: 1]]"))
                    .isEqualTo("wrong-type");
        }

        @Test
        @DisplayName("a header that does not say its type gets module")
        void defaultsTheTypeToModule() {
            assertThat(answerTo(
                    "m: make module! [[Title: \"t\"] [a: 1]] "
                    + "'module = select spec-of m 'type")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("MAKE MODULE! at its boundaries")
    class MakingBoundaries {

        @Test
        @DisplayName("an empty block is refused")
        void refuseAnEmptyBlock() {
            assertThat(readTheErrorIdFrom("make module! []")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a block holding a spec and no body is refused")
        void refuseABlockWithNoBody() {
            assertThat(readTheErrorIdFrom("make module! [[Title: \"t\"]]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a third value that is an object is a mixin and is accepted")
        void mixinsAreAccepted() {
            assertThat(answerTo(
                    "module? make module! reduce [[Title: \"t\"] [a: 1] make object! [b: 2]]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a third value that is neither an object nor none is refused")
        void refuseABadMixin() {
            assertThat(readTheErrorIdFrom("make module! [[Title: \"t\"] [a: 1] 5]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a header field of the wrong datatype is refused")
        void refuseAHeaderFieldOfTheWrongType() {
            assertThat(readTheErrorIdFrom("make module! [[Title: \"t\" Version: 5] [a: 1]]"))
                    .isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("make module! [[Title: \"t\" Options: 5] [a: 1]]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a body that is not a block is refused")
        void refuseANonBlockBody() {
            assertThat(readTheErrorIdFrom("make module! [[Title: \"t\"] \"not a block\"]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("anything that is not a block at all is refused")
        void refuseANonBlock() {
            assertThat(readTheErrorIdFrom("make module! 5")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("make module! \"x\"")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("make module! none")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("make module! make object! [a: 1]"))
                    .isNotEqualTo("no-error");
        }
    }

    @Nested
    @DisplayName("TO MODULE! joins a header and a context")
    class Converting {

        private static final String HEADER = "make object! [name: none type: none]";
        private static final String BODY = "make object! [a: 1]";

        @Test
        @DisplayName("two objects make a module")
        void joinsAHeaderAndAContext() {
            assertThat(answerTo(
                    "module? to module! reduce [" + HEADER + " " + BODY + "]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the words of the second one are the module's")
        void takesTheWordsFromTheSecondObject() {
            assertThat(answerTo(
                    "m: to module! reduce [" + HEADER + " " + BODY + "] m/a"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("a first value that is not an object is refused")
        void refusesANonObjectHeader() {
            assertThat(readTheErrorIdFrom("to module! reduce [5 " + BODY + "]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a second value that is not an object is refused")
        void refusesANonObjectContext() {
            assertThat(readTheErrorIdFrom("to module! reduce [" + HEADER + " 5]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("an empty block is refused")
        void refusesAnEmptyBlock() {
            assertThat(readTheErrorIdFrom("to module! []")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a block holding only one object is refused")
        void refusesABlockOfOne() {
            assertThat(readTheErrorIdFrom("to module! reduce [" + HEADER + "]"))
                    .isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("anything that is not a block is refused")
        void refusesANonBlock() {
            assertThat(readTheErrorIdFrom("to module! 5")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("to module! none")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("to module! \"x\"")).isNotEqualTo("no-error");
        }
    }
}
