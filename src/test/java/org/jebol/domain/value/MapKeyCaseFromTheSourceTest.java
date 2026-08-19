package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A map finds a key without minding its case, unless it is told to.
 *
 * <p>{@code Find_Entry} in {@code t-map.c} takes a {@code cased} flag and its
 * six callers do not agree about it. A path read, SELECT, FIND, PUT and POKE
 * all pass false, so they match without minding case. MAKE and REMOVE/KEY pass
 * true, and so do {@code select/case} and {@code put/case}.
 *
 * <p>Which means the two ends of a map behave differently on purpose: building
 * one keeps {@code "k"} and {@code "K"} apart, and looking one up does not.
 * That is not an inconsistency to tidy -- it is what lets a map be written with
 * whatever case a caller has to hand while still being able to hold both.
 *
 * <p>Three things here would be got wrong by reasoning and were checked
 * against a real 3.22.1 instead. FIND answers the key <em>as stored</em> rather
 * than as asked. A write that matches without case updates the existing entry
 * and keeps the spelling that was there first. And this is not only about
 * strings: a word key matches the same way.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
class MapKeyCaseFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String A_MAP_KEYED_LOWER = """
            m: make map! []
            m/("k"): 1
            """;

    @Nested
    @DisplayName("looking one up does not mind the case")
    class TheLookups {

        @Test
        @DisplayName("SELECT finds it")
        void selectFindsIt() {
            assertThat(answerTo(A_MAP_KEYED_LOWER + """
                    select m "K\"""")).isEqualTo("1");
        }

        @Test
        @DisplayName("a path read finds it")
        void apathReadFindsIt() {
            assertThat(answerTo(A_MAP_KEYED_LOWER + """
                    m/("K")""")).isEqualTo("1");
        }

        @Test
        @DisplayName("and FIND finds it, answering the key as it is stored")
        void findAnswersTheStoredKey() {
            // The part that would be got wrong by reasoning. FIND on a map
            // answers the key rather than the value, and the key it answers is
            // the one the map holds -- so asking with "K" answers "k".
            assertThat(answerTo(A_MAP_KEYED_LOWER + """
                    mold find m "K\"""")).isEqualTo("{\"k\"}");
        }

        @Test
        @DisplayName("a word key matches the same way, which is not only about strings")
        void awordKeyMatchesToo() {
            assertThat(answerTo("""
                    m: make map! []
                    m/a: 1
                    select m 'A""")).isEqualTo("1");
        }

        @Test
        @DisplayName("and FIND on a word answers the set-word the map stores")
        void findOnAWordAnswersTheSetWord() {
            assertThat(answerTo("""
                    m: make map! []
                    m/a: 1
                    mold find m 'A""")).isEqualTo("\"a:\"");
        }

        @Test
        @DisplayName("a key that differs by more than case is still not there")
        void adifferentKeyIsStillAbsent() {
            assertThat(answerTo(A_MAP_KEYED_LOWER + """
                    none? select m "j\"""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("unless it is told to mind it")
    class TheCasedLookups {

        @Test
        @DisplayName("SELECT/CASE does not find the other case")
        void selectCaseDoesNotFindIt() {
            assertThat(answerTo(A_MAP_KEYED_LOWER + """
                    none? select/case m "K\"""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but does find the same one")
        void selectCaseFindsTheSameOne() {
            assertThat(answerTo(A_MAP_KEYED_LOWER + """
                    select/case m "k\"""")).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("writing without minding the case keeps the key that was there")
    class TheWrites {

        @Test
        @DisplayName("a path write updates the entry rather than adding one")
        void apathWriteUpdatesTheEntry() {
            assertThat(answerTo("""
                    m: make map! []
                    m/("k"): 1
                    m/("K"): 2
                    mold keys-of m""")).isEqualTo("{[\"k\"]}");
        }

        @Test
        @DisplayName("and the value it wrote is the one that is there")
        void thevalueIsTheNewOne() {
            assertThat(answerTo("""
                    m: make map! []
                    m/("k"): 1
                    m/("K"): 2
                    select m "k\"""")).isEqualTo("2");
        }

        @Test
        @DisplayName("PUT behaves the same way")
        void putBehavesTheSame() {
            assertThat(answerTo("""
                    m: make map! []
                    put m "k" 1
                    put m "K" 9
                    mold keys-of m""")).isEqualTo("{[\"k\"]}");
            assertThat(answerTo("""
                    m: make map! []
                    put m "k" 1
                    put m "K" 9
                    select m "k\"""")).isEqualTo("9");
        }

        @Test
        @DisplayName("but PUT/CASE adds a second entry")
        void putCaseAddsASecond() {
            assertThat(answerTo("""
                    m: make map! []
                    put m "k" 1
                    put/case m "K" 9
                    2 = length? m""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("building one does mind the case")
    class TheBuilding {

        @Test
        @DisplayName("MAKE keeps two keys that differ only by case")
        void makeKeepsBoth() {
            // The other end of the same rule, and why it is not an
            // inconsistency: a map can hold both, and a caller looking one up
            // need not know which it was written with.
            assertThat(answerTo("""
                    2 = length? make map! ["k" 1 "K" 2]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and each holds its own value")
        void eachHoldsItsOwn() {
            assertThat(answerTo("""
                    m: make map! ["k" 1 "K" 2]
                    select/case m "K\"""")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("removing a key does mind the case")
    class TheRemoval {

        @Test
        @DisplayName("REMOVE/KEY leaves a key of another case alone")
        void removeKeyMindsTheCase() {
            assertThat(answerTo("""
                    m: make map! []
                    m/("k"): 1
                    remove/key m "K"
                    1 = length? m""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and takes the one that matches exactly")
        void removeKeyTakesTheExactOne() {
            assertThat(answerTo("""
                    m: make map! []
                    m/("k"): 1
                    remove/key m "k"
                    0 = length? m""")).isEqualTo("#(true)");
        }
    }
}
