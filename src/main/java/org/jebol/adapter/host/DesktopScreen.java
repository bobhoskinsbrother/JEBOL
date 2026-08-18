package org.jebol.adapter.host;

import org.jebol.domain.eval.ScreenEvent;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.eval.ScreenMetric;
import org.jebol.domain.eval.ScreenPort;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The operator's own screen, through Swing and Java2D.
 *
 * <p>The native renderer, and one of three the same port is meant to carry: a
 * desktop window here, a phone and a browser later. Nothing above this line
 * names a toolkit, so the other two are adapters rather than programs.
 *
 * <p>Swing rather than JavaFX because JavaFX left the JDK in 11 and this
 * project has no runtime dependencies. {@code Graphics2D} covers the whole
 * DRAW dialect when that arrives -- pen, fill, shapes, clip, anti-alias, text
 * and image -- at close to one call each.
 *
 * <p>The threading rule this obeys is not a preference. An interpreter is
 * owned by one thread and that is what lets series share mutable storage with
 * nothing synchronising them. Swing calls a listener on its own event thread,
 * so every listener here does one thing: it puts an event on a queue and
 * returns. The interpreter's thread takes it later, inside WAIT.
 *
 * <p>Refuses when there is no display, exactly as {@link DesktopWindows} does
 * and for the same reason. But it refuses only SHOW: measurements answer zero
 * and taking the root gob is accepted, because the view system comes up while
 * the library is still loading and a build server has no screen.
 */
public final class DesktopScreen implements ScreenPort {

    private final boolean present;
    private final Map<GobValue, JFrame> windows = new IdentityHashMap<>();
    private final Deque<ScreenEvent> queued = new ArrayDeque<>();

    private GobValue root;
    private org.jebol.domain.value.ObjectValue drawDialect;

    @Override
    public void useDrawDialect(Value dialect) {
        this.drawDialect = dialect instanceof org.jebol.domain.value.ObjectValue named
                ? named
                : null;
    }

    private DesktopScreen(boolean present) {
        this.present = present;
    }

    /** A screen, or one that refuses if this machine has not got one. */
    public static DesktopScreen onThisMachine() {
        return new DesktopScreen(!GraphicsEnvironment.isHeadless());
    }

    @Override
    public boolean hasADisplay() {
        return present;
    }

