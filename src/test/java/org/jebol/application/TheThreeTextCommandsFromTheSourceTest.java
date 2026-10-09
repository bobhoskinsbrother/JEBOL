package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TheThreeTextCommandsFromTheSourceTest {

    private static final String A_PLAIN_GOB = """
            plain: make gob! [size: 200x100 text: "abcd"]
            """;

    private static final String A_RICH_TEXT_GOB = """
            written: copy "abcd^/ab"
            rich: make gob! [size: 200x100]
            rich/text: reduce [
                'para make object! [origin: 0x0 margin: 0x0 align: 'left valign: 'top]
                written
            ]
            """;

    private Interpreter anInterpreterOn(RecordingScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.WINDOWS).withWallClockLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private String answerTo(String source) {
        Interpreter interpreter = anInterpreterOn(RecordingScreen.measuring(1024, 768));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("size-text")
    class SizeText {

        @Test
        @DisplayName("answers the room a plain string takes, without the para's origin and margin")
        void aPlainString() {
            assertThat(answerTo(A_PLAIN_GOB + "size-text plain")).isEqualTo("24x12");
        }

        @Test
        @DisplayName("answers the widest line and every line's height, for rich text over two lines")
        void richTextOverTwoLines() {
            assertThat(answerTo(A_RICH_TEXT_GOB + "size-text rich")).isEqualTo("24x24");
        }

        @Test
        @DisplayName("answers 0x0 for a gob with no text")
        void noText() {
            assertThat(answerTo("size-text make gob! [size: 10x10]")).isEqualTo("0x0");
        }

        @Test
        @DisplayName("answers one line's height for an empty string")
        void anEmptyString() {
            assertThat(answerTo("size-text make gob! [size: 10x10 text: {}]")).isEqualTo("0x12");
        }

        @ParameterizedTest(name = "size-text {0} is refused")
        @ValueSource(strings = {"5", "{abc}", "none", "[]"})
        @DisplayName("anything but a gob is refused, as its declaration says")
        void anythingButAGobIsRefused(String given) {
            assertThat(answerTo("e: try [size-text " + given + "] e/id")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("caret-to-offset")
    class CaretToOffset {

        @ParameterizedTest(name = "string {0}, character {1} is drawn at {2}")
        @CsvSource({
                "3, 1, 0x0",
                "3, 3, 12x0",
                "3, 5, 24x0",
                "3, 6, 0x12",
                "3, 8, 12x12",
                "3, 99, 12x12",
        })
        @DisplayName("answers where the caret is drawn, named by integers, from the gob's own top left")
        void byIntegers(int element, int position, String drawnAt) {
            assertThat(answerTo(A_RICH_TEXT_GOB + "caret-to-offset rich %d %d".formatted(element, position)))
                    .isEqualTo(drawnAt);
        }

        @Test
        @DisplayName("takes the block at the string and the string at the caret, as the 2010 GUI asks")
        void byPositionedSeries() {
            assertThat(answerTo(A_RICH_TEXT_GOB + "caret-to-offset rich at rich/text 3 at written 3"))
                    .isEqualTo("12x0");
        }

        @Test
        @DisplayName("counts in the para's origin, where the text is written")
        void countsTheOrigin() {
            assertThat(answerTo(A_PLAIN_GOB + "caret-to-offset plain 1 2")).isEqualTo("8x2");
        }

        @ParameterizedTest(name = "caret-to-offset with {0} is refused")
        @ValueSource(strings = {"1.5 1", "1 1.5", "{a} 1", "1 none"})
        @DisplayName("a string's place or a character's place that is neither an integer nor a series is refused")
        void wrongPlacesAreRefused(String given) {
            assertThat(answerTo(A_RICH_TEXT_GOB + "e: try [caret-to-offset rich " + given + "] e/id"))
                    .isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("offset-to-caret")
    class OffsetToCaret {

        @ParameterizedTest(name = "{0} finds the caret before character {1}")
        @CsvSource({
                "0x5,   1",
                "8x5,   2",
                "9x5,   3",
                "500x5, 5",
                "7x13,  7",
                "7x500, 7",
        })
        @DisplayName("answers the rich-text block at the string, the string at the nearest caret")
        void theNearestCaret(String point, int character) {
            assertThat(answerTo(A_RICH_TEXT_GOB + """
                    found: offset-to-caret rich %s
                    reduce [index? found  index? first found  same? head first found written]""".formatted(point)))
                    .isEqualTo("[3 %d #(true)]".formatted(character));
        }

        @Test
        @DisplayName("answers a copy of the block, so moving it leaves the gob's text where it was")
        void aCopyOfTheBlock() {
            assertThat(answerTo(A_RICH_TEXT_GOB + """
                    found: offset-to-caret rich 9x5
                    reduce [same? head found rich/text  index? third rich/text]"""))
                    .isEqualTo("[#(false) 1]");
        }

        @Test
        @DisplayName("answers none for a gob whose text holds no string")
        void noStringNoCaret() {
            assertThat(answerTo("""
                    g: make gob! [size: 50x50]
                    g/text: [bold]
                    offset-to-caret g 5x5""")).isEqualTo("_");
        }

        @ParameterizedTest(name = "offset-to-caret with {0} is refused")
        @ValueSource(strings = {"5", "{5x5}", "none", "[5 5]"})
        @DisplayName("a point that is no pair is refused")
        void aPointThatIsNoPairIsRefused(String given) {
            assertThat(answerTo(A_RICH_TEXT_GOB + "e: try [offset-to-caret rich " + given + "] e/id"))
                    .isEqualTo("expect-arg");
        }
    }

    @Test
    @DisplayName("measuring needs a screen to measure with, so a machine with none refuses it as not present")
    void noScreenRefuses() {
        Interpreter interpreter = anInterpreterOn(RecordingScreen.absent());
        String asked = A_PLAIN_GOB + "e: try [size-text plain] e/id";
        interpreter.defineFreshWordsIn(asked);

        assertThat(interpreter.display(interpreter.run(asked))).isEqualTo("no-service");
    }
}
