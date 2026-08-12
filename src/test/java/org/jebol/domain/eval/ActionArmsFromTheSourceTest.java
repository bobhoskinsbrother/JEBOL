package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Specified in {@code spec/natives.allium} under "Reading and writing the
 * bits of a set" and "Adding to a map, and asking an object whether", read
 * from {@code s-ops.c}, {@code t-bitset.c}, {@code t-object.c} and
 * {@code series-test.r3}.
 */
class ActionArmsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("COMPLEMENT on a binary")
    class ComplementOnABinary {

        @Test
        @DisplayName("answers new bytes with every bit flipped")
        void answersNewFlippedBytes() {
            assertThat(answerTo("""
                    complement #{0102}""")).isEqualTo("#{FEFD}");
        }

        @Test
        @DisplayName("leaves the original untouched")
        void leavesTheOriginalUntouched() {
            assertThat(answerTo("""
                    b: #{01} complement b b""")).isEqualTo("#{01}");
        }

        @Test
        @DisplayName("reads from the series position")
        void readsFromTheSeriesPosition() {
            assertThat(answerTo("""
                    complement next #{0102}""")).isEqualTo("#{FD}");
        }

        @Test
        @DisplayName("an empty binary flips to an empty binary")
        void anEmptyBinaryStaysEmpty() {
            assertThat(answerTo("""
                    complement #{}""")).isEqualTo("#{}");
        }
    }

    @Nested
    @DisplayName("INSERT on a bitset is APPEND on a bitset")
    class InsertOnABitset {

        @Test
        @DisplayName("a char becomes a member, and the set itself is the answer")
        void aCharBecomesAMember() {
            assertThat(answerTo("""
                    bs: charset {a} same? bs insert bs #"b\"""")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    bs: charset {a} insert bs #"b" find bs #"b\"""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string contributes each of its characters")
        void aStringContributesEachCharacter() {
            assertThat(answerTo("""
                    bs: charset {} insert bs {bc}
                    reduce [find bs #"b" find bs #"c" find bs #"d"]"""))
                    .isEqualTo("[#(true) #(true) #(false)]");
        }

        @Test
        @DisplayName("a block range contributes the whole range")
        void aBlockRangeContributesTheRange() {
            assertThat(answerTo("""
                    bs: charset {} insert bs [#"a" - #"c"]
                    reduce [find bs #"a" find bs #"c" find bs #"d"]"""))
                    .isEqualTo("[#(true) #(true) #(false)]");
        }

        @Test
        @DisplayName("adding to a complemented set still means membership")
        void addingToAComplementedSetStillMeansMembership() {
            assertThat(answerTo("""
                    bs: complement charset {b}
                    reduce [find bs #"b" find insert bs #"b" #"b"]"""))
                    .isEqualTo("[#(false) #(true)]");
        }

        @Test
        @DisplayName("something that names no bits is refused")
        void somethingThatNamesNoBitsIsRefused() {
            assertThat(errorIdOf("""
                    insert charset {a} 1.5""")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("REMOVE on a bitset clears members")
    class RemoveOnABitset {

        @Test
        @DisplayName("/key clears the members it names, answering the set")
        void keyClearsWhatItNames() {
            assertThat(answerTo("""
                    bs: charset {ab} remove/key bs #"a"
                    reduce [find bs #"a" find bs #"b"]"""))
                    .isEqualTo("[#(false) #(true)]");
            assertThat(answerTo("""
                    bs: charset {a} same? bs remove/key bs #"a\"""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/key with a string clears each of its characters")
        void keyWithAStringClearsEach() {
            assertThat(answerTo("""
                    bs: charset {abc} remove/key bs {ab}
                    reduce [find bs #"a" find bs #"c"]"""))
                    .isEqualTo("[#(false) #(true)]");
        }

        @Test
        @DisplayName("/part clears a range")
        void partClearsARange() {
            assertThat(answerTo("""
                    bs: charset [#"a" - #"z"] remove/part bs [#"a" - #"y"]
                    reduce [find bs #"a" find bs #"z"]"""))
                    .isEqualTo("[#(false) #(true)]");
        }

        @Test
        @DisplayName("/part with a range that is not one of the four kinds is refused")
        void partOfTheWrongKindIsRefused() {
            assertThat(errorIdOf("""
                    remove/part charset {a} 5""")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("asking both ways at once is refused")
        void bothWaysAtOnceIsRefused() {
            assertThat(errorIdOf("""
                    remove/key/part charset {ab} #"a" #"b\"""")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("saying neither way raises missing-arg")
        void neitherWayRaisesMissingArg() {
            assertThat(errorIdOf("""
                    remove charset {a}""")).isEqualTo("missing-arg");
        }
    }

    @Nested
    @DisplayName("INSERT on an object is APPEND on an object")
    class InsertOnAnObject {

        @Test
        @DisplayName("a bare word adds a field holding unset")
        void aWordAddsAnUnsetField() {
            assertThat(answerTo("""
                    o: make object! [] insert o 'f mold words-of o"""))
                    .isEqualTo("\"[f]\"");
        }

        @Test
        @DisplayName("a block is read as word and value pairs, answering the object")
        void aBlockIsReadAsPairs() {
            assertThat(answerTo("""
                    o: make object! [] insert o [a 1 b 2] reduce [o/a o/b]"""))
                    .isEqualTo("[1 2]");
            assertThat(answerTo("""
                    o: make object! [] same? o insert o [a 1]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/dup with a count of zero does nothing at all")
        void duplicatingZeroTimesDoesNothing() {
            assertThat(answerTo("""
                    o: make object! [] insert/dup o [c 3] 0 mold words-of o"""))
                    .isEqualTo("\"[]\"");
        }
    }

    @Nested
    @DisplayName("PUT on an object")
    class PutOnAnObject {

        @Test
        @DisplayName("a key that is not a word is refused as the argument it is")
        void aKeyThatIsNotAWordIsRefused() {
            assertThat(errorIdOf("""
                    put make object! [a: 1] 1 2""")).isEqualTo("invalid-arg");
        }
    }

    @Test
    @DisplayName("REMOVE/KEY on an ordinary series is not a feature")
    void removeKeyOnAnOrdinarySeriesIsNotAFeature() {
        assertThat(errorIdOf("""
                remove/key {abcd} #"a\"""")).isEqualTo("feature-na");
    }
}
