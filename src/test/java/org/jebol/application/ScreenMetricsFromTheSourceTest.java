package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI-METRIC: what a script can ask about the screen it is drawing on.
 *
 * <p>Declared in {@code boot/window.reb} as a command of the {@code window}
 * host extension, and served by {@code src/os/posix/host-window.c} and
 * {@code src/os/win32/host-window.c}. Both hosts answer exactly twelve
 * keywords. Eleven measure and answer a pair; {@code screens} counts and
 * answers an integer, which is why the C writes it into the frame and returns
 * before reaching the code that makes a pair.
 *
 * <p>Two things here are load-bearing rather than decorative, and both are
 * pinned below. A title bar's pair has a width of zero, because VIEW writes
 * the whole pair into a window's offset and a width there would push every
 * window sideways. And a word no host serves is refused rather than answered
 * with none, because a metric is a number a caller is about to compute with:
 * a none reaching {@code screen/size - window/size / 2} fails somewhere else
 * entirely and blames the subtraction.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class ScreenMetricsFromTheSourceTest {

    private static final String ROOM = "1024x768";

    private static Interpreter withAScreen(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String answerFrom(RecordingScreen screen, String source) {
        Interpreter interpreter = withAScreen(screen);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerTo(String source) {
        return answerFrom(RecordingScreen.measuring(1024, 768), source);
    }

    @Nested
    @DisplayName("the eleven metrics that measure")
    class TheMeasurements {

        @Test
        @DisplayName("the whole display")
        void theScreenSize() {
            assertThat(answerTo("gui-metric 'screen-size")).isEqualTo(ROOM);
        }

        @Test
        @DisplayName("where this display starts in the desktop")
        void theScreenOrigin() {
            assertThat(answerTo("gui-metric 'screen-origin")).isEqualTo("0x0");
        }

        @Test
        @DisplayName("dots per inch, across and down")
        void theScreenDpi() {
            assertThat(answerTo("gui-metric 'screen-dpi")).isEqualTo("96x96");
        }

        @Test
        @DisplayName("where the usable area starts, inside the furniture")
        void theWorkOrigin() {
            assertThat(answerTo("gui-metric 'work-origin")).isEqualTo("0x25");
        }

        @Test
        @DisplayName("and how big the usable area is, which is less than the screen")
        void theWorkSize() {
            assertThat(answerTo("gui-metric 'work-size")).isEqualTo("1024x743");
        }

        @Test
        @DisplayName("the frame around a window that can be resized")
        void theBorderSize() {
            assertThat(answerTo("gui-metric 'border-size")).isEqualTo("4x4");
        }

        @Test
        @DisplayName("and the frame around one that cannot, which is thinner")
        void theFixedBorder() {
            assertThat(answerTo("gui-metric 'border-fixed")).isEqualTo("3x3");
        }

        @Test
        @DisplayName("the smallest a window is allowed to be")
        void theMinimumWindow() {
            assertThat(answerTo("gui-metric 'window-min-size")).isEqualTo("112x27");
        }

        @Test
        @DisplayName("the scaling factor from logical pixels to physical ones")
        void theLogicalSize() {
            assertThat(answerTo("gui-metric 'log-size")).isEqualTo("1x1");
        }

        @Test
        @DisplayName("and its reciprocal")
        void thePhysicalSize() {
            assertThat(answerTo("gui-metric 'phys-size")).isEqualTo("1x1");
        }

        @Test
        @DisplayName("every one of them is a pair, which is what a caller computes with")
        void theyAreAllPairs() {
            assertThat(answerTo("""
                    not-pairs: copy []
                    foreach m [screen-size screen-origin screen-dpi work-origin work-size
                               title-size border-size border-fixed window-min-size
                               log-size phys-size]
                        [unless pair? gui-metric m [append not-pairs m]]
                    mold not-pairs"""))
                    .isEqualTo("\"[]\"");
        }
    }

    @Nested
    @DisplayName("the one that counts")
    class TheCount {

        @Test
        @DisplayName("how many displays there are, as an integer and not a pair")
        void theNumberOfDisplays() {
            assertThat(answerTo("gui-metric 'screens")).isEqualTo("1");
            assertThat(answerTo("integer? gui-metric 'screens")).isEqualTo("#(true)");
            assertThat(answerTo("pair? gui-metric 'screens")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and it counts what the host has, not always one")
        void twoDisplaysCountAsTwo() {
            assertThat(answerFrom(
                    RecordingScreen.measuring(1024, 768).withDisplays(2),
                    "gui-metric 'screens")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("the title bar has a height and no width")
    class TheTitleBar {

        @Test
        @DisplayName("so the first number of the pair is zero")
        void itsWidthIsZero() {
            // A pair's halves come out as decimals, which the binary agrees
            // with: `first 0x22` is 0.0 and its type is decimal!.
            assertThat(answerTo("gui-metric 'title-size")).isEqualTo("0x22");
            assertThat(answerTo("first gui-metric 'title-size")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("which matters because VIEW uses the whole pair as an offset")
        void theZeroIsLoadBearing() {
            assertThat(answerTo("""
                    zero? first gui-metric 'title-size""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @ParameterizedTest
        @ValueSource(strings = {"virtual-screen-size", "nosuch", "screen-height", "size"})
        @DisplayName("a word no host serves is refused, never answered with none")
        void anUnservedWordIsRefused(String word) {
            assertThat(answerTo("error? try [gui-metric '" + word + "]"))
                    .as("%s is not one of the twelve", word)
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("virtual-screen-size especially, because the word list promises it")
        void theWordListPromisesOneNobodyServes() {
            assertThat(answerTo("error? try [gui-metric 'virtual-screen-size]"))
                    .as("boot/window.reb lists it and neither host has a branch for it")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and refusing rather than answering none is the whole point")
        void theRefusalIsNotNone() {
            assertThat(answerTo("none? attempt [gui-metric 'nosuch]"))
                    .as("an attempt swallows the raise, so this being none proves it raised")
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what it is given that is not a word at all")
    class TheWrongTypes {

        @ParameterizedTest
        @ValueSource(strings = {"5", "\"screen-size\"", "#(none)", "1x1", "[screen-size]"})
        @DisplayName("a value that is not a word is refused rather than coerced")
        void aNonWordIsRefused(String written) {
            assertThat(answerTo("error? try [gui-metric " + written + "]"))
                    .as("%s is not a word", written)
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a string spelling the right word is still not that word")
        void aStringIsNotAWord() {
            assertThat(answerTo("""
                    error? try [gui-metric {screen-size}]"""))
                    .as("silent coercion here would make a typo answer a number")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a display index that is not an integer is refused")
        void aNonIntegerDisplayIsRefused() {
            assertThat(answerTo("""
                    error? try [gui-metric/display 'screen-size {0}]"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("which display is being asked about")
    class TheDisplayIndex {

        @Test
        @DisplayName("without saying, it is the first one")
        void theDefaultIsTheFirst() {
            assertThat(answerTo("gui-metric 'screen-size"))
                    .as("the C starts `REBINT display = 0` and only overwrites it "
                            + "when /display was written")
                    .isEqualTo(ROOM);
        }

        @Test
        @DisplayName("display zero is the first one, counting from zero")
        void zeroIsTheFirst() {
            assertThat(answerTo("gui-metric/display 'screen-size 0")).isEqualTo(ROOM);
        }

        @Test
        @DisplayName("and saying zero is the same as not saying")
        void sayingZeroChangesNothing() {
            assertThat(answerTo("""
                    (gui-metric 'screen-size) = (gui-metric/display 'screen-size 0)"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("one past the last display is refused, not answered with the first's")
        void oneTooManyIsRefused() {
            assertThat(answerTo("error? try [gui-metric/display 'screen-size 1]"))
                    .as("one display means index 0 and nothing else; answering the "
                            + "laptop's numbers for display 1 is silently wrong")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the last of two is served when there are two")
        void theSecondOfTwoIsServed() {
            assertThat(answerFrom(
                    RecordingScreen.measuring(1024, 768).withDisplays(2),
                    "pair? gui-metric/display 'screen-size 1")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a negative index is refused like any other that is not there")
        void aNegativeIndexIsRefused() {
            assertThat(answerTo("error? try [gui-metric/display 'screen-size -1]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but a machine with no displays answers zero whichever is named")
        void noDisplaysStillAnswersZero() {
            // Not a refusal, because the split is about the screen and not
            // about the index: a screen that is not there answers zeros for
            // everything, and the out-of-range check only has a range to
            // check against when there is a display.
            assertThat(answerFrom(RecordingScreen.absent(),
                    "gui-metric/display 'screen-size 0")).isEqualTo("0x0");
            assertThat(answerFrom(RecordingScreen.absent(),
                    "gui-metric/display 'screen-size 7")).isEqualTo("0x0");
        }
    }

    @Nested
    @DisplayName("a screen that is not there answers about itself")
    class TheAbsentScreen {

        @Test
        @DisplayName("every metric is zero, which is what a GTK build answers for ten of them")
        void everyMetricIsZero() {
            assertThat(answerFrom(RecordingScreen.absent(), """
                    not-zero: copy []
                    foreach m [screen-size screen-origin screen-dpi work-origin work-size
                               title-size border-size border-fixed window-min-size
                               log-size phys-size]
                        [unless 0x0 = gui-metric m [append not-zero m]]
                    mold not-zero"""))
                    .isEqualTo("\"[]\"");
        }

        @Test
        @DisplayName("and there are no displays to count")
        void thereAreNoDisplays() {
            assertThat(answerFrom(RecordingScreen.absent(), "gui-metric 'screens"))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("but a word no host serves is still refused, screen or no screen")
        void anUnservedWordIsStillRefused() {
            assertThat(answerFrom(RecordingScreen.absent(),
                    "error? try [gui-metric 'nosuch]")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("without the grant")
    class TheGrant {

        @Test
        @DisplayName("an interpreter that was not granted the screen refuses")
        void anUngrantedScriptIsRefused() {
            Interpreter walled = Interpreter.create();
            assertThat(walled.display(walled.run("error? try [gui-metric 'screen-size]")))
                    .isEqualTo("#(true)");
        }
    }
}
