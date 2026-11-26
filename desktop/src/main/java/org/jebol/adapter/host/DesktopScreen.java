package org.jebol.adapter.host;

import org.jebol.application.DefaultFont;
import org.jebol.domain.host.WindowHistory;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.ShownWindow;
import org.jebol.domain.host.WindowArrangement;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.function.IntFunction;

public final class DesktopScreen extends GobScreen {

    private static final String MACOS_APP_NAME_PROPERTY = "apple.awt.application.name";
    private static final List<Integer> ICON_SIZES = List.of(16, 32, 64, 128, 256, 512);

    private static final String UNTITLED = "REBOL: untitled";
    private static final String OPERATING_SYSTEM_PROPERTY = "os.name";
    private static final long NANOSECONDS_IN_A_MILLISECOND = 1_000_000;

    private IntFunction<BufferedImage> icon = new JebolIcon()::drawAt;
    private Optional<String> identity = Optional.empty();
    private final boolean present;
    private final DefaultFont font;
    private final Font face;
    private static final int MILLISECONDS_A_REMAP_TAKES_TO_SETTLE = 750;

    private final Map<GobValue, JFrame> windows = new IdentityHashMap<>();
    private final Map<JFrame, WindowStateReports> stateReports = new IdentityHashMap<>();
    private final Map<GobStorage, Double> shownScales = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<GobValue> openedInOrder = new ArrayList<>();
    private final LeavingConfirmation leaving;
    private final BackForwardChords chords;
    private final DesktopSystem system;
    private Optional<Cursor> chosenCursor = Optional.empty();

    private DesktopScreen(FilePort files, boolean present, LeavingConfirmation leaving, DesktopSystem system) {
        super(() -> System.nanoTime() / NANOSECONDS_IN_A_MILLISECOND, new WindowHistory());
        this.present = present;
        this.font = DefaultFont.readFrom(files);
        this.face = Java2DSurface.faceFrom(font.readFontBytes());
        this.leaving = leaving;
        this.chords = new BackForwardChords(system);
        this.system = system;
    }

    public static DesktopScreen onThisMachine(FilePort files) {
        System.setProperty(MACOS_APP_NAME_PROPERTY, JebolIcon.APPLICATION_NAME);
        DesktopSystem system = DesktopSystem.named(System.getProperty(OPERATING_SYSTEM_PROPERTY, ""));
        return confirmingWith(files, new SwingLeavingConfirmation(), system);
    }

    static DesktopScreen confirmingWith(FilePort files, LeavingConfirmation asking, DesktopSystem system) {
        return new DesktopScreen(files, !GraphicsEnvironment.isHeadless(), asking, system);
    }

    void requestClose(GobValue gob) {
        if (navigation().isGuarding() && !leaving.operatorLeaves()) {
            return;
        }
        post(ScreenEventType.CLOSE, gob);
    }

    void pressKey(int keyCode, int modifiers, char typed, GobValue window) {
        queueKey(keyCode, typed, ScreenEventType.KEY, ScreenEventType.CONTROL, window);
        chords.keyDirection(keyCode, modifiers).ifPresent(navigation()::reportMoveRequest);
    }

    void pressButton(int button, int across, int down, GobValue window) {
        if (chords.isSidewaysTurn(button)) {
            return;
        }
        post(ScreenEventType.DOWN, window, new ScreenEventDetail.At(across, down));
        chords.buttonDirection(button).ifPresent(navigation()::reportMoveRequest);
    }

    void releaseButton(int button, int across, int down, GobValue window) {
        if (chords.isSidewaysTurn(button)) {
            return;
        }
        post(ScreenEventType.UP, window, new ScreenEventDetail.At(across, down));
    }

    public DesktopScreen wearing(AppIcon appsOwn) {
        icon = appsOwn::drawAt;
        return this;
    }

    public DesktopScreen belongingTo(String appIdentity) {
        identity = Optional.of(appIdentity);
        return this;
    }

    BufferedImage iconAt(int size) {
        return icon.apply(size);
    }

