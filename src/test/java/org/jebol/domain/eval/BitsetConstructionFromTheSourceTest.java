package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a bitset is built, which is {@code Make_Bitset} and {@code Set_Bits} in
 * {@code t-bitset.c} and is two rules rather than one.
 *
 * <p>{@code make bitset! 8} asks for room for eight bits and turns none of them
 * on. {@code append bs 8} names the bit. Reading both through one rule made
 * {@code alter} report that it had added a bit and change nothing, which is the
 * shape Rebol's own suite catches at {@code modify / alter}.
 *
 * <p>Inside a block every spec adds to what came before, so two binaries fall
 * together rather than the second replacing the first, and a spec the walk does
 * not recognise is an error instead of being passed over in silence.
 */
class BitsetConstructionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("a number given to MAKE asks for room, and turns no bit on")
    void aNumberAsksForRoom() {
        assertThat(answerTo("""
                make bitset! 8""")).isEqualTo("#(bitset! #{00})");
    }

    @Test
    @DisplayName("the room rounds up to whole octets at each boundary")
    void theRoomRoundsUpToWholeOctets() {
        assertThat(answerTo("""
                collect [
                    foreach wanted [0 1 7 8 9 15 16 17] [
                        keep make bitset! wanted
                    ]
                ]""")).isEqualTo(
                "[#(bitset! #{}) #(bitset! #{00}) #(bitset! #{00}) #(bitset! #{00})"
                        + " #(bitset! #{0000}) #(bitset! #{0000}) #(bitset! #{0000})"
                        + " #(bitset! #{000000})]");
    }

    @Test
    @DisplayName("the room asked for is the length, counted in bits")
    void theRoomAskedForIsTheLength() {
        assertThat(answerTo("""
                reduce [length? make bitset! 0 length? make bitset! 9]"""))
                .isEqualTo("[0 16]");
    }

    @Test
    @DisplayName("no bit in the room asked for is on")
    void theRoomStartsEmpty() {
        assertThat(answerTo("""
                reduce [pick make bitset! 8 0 pick make bitset! 8 7]"""))
                .isEqualTo("[#(false) #(false)]");
    }

    @Test
    @DisplayName("TO reads a number as room, exactly as MAKE does")
    void toReadsANumberAsRoomAsWell() {
        assertThat(answerTo("""
                reduce [to bitset! 8 to bitset! 1]"""))
                .isEqualTo("[#(bitset! #{00}) #(bitset! #{00})]");
    }

    @Test
    @DisplayName("a number given to APPEND names the bit rather than the room")
    void aNumberAppendedNamesTheBit() {
        assertThat(answerTo("""
                append make bitset! 8 5""")).isEqualTo("#(bitset! #{04})");
    }

    @Test
    @DisplayName("a run given to APPEND turns on every bit it spans")
    void aRunAppendedTurnsOnEveryBit() {
        assertThat(answerTo("""
                append make bitset! 8 [1 - 3]""")).isEqualTo("#(bitset! #{70})");
    }

    @Test
    @DisplayName("ALTER adds a bit, then takes the same bit away")
    void alterAddsThenRemoves() {
        assertThat(answerTo("""
                bs: #(bitset! #{00})
                reduce [
                    alter bs 1
                    to binary! bs
                    alter bs 1
                    to binary! bs
                ]""")).isEqualTo("[#(true) #{40} #(false) #{00}]");
    }

    @Test
    @DisplayName("REMOVE/KEY reads a number as the bit too, and empties the set")
    void removeKeyReadsANumberAsTheBit() {
        assertThat(answerTo("""
                bs: charset "012345789"
                reduce [
                    to binary! remove/key bs #"0"
                    to binary! remove/key bs 49
                    to binary! remove/key bs [#"2" - #"7" "8" #"9"]
                ]""")).isEqualTo(
                "[#{0000000000007DC0} #{0000000000003DC0} #{0000000000000000}]");
    }

    @Test
    @DisplayName("two binaries in a block fall together rather than replacing")
    void twoBinariesFallTogether() {
        assertThat(answerTo("""
                make bitset! [#{0002} #{0100}]""")).isEqualTo("#(bitset! #{0102})");
    }

    @Test
    @DisplayName("three binaries fall together the same way")
    void threeBinariesFallTogether() {
        assertThat(answerTo("""
                make bitset! [#{0002} #{0100} #{0010}]"""))
                .isEqualTo("#(bitset! #{0112})");
    }

    @Test
    @DisplayName("a run and a binary in one block are both kept")
    void aRunAndABinaryAreBothKept() {
        assertThat(answerTo("""
                make bitset! [1 - 3 #{80}]""")).isEqualTo("#(bitset! #{F0})");
    }

    @Test
    @DisplayName("the word BITS in front of a binary supplies the octets whole")
    void theWordBitsSuppliesTheOctets() {
        assertThat(answerTo("""
                make bitset! [bits #{0102}]""")).isEqualTo("#(bitset! #{0102})");
    }

    @Test
    @DisplayName("a binary and a string in one block are both kept")
    void aBinaryAndAStringAreBothKept() {
        assertThat(answerTo("""
                make bitset! [#{01} "a"]"""))
                .isEqualTo("#(bitset! #{01000000000000000000000040})");
    }

    @Test
    @DisplayName("NOT at the head complements what the rest of the block names")
    void notAtTheHeadComplements() {
        assertThat(answerTo("""
                reduce [make bitset! [not] make bitset! [not 1]]"""))
                .isEqualTo("[#(bitset! not #{}) #(bitset! not #{40})]");
    }

    @Test
    @DisplayName("a run of characters holds its ends and its middle, and nothing outside")
    void aRunOfCharactersHoldsWhatItSpans() {
        assertThat(answerTo("""
                bs: charset [#"c" - #"f"]
                reduce [
                    find bs #"c"
                    find bs #"d"
                    find bs #"f"
                    find bs #"b"
                    find bs #"g"
                ]""")).isEqualTo("[#(true) #(true) #(true) #(false) #(false)]");
    }

    @Test
    @DisplayName("a run of one character is that character alone")
    void aRunOfOneCharacter() {
        assertThat(answerTo("""
                bs: charset [#"c" - #"c"]
                reduce [find bs #"c" find bs #"b" find bs #"d"]"""))
                .isEqualTo("[#(true) #(false) #(false)]");
    }

    @Test
    @DisplayName("a word the walk does not recognise is an invalid argument")
    void anUnrecognisedWordRaises() {
        assertThat(errorIdFrom("""
                make bitset! [zzz]""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("NOT anywhere but the head names nothing, so it raises")
    void notAwayFromTheHeadRaises() {
        assertThat(errorIdFrom("""
                make bitset! [#{0002} #{0100} not]""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("BITS with nothing usable after it raises")
    void bitsWithoutABinaryRaises() {
        assertThat(errorIdFrom("""
                make bitset! [bits]""")).isEqualTo("invalid-arg");
        assertThat(errorIdFrom("""
                make bitset! [bits "x"]""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a run whose far end is a different kind raises")
    void aMismatchedRunRaises() {
        assertThat(errorIdFrom("""
                make bitset! [#"a" - 5]""")).isEqualTo("invalid-arg");
        assertThat(errorIdFrom("""
                make bitset! [1 - "x"]""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a dash with nothing after it raises against the end of the block")
    void aRunWithNoFarEndRaises() {
        assertThat(errorIdFrom("""
                make bitset! [1 -]""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("a run that ends before it starts is past the end")
    void aBackwardsRunRaises() {
        assertThat(errorIdFrom("""
                make bitset! [3 - 1]""")).isEqualTo("past-end");
    }

    @Test
    @DisplayName("a bit below zero names nothing a set could hold")
    void aNegativeBitRaises() {
        assertThat(errorIdFrom("""
                make bitset! -1""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("a fraction is no answer to how much room to make")
    void aFractionRaises() {
        assertThat(errorIdFrom("""
                make bitset! 1.5""")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("INSERT after asking for room keeps the room and adds the bits")
    void insertKeepsTheRoomAndAddsTheBits() {
        assertThat(answerTo("""
                bs: make bitset! 8
                insert bs ["hello" #"x" - #"z"]
                reduce [length? bs bs]""")).isEqualTo(
                "[128 #(bitset! #{000000000000000000000000048900E0})]");
    }

    @Test
    @DisplayName("MAKE from another bitset copies it, so writing to one leaves the other")
    void makeFromABitsetCopies() {
        assertThat(answerTo("""
                b1: charset ""
                b2: make bitset! b1
                b2/1: true
                reduce [b1 b2]""")).isEqualTo("[#(bitset! #{}) #(bitset! #{40})]");
    }

    @Test
    @DisplayName("a set and its complement are never equal, however the question is put")
    void aSetNeverEqualsItsComplement() {
        assertThat(answerTo("""
                b: make bitset! #{00}
                reduce [
                    equiv? b complement b
                    equal? b complement b
                    strict-equal? b complement b
                    same? b complement b
                ]""")).isEqualTo("[#(false) #(false) #(false) #(false)]");
    }

    @Test
    @DisplayName("two complements of the same set are equal to each other")
    void twoComplementsAreEqual() {
        assertThat(answerTo("""
                (complement make bitset! #{00}) = (complement make bitset! #{00})"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("two sets holding nothing differ when they were given different room")
    void differentRoomMeansDifferentSets() {
        assertThat(answerTo("""
                (make bitset! 1) = (make bitset! 9)""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("FIND takes either case of a character, and /case takes only the one")
    void findTakesEitherCase() {
        assertThat(answerTo("""
                letters: charset [#"A" #"B" #"C"]
                reduce [
                    find letters #"a"
                    find letters #"A"
                    find/case letters #"A"
                    find/case letters #"a"
                ]""")).isEqualTo("[#(true) #(true) #(true) #(false)]");
    }

    @Test
    @DisplayName("a number naming the same code point is always asked exactly")
    void aNumberIsAlwaysCased() {
        assertThat(answerTo("""
                letters: charset [#"A" #"B" #"C"]
                reduce [find letters to-integer #"A" find letters to-integer #"a"]"""))
                .isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("PICK never folds case, whatever FIND does")
    void pickNeverFoldsCase() {
        assertThat(answerTo("""
                letters: charset [#"A" #"B" #"C"]
                reduce [pick letters #"A" pick letters #"a"]"""))
                .isEqualTo("[#(true) #(false)]");
    }

    @Test
    @DisplayName("a logic is no index into a bitset, for PICK or for POKE")
    void aLogicIsNoIndex() {
        assertThat(errorIdFrom("""
                pick make bitset! #{C0} true""")).isEqualTo("invalid-type");
        assertThat(errorIdFrom("""
                poke make bitset! #{C0} true none""")).isEqualTo("invalid-type");
    }

    @Test
    @DisplayName("PICKZ answers what PICK does, because a bitset counts from nought")
    void pickzMatchesPick() {
        assertThat(answerTo("""
                b: make bitset! [4]
                reduce [pickz b 4 pick b 4 b/4 find b 4]"""))
                .isEqualTo("[#(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("POKE takes the same block grammar MAKE does")
    void pokeTakesABlockOfSpecs() {
        assertThat(answerTo("""
                bs: complement charset ""
                poke bs [32 - 40] none
                bs""")).isEqualTo("#(bitset! not #{00000000FF80})");
    }

    @Test
    @DisplayName("POKE through a block puts back what it took out")
    void pokeThroughABlockPutsBack() {
        assertThat(answerTo("""
                bs: complement charset ""
                poke bs [32 - 40] none
                poke bs [32 - 40] true
                bs""")).isEqualTo("#(bitset! not #{000000000000})");
    }

    @Test
    @DisplayName("COPY carries the octets and loses the complement, as REBOL does")
    void copyLosesTheComplement() {
        assertThat(answerTo("""
                b: complement charset "a"
                reduce [b copy b complement? copy b]""")).isEqualTo(
                "[#(bitset! not #{00000000000000000000000040})"
                        + " #(bitset! #{00000000000000000000000040}) #(false)]");
    }

    @Test
    @DisplayName("MAKE from a complemented set loses it the same way")
    void makeFromAComplementedSetLosesIt() {
        assertThat(answerTo("""
                b: complement charset "a"
                reduce [make bitset! b to bitset! b]""")).isEqualTo(
                "[#(bitset! #{00000000000000000000000040})"
                        + " #(bitset! #{00000000000000000000000040})]");
    }

    @Test
    @DisplayName("so the copy answers the opposite of what the original answers")
    void theCopyAnswersTheOpposite() {
        assertThat(answerTo("""
                b: complement charset "a"
                reduce [find b #"a" find copy b #"a"]""")).isEqualTo("[#(false) #(true)]");
    }

    @Test
    @DisplayName("CLEAR leaves no room at all")
    void clearLeavesNoRoom() {
        assertThat(answerTo("""
                bs: charset "^(00)^(01)^(02)^(03)^(04)^(05)^(06)^(07)"
                filled: copy bs
                clear bs
                reduce [filled length? bs bs]"""))
                .isEqualTo("[#(bitset! #{FF}) 0 #(bitset! #{})]");
    }
}
