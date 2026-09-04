package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A set-word in a walk's name list is a position, not a value.
 *
 * <p>{@code Init_Loop} in {@code n-loop.c} accepts a word or a set-word and
 * remembers which of the two each name was -- {@code VAL_SET(word,
 * VAL_TYPE(spec))} -- and {@code Loop_Each} then has an arm for each. A word
 * takes the next value and moves on. A set-word is handed the series itself,
 * standing where the walk has got to, and the index is left alone; the C's own
 * note beside that line reads "do not increment block."
 *
 * <p>What it is for: a walk that inserts or removes where it stands. Rebol's
 * own HANDLE-EVENTS is written on it, walking the handler list for the first
 * with a lower priority and doing {@code insert here handler}.
 *
 * <p>Reading it as an ordinary name is a quiet failure rather than a loud one,
 * which is why it went unnoticed. The loop still runs. It just takes two
 * values a round instead of one, so half of them are skipped and the second
 * name holds a value from the series where a position was meant to be.
 * HANDLE-EVENTS then reached {@code hand/priority} on a none and raised, which
 * stopped every script that showed a window.
 *
 * <p>Every expectation below was checked against a real 3.22.1 first.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
class WalkPositionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("over a block")
    class OverABlock {

        @Test
        @DisplayName("a leading set-word stands at the value the word is about to take")
        void aLeadingSetWordStandsAtTheValue() {
            assertThat(answerTo("""
                    mold collect [foreach [p: v] [a b c] [keep reduce [mold p mold v]]]"""))
                    .isEqualTo("{[\"[a b c]\" \"a\" \"[b c]\" \"b\" \"[c]\" \"c\"]}");
        }

        @Test
        @DisplayName("and it takes one value a round, not two")
        void itTakesOneValueARound() {
            assertThat(answerTo("""
                    n: 0
                    foreach [p: v] [a b c] [n: n + 1]
                    n""")).as("a set-word consumes nothing, so three values are three rounds")
                    .isEqualTo("3");
        }

        @Test
        @DisplayName("a trailing set-word stands after the words before it have taken")
        void aTrailingSetWordStandsAfterTheOthers() {
            assertThat(answerTo("""
                    mold collect [foreach [v p:] [a b c] [keep reduce [mold v mold p]]]"""))
                    .as("the position is read when the list reaches it, not at the "
                            + "start of the round, so the last round sees an empty tail")
                    .isEqualTo("{[\"a\" \"[b c]\" \"b\" \"[c]\" \"c\" \"[]\"]}");
        }

        @Test
        @DisplayName("and the position is the series itself, sharing its storage")
        void thePositionSharesStorage() {
            assertThat(answerTo("""
                    s: [a b c]
                    found: none
                    foreach [p: v] s [if v = 'b [found: p]]
                    same? s head found""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("which is what lets a walk insert where it stands")
        void aWalkCanInsertWhereItStands() {
            assertThat(answerTo("""
                    s: [a c]
                    foreach [here: v] s [if v = 'c [insert here 'b  break]]
                    mold s"""))
                    .as("HANDLE-EVENTS inserts a handler by priority exactly this way")
                    .isEqualTo("\"[a b c]\"");
        }
    }

    @Nested
    @DisplayName("a list of nothing but set-words")
    class OnlySetWords {

        @Test
        @DisplayName("still advances, one value a round")
        void itStillAdvances() {
            assertThat(answerTo("""
                    mold collect [foreach [a:] [1 2 3] [keep mold a]]"""))
                    .as("nothing takes a value, so without this the walk never ends; "
                            + "the C's fix is `if (index == rindex) index++`")
                    .isEqualTo("{[\"[1 2 3]\" \"[2 3]\" \"[3]\"]}");
        }

        @Test
        @DisplayName("and runs once per value rather than for ever")
        void itRunsOncePerValue() {
            assertThat(answerTo("""
                    n: 0
                    foreach [a:] [1 2 3] [n: n + 1]
                    n""")).isEqualTo("3");
        }

        @Test
        @DisplayName("over an empty series it does not run at all")
        void anEmptySeriesRunsNothing() {
            assertThat(answerTo("""
                    n: 0
                    foreach [a:] [] [n: n + 1]
                    n""")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("over a string")
    class OverAString {

        @Test
        @DisplayName("the position is a string and the word is a character")
        void thePositionIsAString() {
            assertThat(answerTo("""
                    mold collect [foreach [p: c] {abc} [keep reduce [mold p mold c]]]"""))
                    .isEqualTo("{[{\"abc\"} {#\"a\"} {\"bc\"} {#\"b\"} {\"c\"} {#\"c\"}]}");
        }
    }

    @Nested
    @DisplayName("over an object or a map")
    class OverAnObjectOrAMap {

        @Test
        @DisplayName("a set-word is the whole thing, because neither is walked by index")
        void theSetWordIsTheWholeObject() {
            assertThat(answerTo("""
                    o: make object! [x: 1 y: 2]
                    same-every-round: true
                    foreach [whole: k] o [unless same? whole o [same-every-round: false]]
                    same-every-round"""))
                    .as("`if (ANY_OBJECT(value) || IS_MAP(value)) *vars = *value;`")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the word still walks the keys one a round")
        void thewordStillWalksTheKeys() {
            assertThat(answerTo("""
                    mold collect [foreach [whole: k] make object! [x: 1 y: 2] [keep k]]"""))
                    .isEqualTo("\"[x y]\"");
        }

        @Test
        @DisplayName("over a map, the same")
        void overAMapTheSame() {
            assertThat(answerTo("""
                    m: make map! [a 1 b 2]
                    mold collect [foreach [whole: k] m [keep k]]"""))
                    .isEqualTo("\"[a b]\"");
        }
    }

    @Nested
    @DisplayName("the other walks take the same name list")
    class TheOtherWalks {

        @Test
        @DisplayName("MAP-EACH, because Init_Loop reads the list before the walk is chosen")
        void mapEachTakesIt() {
            assertThat(answerTo("""
                    mold map-each [p: v] [1 2 3] [v * 2]""")).isEqualTo("\"[2 4 6]\"");
        }

        @Test
        @DisplayName("and REMOVE-EACH")
        void removeEachTakesIt() {
            assertThat(answerTo("""
                    s: [1 2 3 4]
                    remove-each [p: v] s [even? v]
                    mold s""")).isEqualTo("\"[1 3]\"");
        }
    }

    @Nested
    @DisplayName("what is still refused")
    class TheRefusals {

        @Test
        @DisplayName("a name that is neither a word nor a set-word")
        void anythingElseIsRefused() {
            assertThat(answerTo("error? try [foreach [1] [a b] []]")).isEqualTo("#(true)");
            assertThat(answerTo("error? try [foreach [{p}] [a b] []]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and an empty name list")
        void anEmptyListIsRefused() {
            assertThat(answerTo("error? try [foreach [] [a b] []]")).isEqualTo("#(true)");
        }
    }
}
