package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDiffPartClipsAtTheEdgeFromTheSourceTest {

    private static final String WHITE_OVER_BLACK_AND_WHITE = """
            i1: make image! [2x2 0.0.0]
            i2: make image! [2x2 255.255.255]
            i1/1: 255.255.255
            i1/2: 255.255.255
            """;

    private static String compare(String corner, String size) {
        String source = WHITE_OVER_BLACK_AND_WHITE + """
                r: try [image-diff/part i1 i2 %s %s]
                either error? r [reduce [r/id r/arg1]] [r]""".formatted(corner, size);
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a rectangle past the right or bottom edge is clipped to it")
    class PastTheEdge {

        @ParameterizedTest(name = "{0} {1} is {2}")
        @CsvSource({
                "0x0, 1x3, 49.9999999999999%",
                "0x0, 3x1, 0%",
                "0x0, 3x3, 49.9999999999999%",
                "1x0, 1x3, 49.9999999999999%",
                "0x1, 2x2, 100%",
                "1x1, 5x5, 100%",
                "1x0, 5x1, 0%",
                "0x0, 9x9, 49.9999999999999%",
                "-1x-1, 9x9, 49.9999999999999%"})
        @DisplayName("compares only the pixels up to the far edges")
        void clips(String corner, String size, String answer) {
            assertThat(compare(corner, size)).isEqualTo(answer);
        }
    }

    @Nested
    @DisplayName("inside the image nothing changes")
    class InsideTheImage {

        @ParameterizedTest(name = "{0} {1} is {2}")
        @CsvSource({
                "0x0, 1x1, 0%",
                "0x0, 1x2, 49.9999999999999%",
                "0x0, 2x2, 49.9999999999999%",
                "1x0, 1x2, 49.9999999999999%",
                "0x1, 2x1, 100%",
                "2x2, -1x-2, 49.9999999999999%",
                "2x2, -3x-3, 49.9999999999999%",
                "-1x0, 2x2, 49.9999999999999%",
                "1x1, -5x-5, 0%"})
        @DisplayName("compares the rectangle asked for")
        void compares(String corner, String size, String answer) {
            assertThat(compare(corner, size)).isEqualTo(answer);
        }
    }

    @Nested
    @DisplayName("the refusals are a real 3.22.5's")
    class Refusals {

        @ParameterizedTest(name = "{0} {1} names {2}")
        @CsvSource({
                "0x0, 0x2, 0x2",
                "3x0, 1x2, 3x0",
                "0x2, 1x2, 0x2",
                "2x0, 0x1, 0x1",
                "3x0, 0x2, 3x0",
                "2x0, 1x1, 2x0",
                "0x2, 1x1, 0x2"})
        @DisplayName("an empty rectangle names its size and a corner outside names the corner")
        void refuses(String corner, String size, String named) {
            assertThat(compare(corner, size)).isEqualTo("[invalid-data " + named + "]");
        }
    }
}