    private void showIcon(JFrame frame) {
        List<Image> everySize = ICON_SIZES.stream().<Image>map(icon::apply).toList();
        frame.setIconImages(everySize);
        if (Taskbar.isTaskbarSupported()
                && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(everySize.getLast());
        }
    }

    @Override
    public boolean hasDisplay() {
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
            return measureMetric(metric, display);
        } catch (HeadlessException noScreen) {
            return PairValue.of(0, 0);
        }
    }

    private PairValue measureMetric(ScreenMetric metric, int display) {
        Rectangle whole = displayDimensions(display);
        Rectangle usable = workAreaOfDisplay(display);
        Insets furniture = resizableWindowInsets();
        return switch (metric) {
            case SCREEN_SIZE -> PairValue.of(whole.width, whole.height);
            case SCREEN_ORIGIN -> PairValue.of(whole.x, whole.y);
            case SCREEN_DPI -> measureDotsPerInch();
            case WORK_ORIGIN -> PairValue.of(usable.x, usable.y);
            case WORK_SIZE -> PairValue.of(usable.width, usable.height);
            case TITLE_SIZE -> PairValue.of(0, furniture.top);
            case BORDER_SIZE -> PairValue.of(furniture.left, furniture.bottom);
            case BORDER_FIXED -> PairValue.of(
                    fixedWindowInsets().left, fixedWindowInsets().bottom);
            case WINDOW_MIN_SIZE -> measureSmallestWindowSwingLaysOut();
            case LOG_SIZE, PHYS_SIZE -> PairValue.of(1, 1);
            case SCREENS -> PairValue.of(displayCount(), displayCount());
        };
    }

    private GraphicsDevice findDeviceAt(int display) {
        GraphicsDevice[] all = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices();
        return all[Math.min(Math.max(0, display), all.length - 1)];
    }

    private Rectangle displayDimensions(int display) {
        return findDeviceAt(display).getDefaultConfiguration().getBounds();
    }

    private Rectangle workAreaOfDisplay(int display) {
        GraphicsConfiguration where = findDeviceAt(display).getDefaultConfiguration();
        Rectangle whole = where.getBounds();
        Insets taken = Toolkit.getDefaultToolkit().getScreenInsets(where);
        return new Rectangle(
                whole.x + taken.left, whole.y + taken.top,
                whole.width - taken.left - taken.right,
                whole.height - taken.top - taken.bottom);
    }

    private PairValue measureDotsPerInch() {
        int resolution = Toolkit.getDefaultToolkit().getScreenResolution();
        return PairValue.of(resolution, resolution);
    }

    private Insets resizableWindowInsets() {
        return measureWindowInsets(true);
    }

    private Insets fixedWindowInsets() {
        return measureWindowInsets(false);
    }

    private Insets measureWindowInsets(
            boolean resizable) {
        JFrame measured = new JFrame();
        try {
            measured.setResizable(resizable);
            measured.pack();
            return measured.getInsets();
        } finally {
            measured.dispose();
        }
    }

    private PairValue measureSmallestWindowSwingLaysOut() {
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
    public TextMeasurement textMeasurement() {
        if (!present) {
            return ScreenPort.none().textMeasurement();
        }
        return font.measurement();
    }

    @Override
    protected Optional<ImagePort.Pixels> paintOffscreen(PaintList painting, int wide, int high) {
        return Optional.of(Java2DSurface.paintOnWhite(painting.calls(font.measurement()), wide, high, face));
    }

    @Override
    protected Denied refuseWithNoService() {
        return new Denied("no-service", "this machine has no display to put a window on");
    }

    @Override
    protected List<GobValue> gobWithWindow() {
        return List.copyOf(windows.keySet());
    }

    @Override
    protected void openWindowFor(GobValue gob) {
        onToolkitThreadAndWaitedFor(() -> windows.put(gob, showWindow(gob)));
        openedInOrder.add(gob);
    }

    @Override
    protected void repaintWindowFor(GobValue gob) {
        findWindowFor(gob).ifPresent(standing -> onToolkitThreadAndWaitedFor(() -> {
            if (isFirstOpenWindow(gob)) {
                standing.setTitle(windowTitle(gob));
            }
            standing.repaint();
        }));
    }

    @Override
    protected void closeWindowFor(GobValue gob) {
        openedInOrder.removeIf(gob::sharesStorageWith);
        shownScales.remove(gob.storage());
        findWindowFor(gob).ifPresent(standing -> {
            windows.entrySet().removeIf(each -> each.getValue() == standing);
            onToolkitThreadAndWaitedFor(() -> {
                stateReports.remove(standing);
                standing.dispose();
            });
        });
    }

    @Override
    protected void arrangeOpenWindow(GobValue window, WindowArrangement arrangement) {
        findWindowFor(window).ifPresent(frame -> onToolkitThreadAndWaitedFor(() -> {
            switch (arrangement) {
                case MINIMIZE -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED);
                case MAXIMIZE -> showInState(frame, window, Frame.MAXIMIZED_BOTH);
                case RESTORE -> showInState(frame, window, Frame.NORMAL);
                case ACTIVATE -> {
                    frame.toFront();
                    frame.requestFocus();
                }
            }
        }));
    }

    private void showInState(JFrame frame, GobValue window, int state) {
        if ((frame.getExtendedState() & Frame.ICONIFIED) == 0) {
            frame.setExtendedState(state);
            return;
        }
        if (system.bringsMinimisedWindowBackInPlace()) {
            bringBackFromDock(frame, window, state);
            return;
        }
        WindowStateReports reports = stateReports.get(frame);
        reports.beginRemap();
        frame.setVisible(false);
        frame.setExtendedState(Frame.NORMAL);
        frame.setVisible(true);
        afterRemapSettles(() -> {
            if (state == Frame.NORMAL) {
                endRemap(frame, window, reports);
                return;
            }
            frame.setExtendedState(state);
            afterRemapSettles(() -> endRemap(frame, window, reports));
        });
    }

    private void bringBackFromDock(JFrame frame, GobValue window, int state) {
        if (state == Frame.NORMAL) {
            frame.setExtendedState(Frame.NORMAL);
            return;
        }
        WindowStateReports reports = stateReports.get(frame);
        reports.beginRemap();
        onceStateReaches(frame, Frame.NORMAL, () -> {
            frame.setExtendedState(state);
            onceStateReaches(frame, state, () -> afterRemapSettles(() -> endRemap(frame, window, reports)));
        });
        frame.setExtendedState(Frame.NORMAL);
    }

    private static void onceStateReaches(JFrame frame, int wanted, Runnable next) {
        frame.addWindowStateListener(new WindowStateListener() {

            @Override
            public void windowStateChanged(WindowEvent changed) {
                if (changed.getNewState() == wanted) {
                    frame.removeWindowStateListener(this);
                    next.run();
                }
            }
        });
    }

    private void endRemap(JFrame frame, GobValue window, WindowStateReports reports) {
        Dimension drawable = frame.getContentPane().getSize();
        postEach(reports.endRemap(frame.getExtendedState(), drawable.width, drawable.height), window);
    }

    private static void afterRemapSettles(Runnable next) {
        javax.swing.Timer settled = new javax.swing.Timer(MILLISECONDS_A_REMAP_TAKES_TO_SETTLE, done -> next.run());
        settled.setRepeats(false);
        settled.start();
    }

    private boolean isFirstOpenWindow(GobValue gob) {
        return !openedInOrder.isEmpty() && openedInOrder.getFirst().sharesStorageWith(gob);
    }

    private Optional<JFrame> findWindowFor(GobValue gob) {
        return windows.entrySet().stream()
                .filter(each -> each.getKey().sharesStorageWith(gob))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public void useCursor(ScreenCursor cursor) {
        if (!present) {
            return;
        }
        onToolkitThreadAndWaitedFor(() -> {
            Cursor chosen = AwtCursors.cursorFor(cursor);
            chosenCursor = Optional.of(chosen);
            windows.values().forEach(frame -> frame.setCursor(chosen));
        });
    }

    private JFrame showWindow(GobValue gob) {
        JFrame frame = new JFrame(windowTitle(gob));
        chosenCursor.ifPresent(frame::setCursor);
        showIcon(frame);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(paintSurface(gob));
        frame.pack();
        identity.ifPresent(appIdentity -> WindowsAppIdentity.declareForWindow(frame.getTitle(), appIdentity));
        frame.setLocationRelativeTo(null);
        attachListenersThatOnlyQueue(frame, gob);
        frame.setVisible(true);
        return frame;
    }

    private String windowTitle(GobValue gob) {
        return readTitle(gob).orElse(UNTITLED);
    }

    private JPanel paintSurface(GobValue gob) {
        org.jebol.domain.value.ObjectValue reading = drawDialect;
        return paintSurface(gob, reading);
    }

    private JPanel paintSurface(
            GobValue gob, org.jebol.domain.value.ObjectValue drawDialect) {
        DesktopScreen onThisScreen = this;
        Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        PairValue space = PairValue.of(workArea.width, workArea.height);
        PairValue own = gob.storage().size();
        boolean resizable = gob.storage().listRaisedFlags().contains(GobStorage.Flag.RESIZE);
        double scale = resizable ? 1.0 : ShownWindow.of(space, own, PairValue.of(0, 0), 0).scale();
        shownScales.put(gob.storage(), scale);
        JPanel surface = new JPanel() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics onto) {
                super.paintComponent(onto);
                Graphics2D drawing = (Graphics2D) onto;
                if (scale != 1.0) {
                    drawing.scale(scale, scale);
                }
                Java2DSurface.paintContentsLeavingItsTitleToTitleBar(
                        drawing, gob, drawDialect, onThisScreen.textMeasurement(),
                        onThisScreen.face);
            }
        };
        double wide = resizable ? Math.min(own.x(), space.x()) : own.x() * scale;
        double high = resizable ? Math.min(own.y(), space.y()) : own.y() * scale;
        surface.setPreferredSize(new Dimension((int) Math.floor(wide), (int) Math.floor(high)));
        return surface;
    }

    private int unscaled(GobValue gob, int pixel) {
        return (int) Math.round(pixel / shownScales.getOrDefault(gob.storage(), 1.0));
    }

    private void postEach(List<WindowStateReports.Report> reports, GobValue gob) {
        reports.forEach(report -> post(report.kind(), gob, report.detail()));
    }

    private void attachListenersThatOnlyQueue(JFrame frame, GobValue gob) {
        WindowStateReports states = new WindowStateReports(
                () -> System.nanoTime() / NANOSECONDS_IN_A_MILLISECOND);
        stateReports.put(frame, states);
        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent shutting) {
                requestClose(gob);
            }
        });
        frame.addWindowStateListener(changed -> {
            Dimension drawable = frame.getContentPane().getSize();
            postEach(states.stateChanged(changed.getOldState(), changed.getNewState(),
                    drawable.width, drawable.height), gob);
            javax.swing.Timer overdue = new javax.swing.Timer(
                    (int) WindowStateReports.MILLISECONDS_A_STATE_WAITS_FOR_ITS_RESIZE, late -> {
                        Dimension standing = frame.getContentPane().getSize();
                        postEach(states.takeOverdue(standing.width, standing.height), gob);
                    });
            overdue.setRepeats(false);
            overdue.start();
        });
        frame.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent changed) {
                Dimension drawable = frame.getContentPane().getSize();
                postEach(states.resized(drawable.width, drawable.height), gob);
            }

            @Override
            public void componentMoved(ComponentEvent changed) {
                post(ScreenEventType.OFFSET, gob,
                        new ScreenEventDetail.At(frame.getX(), frame.getY()));
            }
        });
        frame.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent typed) {
            }

            @Override
            public void keyPressed(KeyEvent down) {
                pressKey(down.getKeyCode(), down.getModifiersEx(), down.getKeyChar(), gob);
            }

            @Override
            public void keyReleased(KeyEvent up) {
                queueKey(up.getKeyCode(), up.getKeyChar(), ScreenEventType.KEY_UP, ScreenEventType.CONTROL_UP, gob);
            }
        });
        frame.getContentPane().addMouseListener(new MouseListener() {

            @Override
            public void mousePressed(MouseEvent down) {
                pressButton(down.getButton(), unscaled(gob, down.getX()), unscaled(gob, down.getY()), gob);
            }

            @Override
            public void mouseReleased(MouseEvent up) {
                releaseButton(up.getButton(), unscaled(gob, up.getX()), unscaled(gob, up.getY()), gob);
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
                post(ScreenEventType.MOVE, gob, locatePointer(gob, moved));
            }

            @Override
            public void mouseDragged(MouseEvent dragged) {
                post(ScreenEventType.MOVE, gob, locatePointer(gob, dragged));
            }
        });
        frame.getContentPane().addMouseWheelListener(new MouseWheelListener() {

            private final WheelScrolling wheel = new WheelScrolling();

            @Override
            public void mouseWheelMoved(MouseWheelEvent turnedBy) {
                boolean scrollsByBlock = turnedBy.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL;
                wheel.take(turnedBy.getPreciseWheelRotation(), turnedBy.getModifiersEx(), scrollsByBlock)
                        .ifPresent(turning -> post(turning.kind(), gob,
                                new ScreenEventDetail.Scrolled(unscaled(gob, turnedBy.getX()),
                                        unscaled(gob, turnedBy.getY()), turning.amount())));
            }
        });
    }

    private ScreenEventDetail locatePointer(GobValue gob, MouseEvent pointer) {
        return new ScreenEventDetail.At(unscaled(gob, pointer.getX()), unscaled(gob, pointer.getY()));
    }

    private void queueKey(int keyCode, char typed, ScreenEventType typing, ScreenEventType naming,
                          GobValue window) {

        Optional<String> named = Optional.ofNullable(KEYS_THAT_TYPE_NOTHING.get(keyCode));
        if (named.isPresent()) {
            post(naming, window, new ScreenEventDetail.NamedKey(named.get()));
            return;
        }
        if (typed != KeyEvent.CHAR_UNDEFINED) {
            post(typing, window, new ScreenEventDetail.Typed(typed));
        }
    }

    private static final Map<Integer, String> KEYS_THAT_TYPE_NOTHING = Map.ofEntries(
            Map.entry(KeyEvent.VK_PAGE_UP, "page-up"),
            Map.entry(KeyEvent.VK_PAGE_DOWN, "page-down"),
            Map.entry(KeyEvent.VK_END, "end"),
            Map.entry(KeyEvent.VK_HOME, "home"),
            Map.entry(KeyEvent.VK_LEFT, "left"),
            Map.entry(KeyEvent.VK_UP, "up"),
            Map.entry(KeyEvent.VK_RIGHT, "right"),
            Map.entry(KeyEvent.VK_DOWN, "down"),
            Map.entry(KeyEvent.VK_INSERT, "insert"),
            Map.entry(KeyEvent.VK_F1, "f1"),
            Map.entry(KeyEvent.VK_F2, "f2"),
            Map.entry(KeyEvent.VK_F3, "f3"),
            Map.entry(KeyEvent.VK_F4, "f4"),
            Map.entry(KeyEvent.VK_F5, "f5"),
            Map.entry(KeyEvent.VK_F6, "f6"),
            Map.entry(KeyEvent.VK_F7, "f7"),
            Map.entry(KeyEvent.VK_F8, "f8"),
            Map.entry(KeyEvent.VK_F9, "f9"),
            Map.entry(KeyEvent.VK_F10, "f10"),
            Map.entry(KeyEvent.VK_F11, "f11"),
            Map.entry(KeyEvent.VK_F12, "f12"),
            Map.entry(KeyEvent.VK_SHIFT, "shift"),
            Map.entry(KeyEvent.VK_CONTROL, "control"),
            Map.entry(KeyEvent.VK_ALT, "alt"),
            Map.entry(KeyEvent.VK_PAUSE, "pause"),
            Map.entry(KeyEvent.VK_CAPS_LOCK, "capital"));

    private static void onToolkitThreadAndWaitedFor(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException failed) {
            throw new Denied("no-service",
                    "the screen could not do that: " + failed.getCause());
        }
    }
}
