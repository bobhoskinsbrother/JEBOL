package org.jebol.render;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The VID dialect rendered to markup.
 *
 * <p>Written before the renderer exists. The target is HTML, not a window:
 * a layout is a block describing what should be on the page, and rendering
 * one to markup is more natural than rendering it to a desktop toolkit,
 * because the dialect is already a description rather than a sequence of
 * draw calls.
 *
 * <p>VID-shaped rather than faithful, which is the open fork in
 * {@code docs/milestones.md} taken on the recommendation there. Existing
 * layouts mostly work and pixel-exact ones do not; chasing a 2001 desktop
 * toolkit's box model would produce markup nobody wants to style.
 */
class MarkupRenderingTest {

    private static String render(String source) {
        Interpreter interpreter = Interpreter.create();
        return Markup.render(interpreter, source);
    }

    @Nested
    @DisplayName("the shapes a layout is made of")
    class Shapes {

        @Test
        @DisplayName("text becomes a paragraph")
        void textBecomesAParagraph() {
            assertThat(render("[text \"hello\"]"))
                    .contains("<p").contains("hello").contains("</p>");
        }

        @Test
        @DisplayName("a button becomes a button")
        void buttonBecomesAButton() {
            assertThat(render("[button \"Press me\"]"))
                    .contains("<button").contains("Press me").contains("</button>");
        }

        @Test
        @DisplayName("a field becomes an input")
        void fieldBecomesAnInput() {
            assertThat(render("[field \"typed\"]"))
                    .contains("<input").contains("value=\"typed\"");
        }

        @Test
        @DisplayName("a box becomes a div")
        void boxBecomesADiv() {
            assertThat(render("[box \"contents\"]"))
                    .contains("<div").contains("contents");
        }

        @Test
        @DisplayName("several shapes render in order")
        void shapesRenderInOrder() {
            String html = render("[text \"first\" button \"second\"]");

            assertThat(html.indexOf("first"))
                    .as("source order is page order")
                    .isLessThan(html.indexOf("second"));
        }

        @Test
        @DisplayName("an empty layout is still a page")
        void anEmptyLayoutIsStillAPage() {
            assertThat(render("[]")).contains("<div").contains("</div>");
        }
    }

    @Nested
    @DisplayName("the values a layout is decorated with")
    class Decoration {

        @Test
        @DisplayName("a pair becomes a width and a height")
        void pairsBecomeSizes() {
            assertThat(render("[box 140x32]"))
                    .contains("width:140px").contains("height:32px");
        }

        @Test
        @DisplayName("a tuple becomes a colour")
        void tuplesBecomeColours() {
            assertThat(render("[box 100.150.150]"))
                    .contains("rgb(100,150,150)");
        }

        @Test
        @DisplayName("a colour word becomes its colour")
        void colourWordsBecomeColours() {
            assertThat(render("[box red]")).contains("rgb(255,0,0)");
        }

        @Test
        @DisplayName("decoration applies to the shape it follows")
        void decorationBelongsToItsShape() {
            String html = render("[text \"plain\" button \"sized\" 200x40]");

            assertThat(html).contains("width:200px");
            assertThat(html.indexOf("width:200px"))
                    .as("the size belongs to the button, not the text")
                    .isGreaterThan(html.indexOf("plain"));
        }
    }

    @Nested
    @DisplayName("what the markup must not do")
    class Safety {

        @Test
        @DisplayName("text from a script is escaped, not injected")
        void scriptTextIsEscaped() {
            String html = render("[text \"<script>alert(1)</script>\"]");

            assertThat(html)
                    .as("a layout is data, and data must not become markup")
                    .doesNotContain("<script>alert");
            assertThat(html).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("quotes in an attribute are escaped")
        void attributeQuotesAreEscaped() {
            String html = render("[field {say \"hello\"}]");

            assertThat(html).contains("&quot;");
        }
    }

    @Nested
    @DisplayName("rendering is a pure function of the values")
    class Purity {

        @Test
        @DisplayName("the same layout renders the same markup")
        void renderingIsDeterministic() {
            String source = "[text \"a\" button \"b\" box 10x10 blue]";

            assertThat(render(source)).isEqualTo(render(source));
        }

        @Test
        @DisplayName("a layout that is not a layout is refused rather than guessed at")
        void nonLayoutsAreRefused() {
            assertThat(render("42"))
                    .as("nothing to render is an empty page, not an invented one")
                    .doesNotContain("42");
        }
    }

    @Nested
    @DisplayName("the demo programmes, which is why this exists")
    class DemoProgrammes {

        @Test
        @DisplayName("the smallest demo's layout renders")
        void clockLayoutRenders() {
            String html = render(
                    "[origin 0 banner 140x32 rate 1]");

            assertThat(html).contains("width:140px");
        }

        @Test
        @DisplayName("a layout with a nested block of styling renders")
        void nestedBlocksRender() {
            String html = render(
                    "[box 100x50 effect [gradient 0x1 0.0.150 0.0.50]]");

            assertThat(html).contains("width:100px");
        }
    }
}
