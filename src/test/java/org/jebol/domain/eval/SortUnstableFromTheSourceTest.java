package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * /UNSTABLE is the symmetry-partition sort, and its permutation is part of the
 * contract: {@code series-test.r3} pins both orders of the same twenty-five field
 * names compared by length, under "SORT infinite loop case". The two nested
 * classes fail differently on purpose -- the pinned permutation catches a change
 * of algorithm, the property catches a sort that stopped sorting.
 */
class SortUnstableFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String THE_FIELD_NAMES = """
            field-names: ["type" "offset" "size" "text" "image" "color" "menu"
                "data" "enabled?" "visible?" "selected" "flags" "options" "parent"
                "pane" "state" "rate" "edge" "para" "font" "actors" "extra" "draw"
                "on-change*" "on-deep-change*"]
            by-length: func [a b] [(length? a) < (length? b)]
            """;

    @Nested
    @DisplayName("the permutation Rebol's own test pins")
    class ThePinnedPermutation {

        @Test
        @DisplayName("the stable sort settles ties by where they started")
        void theStableOrder() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    (sort/compare copy field-names :by-length) == [
                        "type" "size" "text" "menu" "data" "pane" "rate" "edge"
                        "para" "font" "draw" "image" "color" "flags" "state"
                        "extra" "offset" "parent" "actors" "options" "enabled?"
                        "visible?" "selected" "on-change*" "on-deep-change*"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the unstable sort settles them where the partition left them")
        void theUnstableOrder() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    (sort/unstable/compare copy field-names :by-length) == [
                        "type" "menu" "size" "text" "draw" "pane" "edge" "data"
                        "rate" "font" "para" "flags" "color" "image" "state"
                        "extra" "offset" "actors" "parent" "options" "selected"
                        "visible?" "enabled?" "on-change*" "on-deep-change*"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the two disagree, which is what /UNSTABLE means")
        void theTwoOrdersDiffer() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    not equal?
                        sort/compare copy field-names :by-length
                        sort/unstable/compare copy field-names :by-length"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a stable skip sort keeps two equal keys in the order they arrived")
        void theStableSkipOrder() {
            assertThat(answerTo("""
                    (sort/skip/compare [Alice 30 Carol 30 Bob 25] 2 2)
                        == [Bob 25 Alice 30 Carol 30]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("where the unstable one does not")
        void theUnstableSkipOrder() {
            assertThat(answerTo("""
                    (sort/unstable/skip/compare [Alice 30 Carol 30 Bob 25] 2 2)
                        == [Bob 25 Carol 30 Alice 30]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("whatever the permutation, the sort is still a sort")
    class TheOrderingProperty {

        @Test
        @DisplayName("a differently arranged input comes back in order")
        void theResultIsInOrder() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    differently-arranged: reverse copy field-names
                    sorted: sort/unstable/compare copy differently-arranged :by-length
                    in-order: true
                    repeat at (-1 + length? sorted) [
                        if by-length sorted/(at + 1) sorted/:at [in-order: false]
                    ]
                    in-order""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("holding exactly the items it was given")
        void theResultHoldsTheSameItems() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    differently-arranged: reverse copy field-names
                    sorted: sort/unstable/compare copy differently-arranged :by-length
                    (sort copy sorted) = (sort copy differently-arranged)"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and none of them twice")
        void theResultIsAsLongAsTheInput() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    differently-arranged: reverse copy field-names
                    sorted: sort/unstable/compare copy differently-arranged :by-length
                    (length? sorted) = (length? field-names)""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block sorts to an empty block")
        void theEmptyBlockIsTheDegenerateCase() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    empty? sort/unstable/compare copy [] :by-length"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and one item is already in order")
        void oneItemIsAlreadySorted() {
            assertThat(answerTo(THE_FIELD_NAMES + """
                    (sort/unstable/compare copy ["only"] :by-length) = ["only"]"""))
                    .isEqualTo("#(true)");
        }
    }
}
