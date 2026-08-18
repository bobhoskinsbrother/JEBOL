package org.jebol.domain.eval;

import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.List;

/**
 * How a script puts a gob tree on a screen and hears what happened to it.
 *
 * <p>A port the domain owns and an adapter implements, so the evaluator never
 * touches a widget toolkit. It speaks gobs, measurements and events and
 * nothing else, which is what lets one gob tree reach a desktop window, a
 * phone and a browser: they differ in how a gob is painted and in nothing a
 * script can see.
 *
 * <p>Behind the same grant as the five dialogs. The decision a host makes is
 * whether a script may put pixels on the operator's screen at all, and the
 * verb was never the interesting half.
 *
 * <p>Four requests, and they do not all need a display. Taking the root gob
 * and answering a measurement have answers when there is no screen; showing a
 * window does not. {@link #none()} is where that split is written down and
 * {@code spec/screen.allium} is where it is argued.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public interface ScreenPort {

    /** Whether there is a display behind this port at all. */
    boolean hasADisplay();

    /**
     * A measurement of the screen, as a pair of numbers.
     *
     * <p>Never called for {@link ScreenMetric#SCREENS}, which counts rather
     * than measures and has {@link #displayCount()} of its own.
     *
     * @param display which screen, counting from zero
     */
    PairValue measure(ScreenMetric metric, int display);

    /** How many displays there are. */
    int displayCount();

    /**
     * Takes the gob every window will hang under.
     *
     * <p>INIT-TOP-WINDOW hands this over once, from
     * {@code system/view/screen-gob}, and the port keeps it because SHOW needs
     * to know which gob is the root before it can tell reconciling the whole
     * screen from refreshing one window.
     */
    void takeTheRootGob(GobValue root);

    /**
     * Makes the screen's windows match the gob tree.
     *
     * <p>One verb doing three jobs, and which job it does is read off the gob
     * rather than said by the caller. That is why UNVIEW can be four lines: it
     * takes the gob out of the root's pane and calls this, and the removal is
     * what turns "refresh it" into "close it".
     */
    void show(GobValue gob);

    /**
     * The events that have arrived since this was last asked, and clears them.
     *
     * <p>Taken rather than delivered, because the thread that made them must
     * not be the thread that acts on them. See {@code spec/screen.allium}.
     */
    List<ScreenEvent> takeQueuedEvents();

    /**
     * Makes a gob the root: remembered, cut loose, and sized to the screen.
     *
     * <p>The whole of {@code CMD_WINDOW_INIT_TOP_WINDOW}, which is three lines
     * of C. It is here rather than inside the native because two callers need
     * it and only one of them is a script.
     *
     * <p>The second caller exists because of when {@code view-funcs.reb} runs.
     * INIT-VIEW-SYSTEM is called on that file's last line and its last act is
     * {@code init-top-window: init-view-system: 'done} -- the command is spent
     * on purpose, so that nothing can take the screen over afterwards. In a
     * real 3.22.1 that is harmless, because the graphics host was registered
     * before the library loaded. Here a host supplies a screen after the
     * interpreter is built, by which time the word holds {@code 'done}, so the
     * handover has to happen some other way.
     *
     * <p>The size is what makes it matter. VIEW centres a window with
     * {@code screen/size - window/size / 2}, so a root left at nothing puts
     * every centred window in the same place.
     */
    static void takeAsTheRoot(ScreenPort screen, GobValue root) {
        screen.takeTheRootGob(root);
        root.storage().detachFromParent();
        root.storage().size(screen.hasADisplay()
                ? screen.measure(ScreenMetric.SCREEN_SIZE, 0)
                : PairValue.of(0, 0));
    }

    /** Why the screen refused. Carries an error id the boundary reports. */
    final class Denied extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient String errorId;

        public Denied(String errorId, String because) {
            super(because, null, false, false);
            this.errorId = errorId;
        }

        public String errorId() {
            return errorId;
        }
    }

    /**
     * A screen that is not there, which is what a script gets by default and
     * what every build server has.
     *
     * <p>It answers about itself and refuses to be drawn on, and that split is
     * the decision worth defending. INIT-VIEW-SYSTEM runs on the last line of
     * {@code view-funcs.reb}, so refusing to take the root gob would stop that
     * file partway on a machine with no display -- and a machine with no
     * display is the only machine the library is ever tested on, so the tested
     * library and the shipped library would be different libraries.
     *
     * <p>Zero is a real answer rather than a stand-in. Rebol's own posix host
     * implements two metrics and returns zero for the other ten, so a script
     * reading {@code work-size} and getting {@code 0x0} is reading what a GTK
     * build tells it.
     */
    static ScreenPort none() {
        return new ScreenPort() {

            @Override
            public boolean hasADisplay() {
                return false;
            }

            @Override
            public PairValue measure(ScreenMetric metric, int display) {
                return PairValue.of(0, 0);
            }

            @Override
            public int displayCount() {
                return 0;
            }

            @Override
            public void takeTheRootGob(GobValue root) {
            }

            @Override
            public void show(GobValue gob) {
                throw new Denied("no-service",
                        "this interpreter was given no screen to put a window on");
            }

            @Override
            public List<ScreenEvent> takeQueuedEvents() {
                return List.of();
            }
        };
    }
}
