package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A parameter written with no datatypes beside it gets {@code TS_VALUE} in the C,
 * which is every datatype but unset; {@code any-type!} is the wider set that
 * includes it. The equality natives declare the wider one and the ordering
 * natives declare the bare one, so {@code equal? () ()} answers and
 * {@code greater? () 1} is refused at the argument check.
 */
class BareParameterAndUnsetFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a bare parameter refuses an absence")
    class TheBareParameter {

        @Test
        @DisplayName("a mezzanine function with a bare parameter refuses unset")
        void joinRefusesAnUnset() {
            assertThat(answerTo("""
                    e: try [join () "x"] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a written function with a bare parameter refuses it the same way")
        void aWrittenFunctionRefusesAnUnset() {
            assertThat(answerTo("""
                    takes-anything: func [x] [true]
                    e: try [takes-anything ()] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and takes every other value, none included")
        void aBareParameterTakesNone() {
            assertThat(answerTo("""
                    names-the-type: func [x] [type? :x]
                    (names-the-type none) = none!""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a parameter naming a datatype refuses an absence as well")
        void aTypedParameterRefusesAnUnset() {
            assertThat(answerTo("""
                    counts: func [x [integer!]] [x]
                    e: try [counts ()] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a native taking a value refuses one too")
        void addRefusesAnUnset() {
            assertThat(answerTo("""
                    e: try [add () 1] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("any-type! is the wider set and takes one")
    class TheWiderSet {

        @Test
        @DisplayName("TYPE? of an absence names the unset datatype")
        void typeOfAnUnset() {
            assertThat(answerTo("""
                    (type? ()) = unset!""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("UNSET? answers true of one")
        void unsetOfAnUnset() {
            assertThat(answerTo("""
                    unset? ()""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("FORM of one is the empty string")
        void formOfAnUnset() {
            assertThat(answerTo("""
                    (form ()) = {}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a written function declaring any-type! takes one")
        void aDeclaredParameterTakesAnUnset() {
            assertThat(answerTo("""
                    asks-whether-there-is-one: func [x [any-type!]] [not value? 'x]
                    asks-whether-there-is-one ()""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the same function still takes a value")
        void aDeclaredParameterStillTakesAValue() {
            assertThat(answerTo("""
                    asks-whether-there-is-one: func [x [any-type!]] [not value? 'x]
                    not asks-whether-there-is-one 1""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("equality answers of an absence and ordering refuses one")
    class TheEqualityOrderingSplit {

        @Test
        @DisplayName("EQUAL? of two absences is true")
        void twoAbsencesAreEqual() {
            assertThat(answerTo("""
                    equal? () ()""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("NOT-EQUAL? of an absence and a value is true")
        void anAbsenceIsNotAValue() {
            assertThat(answerTo("""
                    not-equal? () 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("GREATER? refuses one on its left")
        void greaterRefusesAnAbsenceOnTheLeft() {
            assertThat(answerTo("""
                    e: try [greater? () 1] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("LESSER? refuses one on its right")
        void lesserRefusesAnAbsenceOnTheRight() {
            assertThat(answerTo("""
                    e: try [lesser? 1 ()] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("GREATER-OR-EQUAL? refuses two of them")
        void greaterOrEqualRefusesTwoAbsences() {
            assertThat(answerTo("""
                    e: try [greater-or-equal? () ()] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("LESSER-OR-EQUAL? refuses one as well")
        void lesserOrEqualRefusesAnAbsence() {
            assertThat(answerTo("""
                    e: try [lesser-or-equal? () 1] e/id = 'expect-arg""")).isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("SET threads an absence through into the word")
    void setWritesAnAbsenceIntoTheWord() {
        assertThat(answerTo("""
                set 'somewhere-to-put-it () not value? 'somewhere-to-put-it"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ANY passes over an absence and answers the value after it")
    void anyPassesOverAnAbsence() {
        assertThat(answerTo("""
                (any [() 1]) = 1""")).isEqualTo("#(true)");
    }
}
