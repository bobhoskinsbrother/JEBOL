package org.jebol.render;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveRenderingTest {

    private static View viewOf(String source) {
        return View.of(Interpreter.create(), source);
    }

    @Nested
    @DisplayName("faces that can be acted on")
    class Actionable {

        @Test
        @DisplayName("a face with an action block gets a handle the browser can name")
        void actionableFacesAreNamed() {
            View view = viewOf("[button \"Press\" [count: 1]]");

            assertThat(view.writeTheMarkup()).contains("data-jebol-action=");
        }

        @Test
        @DisplayName("a face without one does not")
        void inertFacesAreNotNamed() {
            View view = viewOf("[text \"just words\"]");

            assertThat(view.writeTheMarkup()).doesNotContain("data-jebol-action=");
        }

        @Test
        @DisplayName("each actionable face gets its own handle")
        void handlesAreDistinct() {
            View view = viewOf(
                    "[button \"one\" [x: 1] button \"two\" [x: 2]]");

            assertThat(view.actionHandles()).hasSize(2).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("an event runs the block that belongs to it")
    class Events {

        @Test
        void runTheirAction() {
            View view = viewOf(
                    "count: 0 [button \"Press\" [count: add count 1]]");

            view.raise(view.actionHandles().get(0));

            assertThat(view.evaluate("count").display()).isEqualTo("1");
        }

        @Test
        @DisplayName("pressing twice runs it twice")
        void accumulate() {
            View view = viewOf(
                    "count: 0 [button \"Press\" [count: add count 1]]");
            String handle = view.actionHandles().get(0);

            view.raise(handle);
            view.raise(handle);

            assertThat(view.evaluate("count").display()).isEqualTo("2");
        }

        @Test
        @DisplayName("the right block runs, not merely a block")
        void runTheRightAction() {
            View view = viewOf(
                    "chosen: \"none\" ["
                            + "button \"one\" [chosen: \"first\"] "
                            + "button \"two\" [chosen: \"second\"]]");

            view.raise(view.actionHandles().get(1));

            assertThat(view.evaluate("chosen").display()).isEqualTo("\"second\"");
        }

        @Test
        @DisplayName("an event nobody registered is refused rather than guessed at")
        void refuseUnknownHandles() {
            View view = viewOf("[button \"Press\" [x: 1]]");

            assertThat(view.raise("not-a-real-handle").succeeded()).isFalse();
        }

        @Test
        @DisplayName("an action that fails does not take the view with it")
        void leaveTheViewUsableAfterAFailingAction() {
            View view = viewOf("[button \"Press\" [divide 1 0]]");

            assertThat(view.raise(view.actionHandles().get(0)).succeeded()).isFalse();
            assertThat(view.writeTheMarkup()).contains("Press");
        }
    }

    @Nested
    @DisplayName("the view is re-rendered from the values, not patched")
    class Rerendering {

        @Test
        @DisplayName("markup reflects state changed by an action")
        void followsTheState() {
            View view = viewOf(
                    "caption: \"before\" [button caption [caption: \"after\"]]");

            assertThat(view.writeTheMarkup()).contains("before");
            view.raise(view.actionHandles().get(0));

            assertThat(view.writeTheMarkup())
                    .as("the view is built again from the values, so it cannot drift")
                    .contains("after");
        }

        @Test
        @DisplayName("rendering the same state twice gives the same markup")
        void staysStable() {
            View view = viewOf("[text \"steady\"]");

            assertThat(view.writeTheMarkup()).isEqualTo(view.writeTheMarkup());
        }
    }
}
