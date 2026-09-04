package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FILTER and UNFILTER, the PNG delta filters.
 *
 * <p>Read out of {@code u-png-filter.c}. Byte arithmetic and nothing else:
 * each output byte is the input byte less a prediction made from its
 * neighbours, and the whole point is that the differences compress better than
 * the values.
 *
 * <p>The predictions are not guessable and each is tested against a worked
 * example rather than against the port. AVERAGE floors its mean with a shift
 * rather than rounding, and PAETH's tie-breaks are ordered -- left, then
 * above, then above-left -- so a reordering of the comparisons would pass a
 * careless test and fail a real image.
 *
 * <p>Specified in {@code spec/natives.allium} under "The PNG delta filters".
 */
class PngFilterFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("each filter subtracts the prediction the C makes")
    class ThePredictions {

        @Test
        @DisplayName("SUB takes the byte one pixel to the left")
        void subTakesTheLeft() {
            assertThat(answerTo("filter #{0A141E28} 4 'sub")).isEqualTo("#{0A0A0A0A}");
        }

        @Test
        @DisplayName("and leaves the first pixel of a line alone")
        void subLeavesTheFirstPixel() {
            assertThat(answerTo("first filter #{0A141E28} 4 'sub")).isEqualTo("10");
        }

        @Test
        @DisplayName("UP takes the byte on the line above, and zero above the first")
        void upTakesTheLineAbove() {
            assertThat(answerTo("filter #{0A14 0B16} 2 'up")).isEqualTo("#{0A140102}");
        }

        @Test
        @DisplayName("AVERAGE floors the mean with a shift rather than rounding")
        void averageFloorsTheMean() {
            assertThat(answerTo("filter #{0003 0005} 2 'average"))
                    .isEqualTo("#{00030004}");
            assertThat(answerTo(
                    "unfilter/as #{0003 0004} 2 'average")).isEqualTo("#{00030005}");
        }

        @Test
        @DisplayName("PAETH breaks a tie towards the left, then above, then above-left")
        void paethBreaksTiesInOrder() {
            assertThat(answerTo(
                    "b: #{0A0A 0A0A} f: filter b 2 'paeth "
                    + "b = unfilter/as f 2 'paeth")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a filter of NONE leaves the bytes as they are")
        void noneChangesNothing() {
            assertThat(answerTo("filter #{0A141E28} 4 'none")).isEqualTo("#{0A141E28}");
        }

        @Test
        @DisplayName("a number names a filter as well as a word")
        void aNumberNamesOneToo() {
            assertThat(answerTo(
                    "(filter #{0A141E28} 4 'sub) = filter #{0A141E28} 4 1"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("every filter is reversible, which is what a codec needs")
    class Reversible {

        @Test
        @DisplayName("/AS names the type on the way back, for all five")
        void allFiveReverse() {
            for (String kind : new String[] {"'none", "'sub", "'up", "'average", "'paeth"}) {
                assertThat(answerTo(
                        "b: #{0A141E28 0B151F29 00FF00FF} "
                        + "b = unfilter/as (filter b 4 " + kind + ") 4 " + kind))
                        .as("filter then unfilter with " + kind)
                        .isEqualTo(TRUE);
            }
        }

        @Test
        @DisplayName("and with /SKIP naming the bytes per pixel")
        void reversesWithABytesPerPixel() {
            for (String kind : new String[] {"'sub", "'average", "'paeth"}) {
                assertThat(answerTo(
                        "b: #{0A141E28 0B151F29} "
                        + "b = unfilter/as/skip (filter/skip b 4 " + kind
                        + " 2) 4 " + kind + " 2"))
                        .as("bytes per pixel of two, with " + kind)
                        .isEqualTo(TRUE);
            }
        }
    }

    @Nested
    @DisplayName("without /AS each line carries its own filter type")
    class TheLeadingTypeByte {

        @Test
        @DisplayName("the leading byte of each line names that line's filter")
        void theLeadingByteNamesIt() {
            assertThat(answerTo("unfilter #{00 0A14 00 0B16} 2"))
                    .isEqualTo("#{0A140B16}");
        }

        @Test
        @DisplayName("a leading 2 means UP, and undoes an UP-filtered line")
        void aLeadingTwoMeansUp() {
            assertThat(answerTo("unfilter #{00 0A14 02 0102} 2"))
                    .isEqualTo("#{0A140B16}");
        }

        @Test
        @DisplayName("the width excludes the type byte, so the two forms differ")
        void theWidthExcludesTheTypeByte() {
            assertThat(answerTo("4 = length? unfilter #{00 0A14 00 0B16} 2"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("6 = length? unfilter/as #{00 0A14 00 0B16} 2 'none"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("an impossible geometry is refused")
    class Geometry {

        @Test
        @DisplayName("a width of one or less")
        void tooNarrow() {
            assertThat(errorIdFrom("filter #{0A141E28} 1 'sub")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("filter #{0A141E28} 0 'sub")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a width wider than the data")
        void tooWide() {
            assertThat(errorIdFrom("filter #{0A14} 9 'sub")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a bytes-per-pixel of less than one, or wider than a line")
        void anImpossiblePixel() {
            assertThat(errorIdFrom("filter/skip #{0A141E28} 4 'sub 0"))
                    .isEqualTo("invalid-arg");
            assertThat(errorIdFrom("filter/skip #{0A141E28} 4 'sub 9"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a filter nobody has heard of")
        void anUnknownFilter() {
            assertThat(errorIdFrom("filter #{0A141E28} 4 'invented"))
                    .isNotEqualTo("no-error");
            assertThat(errorIdFrom("filter #{0A141E28} 4 9"))
                    .isNotEqualTo("no-error");
        }
    }
}
