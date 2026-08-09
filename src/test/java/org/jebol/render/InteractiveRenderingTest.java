package org.jebol.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A layout that responds to events.
 *
 * <p>Written before it exists. REBOL's own event model was built for a local
 * window with no round trip in it, so the question this milestone answers is
 * what an action block means when the thing that triggers it is somewhere
 * else entirely.
 *
 * <p>The answer taken here is the same shape as Phoenix LiveView or Hotwire:
 * the view lives on the server, an event arrives naming which face was
 * touched, the action block runs, and the view is rendered again. Nothing is
 * sent to the browser but markup, so a script never runs anywhere but here.
 */
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
            View view = viewOf("layout [button \"Press\" [count: 1]]");

            assertThat(view.markup()).contains("data-jebol-action=");
        }

        @Test
        @DisplayName("a face without one does not")
        void inertFacesAreNotNamed() {
            View view = viewOf("layout [text \"just words\"]");

            assertThat(view.markup()).doesNotContain("data-jebol-action=");
        }

        @Test
        @DisplayName("each actionable face gets its own handle")
        void handlesAreDistinct() {
            View view = viewOf(
                    "layout [button \"one\" [x: 1] button \"two\" [x: 2]]");

            assertThat(view.actionHandles()).hasSize(2).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("an event runs the block that belongs to it")
    class Events {

        @Test
        void anEventRunsItsAction() {
            View view = viewOf(
                    "count: 0 layout [button \"Press\" [count: add count 1]]");

            view.raise(view.actionHandles().get(0));

            assertThat(view.evaluate("count").display()).isEqualTo("1");
        }

        @Test
        @DisplayName("pressing twice runs it twice")
        void eventsAccumulate() {
            View view = viewOf(
                    "count: 0 layout [button \"Press\" [count: add count 1]]");
            String handle = view.actionHandles().get(0);

            view.raise(handle);
            view.raise(handle);

            assertThat(view.evaluate("count").display()).isEqualTo("2");
        }

        @Test
        @DisplayName("the right block runs, not merely a block")
        void theRightActionRuns() {
            View view = viewOf(
                    "chosen: \"none\" layout ["
                            + "button \"one\" [chosen: \"first\"] "
                            + "button \"two\" [chosen: \"second\"]]");

            view.raise(view.actionHandles().get(1));

            assertThat(view.evaluate("chosen").display()).isEqualTo("\"second\"");
        }

        @Test
        @DisplayName("an event nobody registered is refused rather than guessed at")
        void unknownHandlesAreRefused() {
            View view = viewOf("layout [button \"Press\" [x: 1]]");

            assertThat(view.raise("not-a-real-handle").succeeded()).isFalse();
        }

        @Test
        @DisplayName("an action that fails does not take the view with it")
        void aFailingActionLeavesTheViewUsable() {
            View view = viewOf("layout [button \"Press\" [divide 1 0]]");

            assertThat(view.raise(view.actionHandles().get(0)).succeeded()).isFalse();
            assertThat(view.markup()).contains("Press");
        }
    }

    @Nested
    @DisplayName("the view is re-rendered from the values, not patched")
    class Rerendering {

        @Test
        @DisplayName("markup reflects state changed by an action")
        void markupFollowsState() {
            View view = viewOf(
                    "caption: \"before\" layout [button caption [caption: \"after\"]]");

            assertThat(view.markup()).contains("before");
            view.raise(view.actionHandles().get(0));

            assertThat(view.markup())
                    .as("the view is built again from the values, so it cannot drift")
                    .contains("after");
        }

        @Test
        @DisplayName("rendering the same state twice gives the same markup")
        void rerenderingIsStable() {
            View view = viewOf("layout [text \"steady\"]");

            assertThat(view.markup()).isEqualTo(view.markup());
        }
    }
}
