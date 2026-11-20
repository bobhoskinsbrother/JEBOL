package org.jebol.android.functional;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.android.AppSession;
import org.jebol.android.JebolView;
import org.jebol.application.Interpreter;
import org.jebol.application.MemoryFiles;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.DrawingCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidScreenTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final AtomicReference<JebolView> made = new AtomicReference<>();
    private AppSession session;

    private AppSession openSession() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> made.set(new JebolView(context)));
        session = AppSession.open(made.get(), new MemoryFiles(), OutputPort.discard(), wanted -> {
        }, () -> false);
        return session;
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    private String answer(String source) {
        Interpreter interpreter = session.interpreter();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("view puts a paint list in front of the view, the gob's colour among its fills")
    void paintsWhatIsViewed() {
        openSession();
        made.get().measureViewport(800, 600);

        answer("view/no-wait make gob! [size: 100x80 color: 10.200.30]");

        assertThat(made.get().newestPainting())
                .filteredOn(DrawingCommand.Fill.class::isInstance)
                .map(each -> ((DrawingCommand.Fill) each).colour())
                .contains(new Colour(10, 200, 30));
    }

    @Test
    @DisplayName("the view's size, in dp, reaches the screen gob")
    void measuresTheViewInDp() {
        openSession();
        JebolView view = made.get();
        int wide = 800;
        int high = 600;

        view.measureViewport(wide, high);

        String expected = view.density().toDp(wide) + "x" + view.density().toDp(high);
        assertThat(answer("wait 0 system/view/screen-gob/size")).isEqualTo(expected);
        assertThat(answer("gui-metric 'screen-dpi"))
                .isEqualTo(view.density().screenDpi() + "x" + view.density().screenDpi());
    }

    @Test
    @DisplayName("a finger down and up on a face reaches its handler as down and up at its place in dp")
    void touchReachesTheHandler() {
        openSession();
        JebolView view = made.get();
        view.measureViewport(1600, 1200);
        answer("""
                heard: copy []
                view/no-wait make gob! [size: 400x300 color: 200.200.200]
                system/view/screen-gob/1/offset: 0x0
                show system/view/screen-gob
                handle-events [
                    name: 'touches
                    priority: 100
                    handler: func [event] [
                        if find [down up] event/type [append heard reduce [event/type event/offset]]
                        none
                    ]
                ]
                """);
        float density = view.getResources().getDisplayMetrics().density;

        touch(view, MotionEvent.ACTION_DOWN, 50 * density, 60 * density);
        touch(view, MotionEvent.ACTION_UP, 50 * density, 60 * density);

        assertThat(answer("wait [system/ports/event 0.3] heard")).isEqualTo("[down 50x60 up 50x60]");
    }

    @Test
    @DisplayName("go, then the operator's Back, arrives as a back event naming the place before")
    void backArrivesAsBack() {
        openSession();
        made.get().measureViewport(800, 600);
        answer("""
                view/no-wait make gob! [size: 40x40]
                heard: copy []
                handle-events [
                    name: 'moves
                    priority: 100
                    handler: func [event] [
                        if find [back forward] event/type [append/only heard reduce [event/type event/data]]
                        event
                    ]
                ]
                go 'survey
                """);
        assertThat(session.history().isBackWanted()).isTrue();

        session.followOperatorBack();

        assertThat(answer("wait [system/ports/event 0.3] heard")).isEqualTo("[[back []]]");
        assertThat(session.history().isBackWanted()).isFalse();
    }

    @Test
    @DisplayName("to image! of a gob is the pixels Android's pixel surface paints")
    void makesAnImageOfAGob() {
        openSession();
        made.get().measureViewport(800, 600);

        assertThat(answer("""
                picture: to image! make gob! [size: 4x3 color: 255.0.0]
                reduce [picture/size picture/1 picture/12]
                """)).isEqualTo("[4x3 255.0.0.255 255.0.0.255]");
    }

    @Test
    @DisplayName("text the soft keyboard commits, its delete and its action key reach the script as key events")
    void theKeyboardsTextArrivesAsKeys() {
        openSession();
        JebolView view = made.get();
        view.measureViewport(1600, 1200);
        answer("""
                heard: copy []
                view/no-wait make gob! [size: 400x300]
                handle-events [
                    name: 'keys
                    priority: 100
                    handler: func [event] [
                        if event/type = 'key [append heard event/key]
                        none
                    ]
                ]
                """);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            InputConnection connection = view.onCreateInputConnection(new EditorInfo());
            connection.commitText("hé", 1);
            connection.deleteSurroundingText(1, 0);
            connection.performEditorAction(EditorInfo.IME_ACTION_DONE);
        });

        assertThat(answer("wait [system/ports/event 0.3] heard")).isEqualTo("""
                [#"h" #"é" #"^H" #"^M"]""");
    }

    @Test
    @DisplayName("a finger's press says touch, and a mouse's does not")
    void aFingerSaysTouchAndAMouseDoesNot() {
        openSession();
        JebolView view = made.get();
        view.measureViewport(1600, 1200);
        answer("""
                heard: copy []
                view/no-wait make gob! [size: 400x300 color: 200.200.200]
                system/view/screen-gob/1/offset: 0x0
                show system/view/screen-gob
                handle-events [
                    name: 'touches
                    priority: 100
                    handler: func [event] [
                        if event/type = 'down [append/only heard event/flags]
                        none
                    ]
                ]
                """);

        int apart = ViewConfiguration.get(view.getContext()).getScaledDoubleTapSlop() + 1;
        press(view, MotionEvent.TOOL_TYPE_FINGER, 30);
        press(view, MotionEvent.TOOL_TYPE_STYLUS, 30 + apart);
        press(view, MotionEvent.TOOL_TYPE_MOUSE, 30 + 2 * apart);

        assertThat(answer("wait [system/ports/event 0.3] heard")).isEqualTo("[[touch] [touch] _]");
    }

    @Test
    @DisplayName("a finger's second tap in place reaches the script as a down saying double")
    void aSecondTapSaysDouble() {
        openSession();
        JebolView view = made.get();
        view.measureViewport(1600, 1200);
        answer("""
                heard: copy []
                view/no-wait make gob! [size: 400x300 color: 200.200.200]
                system/view/screen-gob/1/offset: 0x0
                show system/view/screen-gob
                handle-events [
                    name: 'touches
                    priority: 100
                    handler: func [event] [
                        if event/type = 'down [append/only heard event/flags]
                        none
                    ]
                ]
                """);

        press(view, MotionEvent.TOOL_TYPE_FINGER, 30);
        press(view, MotionEvent.TOOL_TYPE_FINGER, 30);

        assertThat(answer("wait [system/ports/event 0.3] heard")).isEqualTo("[[touch] [double touch]]");
    }

    @Test
    @DisplayName("the view takes typing only once show-soft-keyboard asks, so a tap alone raises no keyboard")
    void theViewIsATextEditorOnlyWhenAsked() {
        openSession();
        JebolView view = made.get();
        view.measureViewport(1600, 1200);
        answer("w: view/no-wait make gob! [size: 400x300]");

        assertThat(view.onCheckIsTextEditor()).isFalse();

        answer("show-soft-keyboard/attach w");

        assertThat(view.onCheckIsTextEditor()).isTrue();
    }

    private static void press(JebolView view, int toolType, float acrossPixels) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        pointer.toolType = toolType;
        MotionEvent.PointerCoords place = new MotionEvent.PointerCoords();
        place.x = acrossPixels;
        place.y = 30;
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now, action, 1,
                    new MotionEvent.PointerProperties[] {pointer}, new MotionEvent.PointerCoords[] {place},
                    0, 0, 1, 1, 0, 0, 0, 0);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> view.onTouchEvent(event));
            event.recycle();
        }
    }

    private static void touch(JebolView view, int action, float across, float down) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, across, down, 0);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> view.onTouchEvent(event));
        event.recycle();
    }
}
