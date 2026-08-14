package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What TRANSCODE hands back, which is not the same question as what it reads.
 *
 * <p>One line of {@code REBNATIVE(transcode)} in
 * {@code rebol3-source/src/core/l-scan.c} decides the whole shape:
 *
 * <pre>
 * // Scan_Code clears the next flag!
 * // Decide if result should contain also modified input position.
 * // (with refinements /next, /only and /error)
 * next = scan_state.opts &gt; 0;
 * </pre>
 *
 * <p>{@code opts} is raised by /NEXT, /ONE, /ONLY and /ERROR, and by nothing else. So
 * the local named {@code next} means "the caller asked to stop before the end", and the
 * unread text is appended for all of them. /ONE is the exception, and only because it
 * returns two lines earlier with the value alone.
 *
 * <p>Read as a name the line looks like a bug. Read as a question -- is this caller
 * stepping through a source -- the other three cases fall out of it, because a caller
 * who asked for the failure as a value has the same reason to want the rest as a
 * caller who asked for one value.
 */
class TranscodeAnswerShapeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String call) {
        return answerTo("e: try [" + call + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a caller reading the whole source gets the values and nothing else")
    class TheWholeSource {

        @Test
        @DisplayName("no unread text, because there is none")
        void theWholeSourceHasNoRemainder() {
            assertThat(answerTo("""
                    [1 2] = transcode "1 2\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    2 = length? transcode "1 2\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an empty source is an empty block rather than a failure")
        void anEmptySourceIsAnEmptyBlock() {
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
        void nextKeepsABlockWhole() {
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
        void anUnclosedBlockIsNotAFailureUnderOnly() {
            assertThat(answerTo("""
                    error? try [transcode "[1"]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [[1] ""] = transcode/only "[1\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the two are the same block read two ways, which is the pair to read")
        void theContrastBetweenTheTwo() {
            assertThat(answerTo("""
                    [1 2] = first transcode/next "[1 2]\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1] = first transcode/only "[1 2]\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/one answers the value alone, with no room for the rest")
        void oneAnswersTheValueAlone() {
            assertThat(answerTo("""
                    1 = transcode/one "1 2\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1 2] = transcode/one "[1 2]\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and what is left comes back as the kind that went in")
        void theRemainderKeepsItsKind() {
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
        void errorAloneAppendsTheRest() {
            assertThat(answerTo("""
                    [1 2 ""] = transcode/error "1 2\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    3 = length? transcode/error "1 2\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the values read before the failure survive it")
        void theValuesBeforeTheFailureSurvive() {
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
        void aFailureOnTheFirstValue() {
            assertThat(answerTo("""
                    all [
                        block? blk: transcode/error "1d"
                        error? blk/1
                        blk/1/id = 'invalid
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and Rebol's own binary case, where the remainder is the unread byte")
        void theBinaryCaseFromRebolsSuite() {
            assertThat(answerTo("""
                    all [
                        block? e: transcode/error to binary! {"test^/}
                        error? e/1
                        e/2 = #{0A}
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but without it a failure is raised, which is what makes it worth asking for")
        void withoutItTheFailureIsRaised() {
            assertThat(errorIdOf("""
                    transcode "1 2d\"""")).isEqualTo("invalid");
            assertThat(errorIdOf("""
                    transcode/next "1d\"""")).isEqualTo("invalid");
            assertThat(errorIdOf("""
                    transcode/only "1d\"""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a failure under /next is not an answer of none")
        void aFailureIsNotNone() {
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
        void theTwoTheSuiteAsserts() {
            assertThat(errorIdOf("""
                    transcode/next "\"""")).isEqualTo("past-end");
            assertThat(errorIdOf("""
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
            assertThat(errorIdOf("""
                    transcode/only "\"""")).isEqualTo("past-end");
            assertThat(answerTo("""
                    all [error? e: transcode/only/error "" e/id = 'past-end]"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /error alone, which answers the failure rather than raising it")
        void errorAloneAnswersPastEnd() {
            assertThat(answerTo("""
                    all [error? e: transcode/error "" e/id = 'past-end]"""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and whitespace alone is nothing, for every one of them")
        void whitespaceIsNothing() {
            assertThat(errorIdOf("""
                    transcode/next "  ^/ \"""")).isEqualTo("past-end");
            assertThat(errorIdOf("""
                    transcode/only "  ^/ \"""")).isEqualTo("past-end");
            assertThat(errorIdOf("""
                    transcode/one ";just a comment\"""")).isEqualTo("past-end");
        }
    }

    @Nested
    @DisplayName("and the line count follows the unread text, wherever that goes")
    class TheLineCountAlongside {

        @Test
        @DisplayName("under /only, which is the shape Rebol's own loader reads headers with")
        void underOnly() {
            assertThat(answerTo("""
                    [1 " 2" 1] = transcode/only/line "1 2" 1""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1 "^/2" 41] = transcode/only/line "1^/2" 41""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and under /error, after the values and the unread text")
        void underError() {
            assertThat(answerTo("""
                    [1 2 "" 10] = transcode/error/line "1 2" 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but not under /one, which has nothing to append it to")
        void notUnderOne() {
            assertThat(answerTo("""
                    1 = transcode/one/line "1 2" 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and not without one of the three, because nothing was appended")
        void notOnItsOwn() {
            assertThat(answerTo("""
                    [1 2] = transcode/line "1 2" 10""")).isEqualTo(TRUE);
        }
    }
}
