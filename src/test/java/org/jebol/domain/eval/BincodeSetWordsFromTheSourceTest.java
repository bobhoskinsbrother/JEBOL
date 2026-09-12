package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BincodeSetWordsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("a set-word names the value the next code reads")
    void aSetWordNamesTheValueTheNextCodeReads() {
        assertThat(answerTo("""
                reduce [binary/read #{0102} [a: UI8 UI8] a]"""))
                .isEqualTo("[[1 2] 1]");
    }

    @Test
    @DisplayName("a set-word with no code after it names nothing")
    void aSetWordWithNoCodeAfterItNamesNothing() {
        assertThat(answerTo("""
                spare: 99
                reduce [binary/read #{01} [UI8 spare:] spare]"""))
                .isEqualTo("[[1] 99]");
    }

    @Test
    @DisplayName("a block of only a set-word reads nothing")
    void aBlockOfOnlyASetWordReadsNothing() {
        assertThat(answerTo("""
                lone: 99
                reduce [binary/read #{01} [lone:] lone]"""))
                .isEqualTo("[[] 99]");
    }

    @Test
    @DisplayName("a set-word overwrites what the word already held")
    void aSetWordOverwritesWhatTheWordAlreadyHeld() {
        assertThat(answerTo("""
                held: 99
                binary/read #{07} [held: UI8]
                held""")).isEqualTo("7");
    }

    @Test
    @DisplayName("naming the same word twice keeps the second value")
    void namingTheSameWordTwiceKeepsTheSecondValue() {
        assertThat(answerTo("""
                binary/read #{0A0B} [twice: UI8 twice: UI8]
                twice""")).isEqualTo("11");
    }

    @Test
    @DisplayName("a name given before a code that raises still stands")
    void anEarlierNameSurvivesALaterRefusal() {
        assertThat(answerTo("""
                early: 5
                try [binary/read #{01} [early: UI8 UI64]]
                early""")).isEqualTo("1");
    }

    @Test
    @DisplayName("a length named on one code is spent on the next")
    void aLengthNamedOnOneCodeIsSpentOnTheNext() {
        assertThat(answerTo("""
                binary/read #{02414243} [n: UI8 BYTES :n]"""))
                .isEqualTo("[2 #{4142}]");
    }

    @Test
    @DisplayName("a count of nothing reads no bytes")
    void aCountOfNothingReadsNoBytes() {
        assertThat(answerTo("""
                binary/read #{0041} [n: UI8 BYTES :n UI8]"""))
                .isEqualTo("[0 #{} 65]");
    }

    @Test
    @DisplayName("a position named earlier moves the read cursor")
    void aPositionNamedEarlierMovesTheReadCursor() {
        assertThat(answerTo("""
                binary/read #{0301020304} [p: UI8 AT :p UI8]"""))
                .isEqualTo("[3 2]");
    }

    @Test
    @DisplayName("a skip named earlier moves the read cursor")
    void aSkipNamedEarlierMovesTheReadCursor() {
        assertThat(answerTo("""
                binary/read #{020A0B0C0D} [s: UI8 SKIP :s UI8]"""))
                .isEqualTo("[2 12]");
    }

    @Test
    @DisplayName("a width named earlier sizes a bit field")
    void aWidthNamedEarlierSizesABitField() {
        assertThat(answerTo("""
                binary/read #{04F0} [w: UI8 UB :w]"""))
                .isEqualTo("[4 15]");
    }

    @Test
    @DisplayName("the name lasts for the rest of the block, not just the next code")
    void theNameLastsForTheRestOfTheBlock() {
        assertThat(answerTo("""
                binary/read #{020941424344} [n: UI8 UI8 BYTES :n]"""))
                .isEqualTo("[2 9 #{4142}]");
    }

    @Test
    @DisplayName("the end-of-central-directory record Rebol's own ZIP codec reads")
    void theRecordRebolsZipCodecReads() {
        assertThat(answerTo("""
                binary/read #{000000000100010050000000000000000300414243} [
                    UI16LE UI16LE UI16LE UI16LE UI32LE
                    pos: UI32LE len: UI16LE com: BYTES :len
                ]""")).isEqualTo("[0 0 1 1 80 0 3 #{414243}]");
    }

    @Test
    @DisplayName("two set-words in a row name the one value, and add nothing")
    void twoSetWordsInARowNameTheOneValue() {
        assertThat(answerTo("""
                reduce [binary/read #{01} [a: b: UI8] a b]"""))
                .isEqualTo("[[1] 1 1]");
    }

    @Test
    @DisplayName("three set-words in a row all name the one value")
    void threeSetWordsInARowNameTheOneValue() {
        assertThat(answerTo("""
                reduce [binary/read #{09} [x: y: z: UI8] x y z]"""))
                .isEqualTo("[[9] 9 9 9]");
    }

    @Test
    @DisplayName("a set-word adds no entry to what reading answers")
    void aSetWordAddsNoEntryToTheAnswer() {
        assertThat(answerTo("""
                binary/read #{010203} [a: UI8 UI8 UI8]"""))
                .isEqualTo("[1 2 3]");
    }

    @Test
    @DisplayName("a code that only moves the cursor leaves the set-word waiting")
    void aPositioningCodeLeavesTheSetWordWaiting() {
        assertThat(answerTo("""
                reduce [
                    binary/read #{0A0B0C} [m: AT 3 UI8] m
                    binary/read #{0A0B0C} [m: ATZ 2 UI8] m
                    binary/read #{0A0B0C} [m: SKIP 2 UI8] m
                    binary/read #{0A0B0C0D0E0F} [UI8 m: PAD 4 UI8] m
                ]""")).isEqualTo("[[12] 12 [12] 12 [12] 12 [10 14] 14]");
    }

    @Test
    @DisplayName("INDEX produces a value, so it answers a waiting set-word")
    void indexProducesAValueSoItAnswersASetWord() {
        assertThat(answerTo("""
                reduce [
                    binary/read #{0A0B} [m: INDEX UI8] m
                    binary/read #{0A0B} [m: INDEXZ UI8] m
                ]""")).isEqualTo("[[1 10] 1 [0 10] 0]");
    }

    @Test
    @DisplayName("a set-word in a write block names a one-based position")
    void aSetWordInAWriteBlockNamesAOneBasedPosition() {
        assertThat(answerTo("""
                b: binary 0 binary/write b [at-one: UI8 255]
                c: binary 0 binary/write c [UI8 255 at-two: UI8 254]
                d: binary 0 binary/write d [UI32 1 at-five: UI8 254]
                reduce [at-one at-two at-five]""")).isEqualTo("[1 2 5]");
    }

    @Test
    @DisplayName("a write block of only a set-word names the head")
    void aWriteBlockOfOnlyASetWordNamesTheHead() {
        assertThat(answerTo("""
                b: binary 0
                binary/write b [head-of-it:]
                reduce [head-of-it b/buffer]""")).isEqualTo("[1 #{}]");
    }

    @Test
    @DisplayName("two set-words in a write block name the one position")
    void twoSetWordsInAWriteBlockNameTheOnePosition() {
        assertThat(answerTo("""
                b: binary 0
                binary/write b [p: q: UI8 9]
                reduce [p q]""")).isEqualTo("[1 1]");
    }

    @Test
    @DisplayName("a set-word after written bytes names where they end")
    void aSetWordAfterWrittenBytesNamesWhereTheyEnd() {
        assertThat(answerTo("""
                b: binary 0
                binary/write b [BYTES #{01020304} p: UI8 9]
                reduce [p b/buffer]""")).isEqualTo("[5 #{0102030409}]");
    }

    @Test
    @DisplayName("moving the write cursor moves where a set-word lands")
    void movingTheWriteCursorMovesWhereASetWordLands() {
        assertThat(answerTo("""
                b: binary 0
                binary/write b [UI32 0 AT 2 p: UI8 9]
                reduce [p b/buffer]""")).isEqualTo("[2 #{00090000}]");
    }

    @Test
    @DisplayName("a name holding text where a count is wanted is refused")
    void aCountThatIsNotANumberIsRefused() {
        assertThat(errorIdFrom("""
                not-a-count: "nope"
                binary/read #{02414243} [n: UI8 BYTES :not-a-count]"""))
                .isEqualTo("invalid-spec");
    }

    @Test
    @DisplayName("a count outside the data is refused, negative or too large")
    void aCountOutsideTheDataIsRefused() {
        assertThat(errorIdFrom("""
                binary/read #{FF414243} [n: SI8 BYTES :n]"""))
                .isEqualTo("out-of-range");
        assertThat(errorIdFrom("""
                binary/read #{0941} [n: UI8 BYTES :n]"""))
                .isEqualTo("out-of-range");
    }
}
