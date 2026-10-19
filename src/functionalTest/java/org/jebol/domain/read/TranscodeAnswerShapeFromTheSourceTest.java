package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscodeAnswerShapeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String call) {
        return answerTo("e: try [" + call + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a caller reading the whole source gets the values and nothing else")
    class TheWholeSource {

        @Test
        @DisplayName("no unread text, because there is none")
        void leavesNoUnreadText() {
            assertThat(answerTo("""
                    [1 2] = transcode "1 2\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    2 = length? transcode "1 2\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an empty source is an empty block rather than a failure")
        void answersAnEmptyBlockForAnEmptySource() {
            assertThat(answerTo("""
                    [] = transcode "\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [] = transcode "  ^/  \"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a caller reading one value gets the unread text after it")
    class OneValueAndTheRest {

        @Test
        @DisplayName("/next keeps a block whole, because a block is one value")
        void keepsABlockWholeUnderNext() {
            assertThat(answerTo("""
                    [1 " + 1"] = transcode/next "1 + 1\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [[1 + 1] ""] = transcode/next "[1 + 1]\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/only reads one value too, and answers a block as /next does")
        void onlyReadsOneValueAndTheRest() {
            assertThat(answerTo("""
                    [1 " 2"] = transcode/only "1 2\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but /only takes one value at every depth, so a block is dissected")
        void onlyDissectsBlocks() {
            assertThat(answerTo("""
                    [[1] " 2]"] = transcode/only "[1 2]\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [[[1]] " 2] 3]"] = transcode/only "[[1 2] 3]\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and skipping that check means an unclosed block is not a failure")
        void acceptsAnUnclosedBlockUnderOnly() {
            assertThat(answerTo("""
                    error? try [transcode "[1"]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [[1] ""] = transcode/only "[1\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the two are the same block read two ways, which is the pair to read")
        void readsTheSameBlockTwoWays() {
            assertThat(answerTo("""
                    [1 2] = first transcode/next "[1 2]\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1] = first transcode/only "[1 2]\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/one answers the value alone, with no room for the rest")
        void answersTheValueAloneUnderOne() {
            assertThat(answerTo("""
                    1 = transcode/one "1 2\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1 2] = transcode/one "[1 2]\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and what is left comes back as the type that went in")
        void keepsTheKindOfTheRemainder() {
            assertThat(answerTo("""
                    [1 #{202B2031}] = transcode/next to binary! "1 + 1\""""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("""
                    [[1 + 1] #{}] = transcode/next to binary! "[1 + 1]\""""))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and asking for the failure as a value counts as stopping early too")
    class TheFailureAsAValue {

        @Test
        @DisplayName("/error alone appends the unread text, with no other refinement")
        void appendsTheUnreadTextUnderErrorAlone() {
            assertThat(answerTo("""
                    [1 2 ""] = transcode/error "1 2\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    3 = length? transcode/error "1 2\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the values read before the failure survive it")
        void keepsTheValuesReadBeforeTheFailure() {
            assertThat(answerTo("""
                    all [
                        block? blk: transcode/error "1 2d"
                        blk/1 = 1
                        error? blk/2
                        blk/2/id = 'invalid
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a failure on the very first value leaves the error at the head")
        void leavesTheErrorAtTheHeadOnTheFirstValue() {
            assertThat(answerTo("""
                    all [
                        block? blk: transcode/error "1d"
                        error? blk/1
                        blk/1/id = 'invalid
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and Rebol's own binary case, where the remainder is the unread byte")
        void leavesTheUnreadByteAsTheRemainder() {
            assertThat(answerTo("""
                    all [
                        block? e: transcode/error to binary! {"test^/}
                        error? e/1
                        e/2 = #{0A}
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but without it a failure is raised, which is what makes it worth asking for")
        void isRaisedWithoutTheRefinement() {
            assertThat(readTheErrorIdOf("""
                    transcode "1 2d\"""")).isEqualTo("invalid");
            assertThat(readTheErrorIdOf("""
                    transcode/next "1d\"""")).isEqualTo("invalid");
            assertThat(readTheErrorIdOf("""
                    transcode/only "1d\"""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a failure under /next is not an answer of none")
        void isNotNoneUnderNext() {
            assertThat(answerTo("""
                    not error? transcode/next/error "1d\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    all [
                        blk: transcode/next/error "1d"
                        block? blk
                        error? blk/1
                    ]""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and asking for a value where there is none fails, however the caller asked")
    class NothingLeftToRead {

        @Test
        @DisplayName("/next and /one, which Rebol's own suite asserts")
        void failsUnderNextAndOne() {
            assertThat(readTheErrorIdOf("""
                    transcode/next "\"""")).isEqualTo("past-end");
            assertThat(readTheErrorIdOf("""
                    transcode/one "\"""")).isEqualTo("past-end");
            assertThat(answerTo("""
                    all [error? e: transcode/next/error "" e/id = 'past-end]"""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("""
                    all [error? e: transcode/one/error "" e/id = 'past-end]"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /only, by the same one condition")
        void onlyFailsTheSameWay() {
            assertThat(readTheErrorIdOf("""
                    transcode/only "\"""")).isEqualTo("past-end");
            assertThat(answerTo("""
                    all [error? e: transcode/only/error "" e/id = 'past-end]"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /error alone, which answers the failure rather than raising it")
        void answersTheFailureUnderErrorAlone() {
            assertThat(answerTo("""
                    all [error? e: transcode/error "" e/id = 'past-end]"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and whitespace alone is nothing, for every one of them")
        void whitespaceIsNothing() {
            assertThat(readTheErrorIdOf("""
                    transcode/next "  ^/ \"""")).isEqualTo("past-end");
            assertThat(readTheErrorIdOf("""
                    transcode/only "  ^/ \"""")).isEqualTo("past-end");
            assertThat(readTheErrorIdOf("""
                    transcode/one ";just a comment\"""")).isEqualTo("past-end");
        }
    }

    @Nested
    @DisplayName("and the line count follows the unread text, wherever that goes")
    class TheLineCountAlongside {

        @Test
        @DisplayName("under /only, which is the shape Rebol's own loader reads headers with")
        void followsTheUnreadTextUnderOnly() {
            assertThat(answerTo("""
                    [1 " 2" 1] = transcode/only/line "1 2" 1""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1 "^/2" 41] = transcode/only/line "1^/2" 41""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and under /error, after the values and the unread text")
        void followsTheUnreadTextUnderError() {
            assertThat(answerTo("""
                    [1 2 "" 10] = transcode/error/line "1 2" 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but not under /one, which has nothing to append it to")
        void isLeftOutUnderOne() {
            assertThat(answerTo("""
                    1 = transcode/one/line "1 2" 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and not without one of the three, because nothing was appended")
        void isLeftOutWithoutARefinement() {
            assertThat(answerTo("""
                    [1 2] = transcode/line "1 2" 10""")).isEqualTo(TRUE);
        }
    }
}
