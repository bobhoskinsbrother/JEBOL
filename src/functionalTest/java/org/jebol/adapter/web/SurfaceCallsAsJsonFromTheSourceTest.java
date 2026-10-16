package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.TextMeasures;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceCallsAsJsonFromTheSourceTest {

    private static final String ONE_CONTROL_CHARACTER = String.valueOf((char) 1);

    private static GobValue gobFrom(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.WINDOWS));
        interpreter.defineFreshWordsIn(source);
        return (GobValue) interpreter.run(source).value();
    }

    private static String written(String source) {
        return SurfaceCallsAsJson.written(
                PaintList.of(gobFrom(source)).calls(TextMeasures.ofTheCarriedFont()), 800, 600);
    }

    @Nested
    @DisplayName("what a fill becomes")
    class TheFills {

        @Test
        @DisplayName("its kind, its place and its colour")
        void aFillCarriesItsPlaceAndColour() {
            String json = written("make gob! [size: 40x30 color: 200.100.50]");

            assertThat(json).contains("""
                    "kind":"fill",""");
            assertThat(json).contains("""
                    "wide":40,""");
            assertThat(json).contains("""
                    "high":30,""");
            assertThat(json).contains("""
                    "colour":"#c86432",""");
        }

        @Test
        @DisplayName("and the clip it may paint in, which the page must not work out")
        void theClipCrosses() {
            String json = written("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    append parent make gob! [offset: 10x10 size: 100x100 color: 2.2.2]
                    parent""");

            assertThat(json)
                    .as("the child is clipped to ten by ten, and the page is "
                            + "told so rather than deducing it")
                    .contains("""
                            "clip":{"across":10,"down":10,"wide":10,"high":10}""");
        }

        @Test
        @DisplayName("and its opacity, already multiplied down the tree")
        void theOpacityCrosses() {
            assertThat(written("""
                    parent: make gob! [size: 40x40 color: 1.1.1 alpha: 128]
                    append parent make gob! [size: 10x10 color: 2.2.2 alpha: 128]
                    parent"""))
                    .contains("""
                            "opacity":64""");
        }
    }

    @Nested
    @DisplayName("what writing becomes")
    class TheWriting {

        @Test
        @DisplayName("its words, placed, so the page lays nothing out")
        void theWordsCrossPlaced() {
            String json = written("""
                    make gob! [size: 100x20 text: "hello"]""");

            assertThat(json)
                    .contains("""
                            "kind":"text",""")
                    .contains("""
                            "text":"hello",""")
                    .contains("""
                            "size":12,""")
                    .contains("""
                            "baseline":""");
        }

        @ParameterizedTest(name = "wrap? {0}")
        @CsvSource({"true", "false"})
        @DisplayName("a long line wraps into more lines when the para wraps, and the pieces are placed per line")
        void wrappingIsTheDomains(String wraps) {
            String json = written("""
                    labelled: make gob! [size: 100x20]
                    labelled/text: compose [para (make object! [wrap?: %s]) "one two three four five six"]
                    labelled""".formatted(wraps));

            int baselines = json.split("\"baseline\"", -1).length - 1;
            if (wraps.equals("true")) {
                assertThat(baselines)
                        .as("a wrapping para breaks the line and the pieces are placed per line")
                        .isGreaterThan(1);
            } else {
                assertThat(baselines)
                        .as("a plain para keeps one line")
                        .isEqualTo(1);
            }
        }

        @Test
        @DisplayName("a colour it can see through crosses as rgba")
        void aSeeThroughColourCrossesAsRgba() {
            assertThat(written("""
                    labelled: make gob! [size: 100x20]
                    labelled/text: [0.0.0.128 {faint}]
                    labelled"""))
                    .contains("rgba(0,0,0,0.502)");
        }

        @Test
        @DisplayName("a quotation mark in a caption does not end the string")
        void aQuoteIsEscaped() {
            assertThat(written("""
                    make gob! [size: 100x20 text: {say "hi"}]"""))
                    .as("a caption is data, and the first person to put a quote "
                            + "in one must not break the page")
                    .contains("\"text\":\"say \\\"hi\\\"\"");
        }

        @Test
        @DisplayName("and neither does a backslash, a newline or a tab")
        void theOtherEscapesHold() {
            assertThat(SurfaceCallsAsJson.asAString("a\\b")).isEqualTo("\"a\\\\b\"");
            assertThat(SurfaceCallsAsJson.asAString("a\nb")).isEqualTo("\"a\\nb\"");
            assertThat(SurfaceCallsAsJson.asAString("a\tb")).isEqualTo("\"a\\tb\"");
            assertThat(SurfaceCallsAsJson.asAString("a\rb")).isEqualTo("\"a\\rb\"");
        }

        @Test
        @DisplayName("a character below a space becomes an escape rather than a raw byte")
        void controlCharactersAreEscaped() {
            assertThat(SurfaceCallsAsJson.asAString("a" + ONE_CONTROL_CHARACTER + "b"))
                    .isEqualTo("\"a\\u0001b\"");
        }

        @Test
        @DisplayName("and a caption cannot close the object it is written into")
        void aCaptionCannotBreakOut() {
            String breakingOut = "\",\"kind\":\"fill\",\"x\":\"";

            assertThat(SurfaceCallsAsJson.asAString(breakingOut))
                    .as("what a script wrote is one string and stays one")
                    .isEqualTo("\"\\\",\\\"kind\\\":\\\"fill\\\",\\\"x\\\":\\\"\"");
        }

        @Test
        @DisplayName("a character above the basic plane survives whole")
        void anAstralCharacterSurvives() {
            assertThat(SurfaceCallsAsJson.asAString("a🌈b"))
                    .isEqualTo("\"a🌈b\"");
        }
    }

    @Nested
    @DisplayName("the message as a whole")
    class TheMessage {

        @Test
        @DisplayName("says how big the surface is, so the page sizes its canvas")
        void theSurfaceSizeIsSaid() {
            assertThat(written("make gob! [size: 10x10 color: 1.1.1]"))
                    .contains("""
                            "wide":800,""")
                    .contains("""
                            "high":600,""");
        }

        @Test
        @DisplayName("holds the calls in the order they must be painted")
        void theOrderIsKept() {
            String json = written("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent""");

            assertThat(json.indexOf("#010101"))
                    .as("the parent is painted first, so it is written first")
                    .isLessThan(json.indexOf("#020202"));
        }

        @Test
        @DisplayName("and an empty list is a message with no calls, not no message")
        void anEmptyListIsStillAMessage() {
            assertThat(written("make gob! [size: 0x0]"))
                    .contains("""
                            "paint":[]""");
        }
    }
}
