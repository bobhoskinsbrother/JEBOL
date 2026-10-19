package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.application.TextMeasures;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.render.TextMeasure;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;

public final class DesktopScreen extends GobScreen {

    private static final String MACOS_APP_NAME_PROPERTY = "apple.awt.application.name";
    private static final List<Integer> ICON_SIZES = List.of(16, 32, 64, 128, 256, 512);

    private final JebolIcon icon = new JebolIcon();
    private final boolean present;
    private final Map<GobValue, JFrame> windows = new IdentityHashMap<>();

    private DesktopScreen(boolean present) {
        this.present = present;
    }

    public static DesktopScreen onThisMachine() {
        System.setProperty(MACOS_APP_NAME_PROPERTY, JebolIcon.APPLICATION_NAME);
        return new DesktopScreen(!GraphicsEnvironment.isHeadless());
    }

    private void showJebolIcon(JFrame frame) {
        List<Image> everySize = ICON_SIZES.stream().<Image>map(icon::drawAt).toList();
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
            return measureTheMetric(metric, display);
        } catch (HeadlessException noScreen) {
            return PairValue.of(0, 0);
        }
    }

    private PairValue measureTheMetric(ScreenMetric metric, int display) {
        Rectangle whole = displayDimensions(display);
        Rectangle usable = workAreaOfDisplay(display);
        Insets furniture = furnitureOfAResizableWindow();
        return switch (metric) {
            case SCREEN_SIZE -> PairValue.of(whole.width, whole.height);
            case SCREEN_ORIGIN -> PairValue.of(whole.x, whole.y);
            case SCREEN_DPI -> measureTheDotsPerInch();
            case WORK_ORIGIN -> PairValue.of(usable.x, usable.y);
            case WORK_SIZE -> PairValue.of(usable.width, usable.height);
            case TITLE_SIZE -> PairValue.of(0, furniture.top);
            case BORDER_SIZE -> PairValue.of(furniture.left, furniture.bottom);
            case BORDER_FIXED -> PairValue.of(
                    measureTheFurnitureOfAFixedWindow().left, measureTheFurnitureOfAFixedWindow().bottom);
            case WINDOW_MIN_SIZE -> measureTheSmallestWindowSwingLaysOut();
            case LOG_SIZE, PHYS_SIZE -> PairValue.of(1, 1);
            case SCREENS -> PairValue.of(displayCount(), displayCount());
        };
    }

    private GraphicsDevice findTheDeviceAt(int display) {
        GraphicsDevice[] all = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices();
        return all[Math.min(Math.max(0, display), all.length - 1)];
    }

    private Rectangle displayDimensions(int display) {
        return findTheDeviceAt(display).getDefaultConfiguration().getBounds();
    }

    private Rectangle workAreaOfDisplay(int display) {
        GraphicsConfiguration where = findTheDeviceAt(display).getDefaultConfiguration();
        Rectangle whole = where.getBounds();
        Insets taken = Toolkit.getDefaultToolkit().getScreenInsets(where);
        return new Rectangle(
                whole.x + taken.left, whole.y + taken.top,
                whole.width - taken.left - taken.right,
                whole.height - taken.top - taken.bottom);
    }

    private PairValue measureTheDotsPerInch() {
        int resolution = Toolkit.getDefaultToolkit().getScreenResolution();
        return PairValue.of(resolution, resolution);
    }

    private Insets furnitureOfAResizableWindow() {
        return measureTheFurnitureOfAWindow(true);
    }

    private Insets measureTheFurnitureOfAFixedWindow() {
        return measureTheFurnitureOfAWindow(false);
    }

    private Insets measureTheFurnitureOfAWindow(
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

    private PairValue measureTheSmallestWindowSwingLaysOut() {
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
    public TextMeasure textMeasure() {
        if (!present) {
            return ScreenPort.none().textMeasure();
        }
        return TextMeasures.ofTheCarriedFont();
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
        onTheToolkitThreadAndWaitedFor(() -> windows.put(gob, showAWindow(gob)));
    }

    @Override
    protected void repaintWindowFor(GobValue gob) {
        findTheWindowFor(gob).ifPresent(standing -> onTheToolkitThreadAndWaitedFor(standing::repaint));
    }

    @Override
    protected void closeWindowFor(GobValue gob) {
        findTheWindowFor(gob).ifPresent(standing -> {
            windows.entrySet().removeIf(each -> each.getValue() == standing);
            onTheToolkitThreadAndWaitedFor(standing::dispose);
        });
    }

    private Optional<JFrame> findTheWindowFor(GobValue gob) {
        return windows.entrySet().stream()
                .filter(each -> each.getKey().sharesStorageWith(gob))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private JFrame showAWindow(GobValue gob) {
        JFrame frame = new JFrame(readTheTitleOf(gob));
        showJebolIcon(frame);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(paintASurface(gob));
        frame.pack();
        frame.setLocationRelativeTo(null);
        attachListenersThatOnlyQueue(frame, gob);
        frame.setVisible(true);
        return frame;
    }

    private static String readTheTitleOf(GobValue gob) {
        Value text = gob.storage().readTheContentIfKind(
                org.jebol.domain.value.GobStorage.Content.STRING);
        return text instanceof AnyStringValue title && !title.text().isEmpty()
                ? title.text()
                : "REBOL: untitled";
    }

    private JPanel paintASurface(GobValue gob) {
        org.jebol.domain.value.ObjectValue reading = drawDialect;
        return paintASurface(gob, reading);
    }

    private JPanel paintASurface(
            GobValue gob, org.jebol.domain.value.ObjectValue drawDialect) {
        DesktopScreen onThisScreen = this;
        JPanel surface = new JPanel() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics onto) {
                super.paintComponent(onto);
                Java2DSurface.paintTheContentsOfLeavingItsTitleToTheTitleBar(
                        (Graphics2D) onto, gob, drawDialect, onThisScreen.textMeasure());
            }
        };
        surface.setPreferredSize(new Dimension(
                (int) Math.round(gob.storage().size().x()),
                (int) Math.round(gob.storage().size().y())));
        return surface;
    }

    private void attachListenersThatOnlyQueue(JFrame frame, GobValue gob) {
        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent shutting) {
                post(ScreenEventType.CLOSE, gob);
            }
        });
        frame.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent changed) {
                Dimension drawable = frame.getContentPane().getSize();
                post(ScreenEventType.RESIZE, gob,
                        new ScreenEventDetail.At(drawable.width, drawable.height));
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
                queueTheKey(down, ScreenEventType.KEY, ScreenEventType.CONTROL, gob);
            }

            @Override
            public void keyReleased(KeyEvent up) {
                queueTheKey(up, ScreenEventType.KEY_UP, ScreenEventType.CONTROL_UP, gob);
            }
        });
        frame.getContentPane().addMouseListener(new MouseListener() {

            @Override
            public void mousePressed(MouseEvent down) {
                post(ScreenEventType.DOWN, gob, locateThePointer(down));
            }

            @Override
            public void mouseReleased(MouseEvent up) {
                post(ScreenEventType.UP, gob, locateThePointer(up));
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
                post(ScreenEventType.MOVE, gob, locateThePointer(moved));
            }

            @Override
            public void mouseDragged(MouseEvent dragged) {
                post(ScreenEventType.MOVE, gob, locateThePointer(dragged));
            }
        });
    }

    private ScreenEventDetail locateThePointer(MouseEvent pointer) {
        return new ScreenEventDetail.At(pointer.getX(), pointer.getY());
    }

    private void queueTheKey(KeyEvent pressed, ScreenEventType typing, ScreenEventType naming,
                             GobValue window) {

        Optional<String> named = Optional.ofNullable(KEYS_THAT_TYPE_NOTHING.get(pressed.getKeyCode()));
        if (named.isPresent()) {
            post(naming, window, new ScreenEventDetail.NamedKey(named.get()));
            return;
        }
        if (pressed.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            post(typing, window, new ScreenEventDetail.Typed(pressed.getKeyChar()));
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

    private static void onTheToolkitThreadAndWaitedFor(Runnable work) {
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
