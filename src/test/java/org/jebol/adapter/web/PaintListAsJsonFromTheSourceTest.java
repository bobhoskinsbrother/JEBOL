package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A paint list on the wire.
 *
 * <p>The one place where "the browser gets the same list" becomes bytes, so it
 * is the one place the claim can be quietly broken. Every number a renderer
 * would otherwise have had to work out has to survive the crossing: the
 * position, the clip and the opacity.
 *
 * <p>Written by hand rather than by a library, because the project has no
 * runtime dependencies and this is a few dozen lines of numbers and strings.
 * Everything a script supplied is escaped: a caption is data, and data
 * arriving from a script must not become code, or the first person to put a
 * quotation mark in one has broken the page.
 *
 * <p>Specified in {@code spec/screen.allium} under the paint list.
 */
class PaintListAsJsonFromTheSourceTest {

    /** A character below a space, built rather than typed into the source. */
    private static final String ONE_CONTROL_CHARACTER = String.valueOf((char) 1);

    private static GobValue gobFrom(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.defineFreshWordsIn(source);
        return (GobValue) interpreter.run(source).value();
    }

    private static String written(String source) {
        return PaintListAsJson.written(PaintList.of(gobFrom(source)), 800, 600);
    }

    @Nested
    @DisplayName("what a fill becomes")
    class TheFills {

        @Test
        @DisplayName("its kind, its place and its colour")
        void afillCarriesItsPlaceAndColour() {
            String json = written("make gob! [size: 40x30 color: 200.100.50]");

            assertThat(json).contains("""
                    "kind":"fill",""");
            assertThat(json).contains("""
                    "wide":40,""");
            assertThat(json).contains("""
                    "high":30,""");
            assertThat(json).contains("""
                    "colour":"#c86432"}""");
        }

        @Test
        @DisplayName("and the clip it may paint in, which the browser must not work out")
        void theClipCrosses() {
            String json = written("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    append parent make gob! [offset: 10x10 size: 100x100 color: 2.2.2]
                    parent""");

            assertThat(json)
                    .as("the child is clipped to ten by ten, and the browser is "
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
        @DisplayName("its words, carried as text")
        void theWordsCross() {
            assertThat(written("""
                    make gob! [size: 100x20 text: "hello"]"""))
                    .contains("""
                            "kind":"writing",""")
                    .contains("""
                            "text":"hello",""");
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
            assertThat(PaintListAsJson.asAString("a\\b")).isEqualTo("\"a\\\\b\"");
            assertThat(PaintListAsJson.asAString("a\nb")).isEqualTo("\"a\\nb\"");
            assertThat(PaintListAsJson.asAString("a\tb")).isEqualTo("\"a\\tb\"");
            assertThat(PaintListAsJson.asAString("a\rb")).isEqualTo("\"a\\rb\"");
        }

        @Test
        @DisplayName("a character below a space becomes an escape rather than a raw byte")
        void controlCharactersAreEscaped() {
            assertThat(PaintListAsJson.asAString("a" + ONE_CONTROL_CHARACTER + "b"))
                    .isEqualTo("\"a\\u0001b\"");
        }

        @Test
        @DisplayName("and a caption cannot close the object it is written into")
        void aCaptionCannotBreakOut() {
            // The whole reason the escaper exists, stated as the attack it
            // stops: a caption that closed its own string could add an
            // instruction of its own to the list the browser executes.
            String breakingOut = "\",\"kind\":\"fill\",\"x\":\"";

            assertThat(PaintListAsJson.asAString(breakingOut))
                    .as("what a script wrote is one string and stays one")
                    .isEqualTo("\"\\\",\\\"kind\\\":\\\"fill\\\",\\\"x\\\":\\\"\"");
        }

        @Test
        @DisplayName("a character above the basic plane survives whole")
        void anAstralCharacterSurvives() {
            // Written as code points rather than as Java's char pairs, because
            // escaping half a surrogate pair produces a string no reader will
            // accept and the fault would look like a browser problem.
            assertThat(PaintListAsJson.asAString("a🌈b"))
                    .isEqualTo("\"a🌈b\"");
        }
    }

    @Nested
    @DisplayName("the message as a whole")
    class TheMessage {

        @Test
        @DisplayName("says how big the surface is, so the browser sizes its canvas")
        void theSurfaceSizeIsSaid() {
            assertThat(written("make gob! [size: 10x10 color: 1.1.1]"))
                    .contains("""
                            "wide":800,""")
                    .contains("""
                            "high":600,""");
        }

        @Test
        @DisplayName("holds the instructions in the order they must be painted")
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
        @DisplayName("and an empty list is a message with no instructions, not no message")
        void anEmptyListIsStillAMessage() {
            assertThat(written("make gob! [size: 0x0]"))
                    .contains("""
                            "paint":[]""");
        }
    }
}
