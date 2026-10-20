package org.jebol.domain.host;

import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;

import java.util.Optional;

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
 * <p>{@link #offerShown()} is not a script's request: it is the
 * interpreter telling the port that it is between script steps, which is when
 * a deferred picture goes out.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public interface ScreenPort {

    /** Whether there is a display behind this port at all. */
    boolean hasDisplay();

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
    void acceptRootGob(GobValue root);

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
     * Offers whatever has been shown since the last offer, and nothing when
     * nothing has.
     *
     * <p>A show is remembered rather than painted where it happens, because a
     * screen only ever shows the tree as it stands: three faces shown while
     * one event is handled are one picture. Called where the interpreter is
     * between script steps -- before a run, when one ends, and inside a wait
     * once there are no more events to take.
     *
     * <p>An adapter over a toolkit does nothing here. Invalidating is already
     * cheap there and the toolkit itself coalesces the drawing, which is the
     * same shape arrived at from the other side.
     */
    default void offerShown() {
    }

    /**
     * The oldest event not yet taken, removed from the queue, or empty when
     * nothing is waiting.
     *
     * <p>Taken rather than delivered, because the thread that made them must
     * not be the thread that acts on them; and one at a time, so the events
     * behind one that wakes a port stay queued for the next wait. See
     * {@code spec/screen.allium}.
     */
    Optional<ScreenEvent> acceptNextEvent();

    /**
     * Takes everything the other side has measured but this port has not yet
     * taken: a measurement becomes the screen's own, resizing what hangs on
     * it. Events are left for the next {@link #acceptNextEvent}, which is
     * what decides the moment they are judged at.
     *
     * <p>Called on the interpreter's own thread -- before a run starts, and
     * whenever the wait asks for an event -- because that is the only place a
     * report may change what a script sees. A measurement applied where it
     * arrived would write a gob another thread was reading.
     */
    default void acceptPendingReports() {
    }

    TextMeasurement textMeasurement();

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
    default void takeAsRoot(GobValue root) {
        acceptRootGob(root);
        root.storage().detachFromParent();
        root.storage().size(hasDisplay()
                ? measure(ScreenMetric.SCREEN_SIZE, 0)
                : PairValue.of(0, 0));
    }

    /**
     * Tells the screen how to read a gob's draw block.
     *
     * <p>{@code system/dialects/draw}, handed over once, exactly as the root
     * gob is and for the same reason: flattening a gob tree has no way to
     * reach a system object and should not learn one. A screen never told
     * paints no draw blocks, which is a gap rather than a failure -- every
     * other type of gob still shows.
     *
     * <p>Held per screen rather than anywhere shared, because a host runs many
     * interpreters at once and each has its own {@code system}.
     */
    default void useDrawDialect(Value dialect) {
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
            public boolean hasDisplay() {
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
            public void acceptRootGob(GobValue root) {
            }

            @Override
            public void show(GobValue gob) {
                throw new Denied("no-service",
                        "this interpreter was given no screen to put a window on");
            }

            @Override
            public Optional<ScreenEvent> acceptNextEvent() {
                return Optional.empty();
            }

            @Override
            public TextMeasurement textMeasurement() {
                return run -> {
                    throw new Denied("no-service",
                            "this interpreter was given no screen to measure text with");
                };
            }
        };
    }
}
