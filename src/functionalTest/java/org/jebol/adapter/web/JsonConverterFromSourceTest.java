package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.TheCarriedFont;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConverterFromSourceTest {

    private static final String ONE_CONTROL_CHARACTER = String.valueOf((char) 1);

    private static GobValue makeAGobFrom(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.WINDOWS));
        interpreter.defineNewWordsIn(source);
        return (GobValue) interpreter.run(source).value();
    }

    private static String write(String source) {
        return JsonConverter.write(
                PaintList.of(makeAGobFrom(source)).calls(TheCarriedFont.measurement()), 800, 600);
    }

    @Nested
    @DisplayName("what a fill becomes")
    class TheFills {

        @Test
        @DisplayName("its type, its place and its colour")
        void carryTheirPlaceAndColour() {
            String json = write("make gob! [size: 40x30 color: 200.100.50]");

            assertThat(json).contains("""
                    "type":"fill",""");
            assertThat(json).contains("""
                    "wide":40,""");
            assertThat(json).contains("""
                    "high":30,""");
            assertThat(json).contains("""
                    "colour":"#c86432",""");
        }

        @Test
        @DisplayName("and the clip it may paint in, which the page must not work out")
        void carryTheClip() {
            String json = write("""
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
        void carryTheOpacity() {
            assertThat(write("""
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
        void carriesTheWordsPlaced() {
            String json = write("""
                    make gob! [size: 100x20 text: "hello"]""");

            assertThat(json)
                    .contains("""
                            "type":"text",""")
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
        void leavesWrappingToTheDomain(String wraps) {
            String json = write("""
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
        void carriesASeeThroughColourAsRgba() {
            assertThat(write("""
                    labelled: make gob! [size: 100x20]
                    labelled/text: [0.0.0.128 {faint}]
                    labelled"""))
                    .contains("rgba(0,0,0,0.502)");
        }

        @Test
        @DisplayName("a quotation mark in a caption does not end the string")
        void escapesAQuote() {
            assertThat(write("""
                    make gob! [size: 100x20 text: {say "hi"}]"""))
                    .as("a caption is data, and the first person to put a quote "
                            + "in one must not break the page")
                    .contains("\"text\":\"say \\\"hi\\\"\"");
        }

        @Test
        @DisplayName("and neither does a backslash, a newline or a tab")
        void keepsTheOtherEscapes() {
            assertThat(JsonConverter.asString("a\\b")).isEqualTo("\"a\\\\b\"");
            assertThat(JsonConverter.asString("a\nb")).isEqualTo("\"a\\nb\"");
            assertThat(JsonConverter.asString("a\tb")).isEqualTo("\"a\\tb\"");
            assertThat(JsonConverter.asString("a\rb")).isEqualTo("\"a\\rb\"");
        }

        @Test
        @DisplayName("a character below a space becomes an escape rather than a raw byte")
        void controlCharactersAreEscaped() {
            assertThat(JsonConverter.asString("a" + ONE_CONTROL_CHARACTER + "b"))
                    .isEqualTo("\"a\\u0001b\"");
        }

        @Test
        @DisplayName("and a caption cannot close the object it is written into")
        void keepsACaptionFromBreakingOut() {
            String breakingOut = "\",\"type\":\"fill\",\"x\":\"";

            assertThat(JsonConverter.asString(breakingOut))
                    .as("what a script wrote is one string and stays one")
                    .isEqualTo("\"\\\",\\\"type\\\":\\\"fill\\\",\\\"x\\\":\\\"\"");
        }

        @Test
        @DisplayName("a character above the basic plane survives whole")
        void keepsAnAstralCharacter() {
            assertThat(JsonConverter.asString("a🌈b"))
                    .isEqualTo("\"a🌈b\"");
        }
    }

    @Nested
    @DisplayName("the message as a whole")
    class TheMessage {

        @Test
        @DisplayName("says how big the surface is, so the page sizes its canvas")
        void saysTheSurfaceSize() {
            assertThat(write("make gob! [size: 10x10 color: 1.1.1]"))
                    .contains("""
                            "wide":800,""")
                    .contains("""
                            "high":600,""");
        }

        @Test
        @DisplayName("holds the calls in the order they must be painted")
        void keepsTheOrder() {
            String json = write("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent""");

            assertThat(json.indexOf("#010101"))
                    .as("the parent is painted first, so it is written first")
                    .isLessThan(json.indexOf("#020202"));
        }

        @Test
        @DisplayName("and an empty list is a message with no calls, not no message")
        void sendsAnEmptyList() {
            assertThat(write("make gob! [size: 0x0]"))
                    .contains("""
                            "paint":[]""");
        }
    }
}
