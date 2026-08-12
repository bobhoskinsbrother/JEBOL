package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MAP-EVENT and WAKE-UP, the two functions the event datatype unblocked.
 *
 * <p>Both are the window system's side of the language, and neither needs a
 * window to do what it does.
 *
 * <p>MAP-EVENT is MAP-GOB-OFFSET with the gob and the point taken out of an
 * event: it finds the deepest gob under the click and rewrites the event to name
 * that gob and a point inside it. Same {@code Map_Gob_Inner}, one caller along.
 *
 * <p>WAKE-UP asks a port to deal with an event. Two steps, and each has a
 * condition on it: the port's UPDATE action runs only if its actor is a native,
 * and its AWAKE function runs only if it has one. The answer says whether the
 * port is finished waiting, and the default is yes.
 */
class MapEventAndWakeUpFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    /** A port, and the only way to one here. */
    private static final String A_PORT = "p: make port! system/standard/port ";

    @Nested
    @DisplayName("MAP-EVENT")
    class MappingAnEvent {

        /** A window holding one child at 10x10 that is 20 by 20, and a click in it. */
        private static final String A_WINDOW =
                "w: make gob! [size: 100x100] "
                + "c: make gob! [offset: 10x10 size: 20x20] append w c ";

        @Test
        @DisplayName("an event over a child comes back naming the child and a point inside it")
        void itFindsTheDeepestGob() {
            // `VAL_EVENT_SER(val) = Map_Gob_Inner(gob, &xy); SET_EVENT_XY(val,
            // ROUND_TO_INT(xy.x), ROUND_TO_INT(xy.y));` -- the same walk
            // MAP-GOB-OFFSET does, with the gob and the point read out of the event
            // instead of given as arguments.
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w offset: 15x15] "
                    + "m: map-event e same? c m/gob")).isEqualTo(TRUE);
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w offset: 15x15] "
                    + "m: map-event e m/offset")).isEqualTo("5x5");
        }

        @Test
        @DisplayName("and an event over no child keeps the gob it had")
        void nothingToDescendInto() {
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w offset: 50x50] "
                    + "m: map-event e same? w m/gob")).isEqualTo(TRUE);
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w offset: 50x50] "
                    + "m: map-event e m/offset")).isEqualTo("50x50");
        }

        @Test
        @DisplayName("an event with no offset is left alone, because there is nowhere to look")
        void withoutAnOffset() {
            // `if (gob && GET_FLAG(VAL_EVENT_FLAGS(val), EVF_HAS_XY))` -- both
            // conditions, so a key event goes straight through.
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w] m: map-event e same? w m/gob"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w] m: map-event e none? m/offset"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an event with no gob is left alone too")
        void withoutAGob() {
            assertThat(answerTo(
                    "e: make event! [offset: 15x15] m: map-event e m/offset"))
                    .isEqualTo("15x15");
            assertThat(answerTo(
                    "e: make event! [offset: 15x15] m: map-event e none? m/gob"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the answer carries the change and the caller's word does not")
        void theChangeIsInTheAnswer() {
            // `return R_ARG1` hands back the copy on the data stack, and an event is
            // inline in a value cell rather than a series -- so this is the same
            // shape TINT has on a tuple. `e: map-event e` is how a script keeps the
            // result.
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w offset: 15x15] map-event e "
                    + "same? w e/gob")).isEqualTo(TRUE);
            assertThat(answerTo(A_WINDOW
                    + "e: make event! [gob: w offset: 15x15] map-event e e/offset"))
                    .isEqualTo("15x15");
        }

        @Test
        @DisplayName("it descends more than one level, and rounds the point it ends on")
        void severalLevelsAndRounding() {
            // The walk is the whole tree, and `ROUND_TO_INT` is what puts the
            // floating result back into an event's two shorts. A gob's offset is a
            // float pair, so a fractional one is what makes the rounding visible.
            assertThat(answerTo(
                    "w: make gob! [size: 100x100] "
                    + "mid: make gob! [offset: 5x5 size: 50x50] "
                    + "deep: make gob! [offset: 10x10 size: 20x20] "
                    + "append w mid append mid deep "
                    + "e: make event! [gob: w offset: 20x20] "
                    + "m: map-event e same? deep m/gob")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "w: make gob! [size: 100x100] "
                    + "c: make gob! [offset: 1.6x1.6 size: 50x50] append w c "
                    + "e: make event! [gob: w offset: 10x10] "
                    + "m: map-event e m/offset")).isEqualTo("8x8");
        }

        @Test
        @DisplayName("and it takes an event and nothing else")
        void itsArgument() {
            // `event [event!]`
            assertThat(errorIdFrom("map-event 1")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("map-event make gob! []")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("WAKE-UP")
    class WakingAPort {

        @Test
        @DisplayName("a port with no awake function is woken")
        void noAwakeMeansAwake() {
            // `val = OFV(port, STD_PORT_AWAKE); if (ANY_FUNC(val)) {...} return
            // R_TRUE;` -- so the default is yes, and the awake function exists to
            // say no.
            assertThat(answerTo(A_PORT + "wake-up p make event! [type: 'read]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and one whose awake function says true is woken")
        void awakeSaysTrue() {
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [true] "
                    + "wake-up p make event! [type: 'read]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("one whose awake function says false is not")
        void awakeSaysFalse() {
            // `if (!(IS_LOGIC(val) && VAL_LOGIC(val))) return R_FALSE;`
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [false] "
                    + "wake-up p make event! [type: 'read]")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and anything that is not the logic true is also not")
        void anythingButTrueIsFalse() {
            // Two conditions and not one: the answer has to be a logic *and* it has
            // to be true. So a port whose awake function answers the event, or a
            // number, or none, is not woken -- and a truthy non-logic does not
            // count, which is the part a reading of "if the awake function says so"
            // would get wrong.
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [none] "
                    + "wake-up p make event! [type: 'read]")).isEqualTo(FALSE);
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [1] "
                    + "wake-up p make event! [type: 'read]")).isEqualTo(FALSE);
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [event] "
                    + "wake-up p make event! [type: 'read]")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("the awake function is handed the event")
        void theAwakeFunctionSeesTheEvent() {
            // `Apply_Func(0, val, D_ARG(2), 0)` -- one argument, the event, so a
            // port can decide by looking at what happened.
            assertThat(answerTo(A_PORT
                    + "seen: none p/awake: func [event] [seen: event/type true] "
                    + "wake-up p make event! [type: 'connect] seen"))
                    .isEqualTo("connect");
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [event/type = 'read] "
                    + "wake-up p make event! [type: 'read]")).isEqualTo(TRUE);
            assertThat(answerTo(A_PORT
                    + "p/awake: func [event] [event/type = 'read] "
                    + "wake-up p make event! [type: 'close]")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and it takes a port and an event, in that order")
        void itsArguments() {
            // `port [port!]` and `event [event!]`.
            assertThat(errorIdFrom("wake-up 1 make event! []")).isEqualTo("expect-arg");
            assertThat(errorIdFrom(A_PORT + "wake-up p 1")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("wake-up (make event! []) (make event! [])"))
                    .isEqualTo("expect-arg");
        }
    }
}