    @Override
    public int displayCount() {
        if (!present) {
            return 0;
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getScreenDevices().length;
    }

    @Override
    public PairValue measure(ScreenMetric metric, int display) {
        if (!present) {
            return PairValue.of(0, 0);
        }
        try {
            return measurementOf(metric, display);
        } catch (HeadlessException noScreen) {
            return PairValue.of(0, 0);
        }
    }

    /**
     * What each metric means on this toolkit.
     *
     * <p>Two of the twelve have no counterpart the JDK will tell us. A window
     * frame's thickness is not a system metric in Java, so the border ones are
     * read off a frame this class makes and throws away, which is the only
     * place the answer honestly is. And the smallest a window may be is a
     * Windows notion with no equivalent, so it is the smallest frame Swing
     * will lay out.
     */
    private PairValue measurementOf(ScreenMetric metric, int display) {
        Rectangle whole = boundsOfDisplay(display);
        Rectangle usable = workAreaOfDisplay(display);
        Insets furniture = furnitureOfAResizableWindow();
        return switch (metric) {
            case SCREEN_SIZE -> PairValue.of(whole.width, whole.height);
            case SCREEN_ORIGIN -> PairValue.of(whole.x, whole.y);
            case SCREEN_DPI -> dotsPerInch();
            case WORK_ORIGIN -> PairValue.of(usable.x, usable.y);
            case WORK_SIZE -> PairValue.of(usable.width, usable.height);
            case TITLE_SIZE -> PairValue.of(0, furniture.top);
            case BORDER_SIZE -> PairValue.of(furniture.left, furniture.bottom);
            case BORDER_FIXED -> PairValue.of(
                    furnitureOfAFixedWindow().left, furnitureOfAFixedWindow().bottom);
            case WINDOW_MIN_SIZE -> smallestWindowSwingWillLayOut();
            case LOG_SIZE, PHYS_SIZE -> PairValue.of(1, 1);
            case SCREENS -> PairValue.of(displayCount(), displayCount());
        };
    }

    private GraphicsDevice deviceAt(int display) {
        GraphicsDevice[] all = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices();
        return all[Math.min(Math.max(0, display), all.length - 1)];
    }

    private Rectangle boundsOfDisplay(int display) {
        return deviceAt(display).getDefaultConfiguration().getBounds();
    }

    private Rectangle workAreaOfDisplay(int display) {
        GraphicsConfiguration where = deviceAt(display).getDefaultConfiguration();
        Rectangle whole = where.getBounds();
        Insets taken = Toolkit.getDefaultToolkit().getScreenInsets(where);
        return new Rectangle(
                whole.x + taken.left, whole.y + taken.top,
                whole.width - taken.left - taken.right,
                whole.height - taken.top - taken.bottom);
    }

    private PairValue dotsPerInch() {
        int resolution = Toolkit.getDefaultToolkit().getScreenResolution();
        return PairValue.of(resolution, resolution);
    }

    private Insets furnitureOfAResizableWindow() {
        return furnitureOfAWindow(true);
    }

    private Insets furnitureOfAFixedWindow() {
        return furnitureOfAWindow(false);
    }

    /**
     * How thick a window's frame is, measured rather than looked up.
     *
     * <p>The JDK has no system metric for it, and a frame only knows its own
     * insets once it has been given a peer. So one is made, made
     * displayable, measured and disposed without ever being shown.
     */
    private Insets furnitureOfAWindow(boolean resizable) {
        JFrame measured = new JFrame();
        try {
            measured.setResizable(resizable);
            measured.pack();
            return measured.getInsets();
        } finally {
            measured.dispose();
        }
    }

    private PairValue smallestWindowSwingWillLayOut() {
        JFrame measured = new JFrame();
        try {
            measured.pack();
            Dimension smallest = measured.getMinimumSize();
            return PairValue.of(smallest.width, smallest.height);
        } finally {
            measured.dispose();
        }
    }

    @Override
    public void takeTheRootGob(GobValue given) {
        this.root = given;
    }

    @Override
    public void show(GobValue gob) {
        if (!present) {
            throw new Denied("no-service",
                    "this machine has no display to put a window on");
        }
        if (root == null) {
            return;
        }
        if (gob.sharesStorageWith(root)) {
            reconcileEveryWindow();
            return;
        }
        if (isInTheRootsPane(gob)) {
            openOrRefresh(gob);
            return;
        }
        closeTheWindowFor(gob);
    }

    /**
     * Closing what left the pane, then opening what arrived in it.
     *
     * <p>That order because the C walks it that way, and it is not arbitrary:
     * a host with a fixed number of window slots that opened first could run
     * out while still holding slots for windows already dismissed.
     */
    private void reconcileEveryWindow() {
        for (GobValue standing : List.copyOf(windows.keySet())) {
            if (!isInTheRootsPane(standing)) {
                closeTheWindowFor(standing);
            }
        }
        for (GobValue child : childrenOfTheRoot()) {
            openOrRefresh(child);
        }
    }

    private List<GobValue> childrenOfTheRoot() {
        List<GobValue> children = new ArrayList<>();
        for (Value child : root.storage().pane()) {
            if (child instanceof GobValue held) {
                children.add(held);
            }
        }
        return List.copyOf(children);
    }

    private boolean isInTheRootsPane(GobValue gob) {
        return childrenOfTheRoot().stream().anyMatch(gob::sharesStorageWith);
    }

    private void openOrRefresh(GobValue gob) {
        JFrame standing = windowFor(gob);
        if (standing != null) {
            onTheToolkitThread(standing::repaint);
            return;
        }
        onTheToolkitThread(() -> windows.put(gob, aWindowShowing(gob)));
    }

    private JFrame windowFor(GobValue gob) {
        for (Map.Entry<GobValue, JFrame> each : windows.entrySet()) {
            if (each.getKey().sharesStorageWith(gob)) {
                return each.getValue();
            }
        }
        return null;
    }

    private void closeTheWindowFor(GobValue gob) {
        JFrame standing = windowFor(gob);
        if (standing == null) {
            return;
        }
        windows.entrySet().removeIf(each -> each.getValue() == standing);
        onTheToolkitThread(standing::dispose);
    }

    private JFrame aWindowShowing(GobValue gob) {
        JFrame frame = new JFrame(titleOf(gob));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(aSurfacePainting(gob));
        frame.pack();
        frame.setLocationRelativeTo(null);
        listenOnBehalfOf(frame, gob);
        frame.setVisible(true);
        return frame;
    }

    private static String titleOf(GobValue gob) {
        Value text = gob.storage().contentIfKind(
                org.jebol.domain.value.GobStorage.Content.STRING);
        return text instanceof StringValue named && !named.text().isEmpty()
                ? named.text()
                : "REBOL: untitled";
    }

    private JPanel aSurfacePainting(GobValue gob) {
        org.jebol.domain.value.ObjectValue reading = drawDialect;
        return aSurfacePainting(gob, reading);
    }

    private static JPanel aSurfacePainting(
            GobValue gob, org.jebol.domain.value.ObjectValue drawDialect) {
        JPanel surface = new JPanel() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics onto) {
                super.paintComponent(onto);
                DesktopPainting.paintTheContentsOf(
                        (Graphics2D) onto, gob, drawDialect);
            }
        };
        surface.setPreferredSize(new Dimension(
                (int) Math.round(gob.storage().size().x()),
                (int) Math.round(gob.storage().size().y())));
        return surface;
    }

    /**
     * Every listener does one thing: it queues and returns.
     *
     * <p>Nothing here runs a handler, evaluates a block or touches a series.
     * Swing calls these on its own thread and the interpreter is owned by
     * another, so anything more would be a second thread inside the
     * interpreter -- and two threads appending to one block corrupt it
     * without either of them failing.
     */
    private void listenOnBehalfOf(JFrame frame, GobValue gob) {
        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent shutting) {
                queue(ScreenEventKind.CLOSE, gob);
            }
        });
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {

            @Override
            public void componentResized(java.awt.event.ComponentEvent changed) {
                queue(ScreenEventKind.RESIZE, gob);
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent changed) {
                queue(ScreenEventKind.OFFSET, gob);
            }
        });
        frame.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent typed) {
            }

            @Override
            public void keyPressed(KeyEvent down) {
                queue(ScreenEventKind.KEY, gob);
            }

            @Override
            public void keyReleased(KeyEvent up) {
                queue(ScreenEventKind.KEY_UP, gob);
            }
        });
        frame.getContentPane().addMouseListener(new MouseListener() {

            @Override
            public void mousePressed(MouseEvent down) {
                queue(ScreenEventKind.DOWN, gob);
            }

            @Override
            public void mouseReleased(MouseEvent up) {
                queue(ScreenEventKind.UP, gob);
            }

            @Override
            public void mouseClicked(MouseEvent clicked) {
            }

            @Override
            public void mouseEntered(MouseEvent arrived) {
            }

            @Override
            public void mouseExited(MouseEvent left) {
            }
        });
        frame.getContentPane().addMouseMotionListener(new MouseMotionListener() {

            @Override
            public void mouseMoved(MouseEvent moved) {
                queue(ScreenEventKind.MOVE, gob);
            }

            @Override
            public void mouseDragged(MouseEvent dragged) {
                queue(ScreenEventKind.MOVE, gob);
            }
        });
    }

    private synchronized void queue(ScreenEventKind kind, GobValue window) {
        queued.add(new ScreenEvent(kind, window));
    }

    @Override
    public synchronized List<ScreenEvent> takeQueuedEvents() {
        List<ScreenEvent> taken = List.copyOf(queued);
        queued.clear();
        return taken;
    }

    /**
     * Runs a piece of toolkit work where the toolkit wants it run, and waits.
     *
     * <p>Waiting because SHOW answers when the window is there rather than
     * when it has been asked for, and a caller that opened a window and then
     * measured it would otherwise measure nothing.
     */
    private static void onTheToolkitThread(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException failed) {
            throw new Denied("no-service",
                    "the screen could not do that: " + failed.getCause());
        }
    }
}
