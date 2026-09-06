package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What COMPRESS/LEVEL means, asked of every method rather than of one.
 *
 * <p>The seven methods have seven different ranges -- three settings for
 * CRUSH, twelve for the deflate family -- and none of them refuses a number
 * outside its own. A level too high is the highest it has; a level below zero
 * is also the highest it has, because the C takes the level unsigned and
 * {@code MIN(top, level)} sends every negative number upward.
 *
 * <p>Minus one is the exception, and it is the one number that means two
 * things. "Nobody asked" is spelled as an unsigned value of all ones, so a
 * caller writing -1 gets the method's own default. For the five methods whose
 * default is already the hardest setting the two readings coincide and nothing
 * shows; for LZMA and Brotli they differ, and that is where it is worth
 * testing.
 *
 * <p>The assertions here are relations -- this level answers what that level
 * answers -- rather than bytes, on purpose. Three of the seven methods are not
 * byte-exact with a real 3.22.5 and never will be without porting libdeflate,
 * so quoting its checksums would test five methods and skip two. Every
 * relation below was measured on {@code ./r3-head} across all seven methods at
 * eighteen levels each.
 */
class CompressionLevelFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The seven this build offers, and the highest setting each of them has. */
    private static final String THE_METHODS_AND_THEIR_TOPS =
            "methods: [zlib deflate gzip crush lzw lzma br]\n"
                    + "tops: [12 12 12 2 9 9 11]\n"
                    + "data: {Lorem ipsum dolor sit amet, consectetur adipisici "
                    + "elit, sed eiusmod tempor incidunt ut labore.}\n";

    private static final String ALL_SEVEN_TRUE =
            "[#(true) #(true) #(true) #(true) #(true) #(true) #(true)]";

    @Nested
    @DisplayName("a level outside the method's range")
    class OutsideTheRange {

        @Test
        @DisplayName("one above the top answers what the top answers")
        void oneAboveTheTop() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    collect [
                        repeat at length? methods [
                            method: pick methods at
                            top: pick tops at
                            keep (compress/level data method top + 1)
                                = compress/level data method top
                        ]
                    ]""")).isEqualTo(ALL_SEVEN_TRUE);
        }

        @Test
        @DisplayName("and so does a level far above it")
        void farAboveTheTop() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    collect [
                        repeat at length? methods [
                            method: pick methods at
                            keep (compress/level data method 99)
                                = compress/level data method pick tops at
                        ]
                    ]""")).isEqualTo(ALL_SEVEN_TRUE);
        }

        /**
         * Upward, not downward. This is the one that would go the other way if
         * anybody wrote the range check the obvious way, and it is the reason
         * the spec says clamped rather than checked.
         */
        @Test
        @DisplayName("a level below zero answers what the top answers, not the bottom")
        void belowZeroClampsUpward() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    collect [
                        repeat at length? methods [
                            method: pick methods at
                            keep (compress/level data method -2)
                                = compress/level data method pick tops at
                        ]
                    ]""")).isEqualTo(ALL_SEVEN_TRUE);
        }

        @Test
        @DisplayName("and so does a level far below it")
        void farBelowZero() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    collect [
                        repeat at length? methods [
                            method: pick methods at
                            keep (compress/level data method -99)
                                = compress/level data method pick tops at
                        ]
                    ]""")).isEqualTo(ALL_SEVEN_TRUE);
        }

        @Test
        @DisplayName("none of the seven refuses any of those")
        void noneOfThemRefuses() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    refused: copy []
                    foreach method methods [
                        foreach level [-99 -2 -1 0 1 13 99][
                            if error? e: try [compress/level data method level] [
                                append refused reduce [method level e/id]
                            ]
                        ]
                    ]
                    refused""")).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("the level nobody asked for")
    class TheLevelNobodyAskedFor {

        @Test
        @DisplayName("minus one is how nobody asked is spelled")
        void minusOneIsTheDefault() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    collect [
                        foreach method methods [
                            keep (compress/level data method -1)
                                = compress data method
                        ]
                    ]""")).isEqualTo(ALL_SEVEN_TRUE);
        }

        /**
         * The five where the default is the hardest setting, so that minus one
         * and minus ninety-nine happen to agree and the difference between
         * "nobody asked" and "out of range" cannot be seen.
         */
        @Test
        @DisplayName("five of the seven default to their hardest setting")
        void fiveDefaultToTheirTop() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    collect [
                        repeat at 5 [
                            method: pick methods at
                            keep (compress data method)
                                = compress/level data method pick tops at
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        /**
         * And the two where it is not, which is where minus one can be told
         * apart from minus two.
         */
        @Test
        @DisplayName("LZMA defaults to five of nine and Brotli to six of eleven")
        void twoDefaultToAMiddleLevel() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    reduce [
                        (compress data 'lzma) = compress/level data 'lzma 5
                        (compress data 'lzma) = compress/level data 'lzma 9
                        (compress/level data 'lzma -1)
                            = compress/level data 'lzma -2
                    ]""")).isEqualTo("[#(true) #(false) #(false)]");
        }
    }

    /**
     * The invariant the spec states, run rather than argued: a level changes
     * which bytes come out and never changes what reading them back answers.
     */
    @Nested
    @DisplayName("compress and decompress are inverses at every level")
    class InversesAtEveryLevel {

        @Test
        @DisplayName("for all seven methods, at eighteen levels each")
        void everyMethodAtEveryLevel() {
            assertThat(answerTo(THE_METHODS_AND_THEIR_TOPS + """
                    bad: copy []
                    foreach method methods [
                        foreach level [-99 -2 -1 0 1 2 3 4 5 6 7 8 9 10 11 12 13 99][
                            either error? e: try [
                                packed: compress/level data method level
                            ][
                                append bad reduce [method level 'compress e/id]
                            ][
                                either error? e2: try [back: decompress packed method][
                                    append bad reduce [method level 'decompress e2/id]
                                ][
                                    unless data = to string! back [
                                        append bad reduce [method level 'differs]
                                    ]
                                ]
                            ]
                        ]
                    ]
                    bad""")).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("what the level argument may be")
    class WhatTheLevelMayBe {

        private static String refusalFor(String written) {
            return answerTo(
                    "raised: try [compress/level {x} 'zlib " + written + "]\n"
                            + "either error? raised [raised/id]['accepted]");
        }

        @Test
        @DisplayName("a whole number, and nothing else")
        void aWholeNumberAndNothingElse() {
            assertThat(refusalFor("1")).isEqualTo("accepted");
            assertThat(refusalFor("1.5")).isEqualTo("expect-arg");
            assertThat(refusalFor("{1}")).isEqualTo("expect-arg");
            assertThat(refusalFor("'one")).isEqualTo("expect-arg");
            assertThat(refusalFor("[1]")).isEqualTo("expect-arg");
            assertThat(refusalFor("#{01}")).isEqualTo("expect-arg");
            assertThat(refusalFor("1x1")).isEqualTo("expect-arg");
        }

        /**
         * A decimal is refused here where /PART truncates one, which is worth
         * knowing: the two refinements read a number two different ways.
         */
        @Test
        @DisplayName("even a decimal that is a whole number")
        void evenAWholeDecimal() {
            assertThat(refusalFor("1.0")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and the refinement without its argument is refused")
        void theRefinementNeedsItsArgument() {
            assertThat(answerTo("""
                    raised: try [compress "x" 'zlib]
                    either error? raised [raised/id]['accepted]"""))
                    .isEqualTo("accepted");
            assertThat(answerTo("""
                    raised: try [do [compress/level "x" 'zlib]]
                    either error? raised [raised/id]['accepted]"""))
                    .isEqualTo("no-arg");
        }
    }
}
