package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * COPY of an object answers a duplicate holding its own slots, which is what
 * Rebol's own DELTA-PROFILE depends on: it copies the standing statistics as a
 * snapshot and subtracts. Sharing the slots made every counter come back as
 * minus the raw value. {@code TS_DEEP_COPIED} names the series datatypes, the map
 * and the function and not the object, so a nested object survives a deep copy
 * untouched and TAKE/DEEP hands one straight through.
 */
class ObjectSnapshotFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("COPY of an object duplicates its slots")
    class TheSnapshot {

        @Test
        @DisplayName("writing a field of the copy leaves the original standing")
        void writingTheCopyLeavesTheOriginal() {
            assertThat(answerTo("""
                    standing: object [a: 1] snapshot: copy standing
                    snapshot/a: 9 standing/a = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the copy is a second object, not a second name for the first")
        void theCopyIsNotTheOriginal() {
            assertThat(answerTo("""
                    standing: object [a: 1] snapshot: copy standing
                    not same? standing snapshot""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and it holds what the original held")
        void theCopyHoldsTheSameValues() {
            assertThat(answerTo("""
                    standing: object [a: 1] snapshot: copy standing
                    snapshot/a = 1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a series field is shared until /DEEP is asked for")
        void aSeriesFieldIsSharedByDefault() {
            assertThat(answerTo("""
                    standing: object [a: [1 2]] snapshot: copy standing
                    same? standing/a snapshot/a""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and /DEEP duplicates it")
        void deepDuplicatesTheSeriesField() {
            assertThat(answerTo("""
                    standing: object [a: [1 2]] snapshot: copy/deep standing
                    not same? standing/a snapshot/a""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("an object sits outside the deep-copied set")
    class TheDeepCopiedSet {

        @Test
        @DisplayName("a nested object stays shared through a deep copy of the block")
        void aNestedObjectStaysShared() {
            assertThat(answerTo("""
                    holder: reduce [object [a: 1]] duplicate: copy/deep holder
                    same? holder/1 duplicate/1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where a nested block is duplicated, which is the contrast")
        void aNestedBlockIsDuplicated() {
            assertThat(answerTo("""
                    holder: reduce [[1 2]] duplicate: copy/deep holder
                    not same? holder/1 duplicate/1""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("TAKE/DEEP hands the taken object itself through")
        void takeDeepHandsTheObjectThrough() {
            assertThat(answerTo("""
                    holder: reduce [object [a: 1]] held: holder/1
                    same? held take/deep holder""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and what it hands through is an object, not a copy of its shape")
        void whatTakeDeepAnswersIsAnObject() {
            assertThat(answerTo("""
                    holder: reduce [object [a: 1]] object? take/deep holder"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("DELTA-PROFILE of an empty block counts nothing")
    class TheProfileDelta {

        @Test
        @DisplayName("every counter comes back zero rather than minus the raw value")
        void anEmptyBlockCountsZero() {
            assertThat(answerTo("""
                    measured: delta-profile []
                    all [
                        measured/evals = 0
                        measured/eval-natives = 0
                        measured/eval-functions = 0
                        measured/series-made = 0
                        measured/made-blocks = 0
                        measured/made-objects = 0
                        measured/recycles = 0
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the answer is an object")
        void theAnswerIsAnObject() {
            assertThat(answerTo("""
                    object? delta-profile []""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a second empty measurement counts zero as well")
        void asecondEmptyMeasurementIsZeroToo() {
            assertThat(answerTo("""
                    first-measure: delta-profile []
                    second-measure: delta-profile []
                    all [first-measure/evals = 0 second-measure/evals = 0]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a block that does work counts more than nothing")
        void workIsCounted() {
            assertThat(answerTo("""
                    measured: delta-profile [loop 10 [make block! 10]]
                    measured/evals > 0""")).isEqualTo("#(true)");
        }
    }
}
