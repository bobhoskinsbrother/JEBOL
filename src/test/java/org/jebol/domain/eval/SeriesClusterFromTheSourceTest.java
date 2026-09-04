package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A batch of series behaviors from {@code series-test.r3}, each read from
 * the C: FORM drops a word's sigil, AJOIN drops an unset, REDUCE/INTO fills
 * any block-family target, FIND/SAME asks series identity, and POKE writes a
 * char into a binary.
 */
class SeriesClusterFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("FORM drops a word's sigil where MOLD keeps it")
    class FormDropsSigils {

        @Test
        @DisplayName("a lit-word forms to its bare name")
        void aLitWordFormsBare() {
            assertThat(answerTo("""
                    form ['a 'b 3]""")).isEqualTo("\"a b 3\"");
        }

        @Test
        @DisplayName("every any-word datatype forms bare")
        void everyAnyWordFormsBare() {
            assertThat(answerTo("""
                    form [/a b: :c #d]""")).isEqualTo("\"a b c d\"");
        }

        @Test
        @DisplayName("MOLD still keeps the sigils so the block reads back")
        void moldKeepsSigils() {
            assertThat(answerTo("""
                    mold ['a b:]""")).isEqualTo("\"['a b:]\"");
        }
    }

    @Test
    @DisplayName("AJOIN drops an unset piece, visible only with a separator")
    void ajoinDropsAnUnset() {
        assertThat(answerTo("""
                {a/3} == ajoin/with [{a} #(unset) 3] to char! 47""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                {a//3} == ajoin/all/with [{a} #(unset) 3] to char! 47"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REDUCE/INTO fills a path target, not only a block")
    void reduceIntoFillsAPath() {
        assertThat(answerTo("""
                tail? reduce/into ['a 1 + 1 3 + 3] p: make path! 3
                p = 'a/2/6""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FIND/SAME on a block finds by series identity, not equality")
    void findSameUsesIdentity() {
        assertThat(answerTo("""
                a: {x} b: {y}
                blk: reduce [{x} {y} a b]
                3 = index? find/same blk reduce [a b]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("POKE writes a char into a binary as its byte")
    void pokeWritesACharIntoABinary() {
        assertThat(answerTo("""
                s: to binary! {abc} poke s 1 to char! 122 s = to binary! {zbc}"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("POKE refuses a char that will not fit a byte")
    void pokeRefusesAWideChar() {
        assertThat(answerTo("""
                e: try [poke to binary! {a} 1 to char! 256] e/id"""))
                .isEqualTo("out-of-range");
    }
}
