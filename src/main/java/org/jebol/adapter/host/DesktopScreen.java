package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * The operator's own screen, through Swing and Java2D.
 *
 * <p>Refuses SHOW when there is no display, and no more than SHOW: measurements
 * answer zero and taking the root gob is accepted, because the view system comes
 * up while the library is still loading and a build server has no screen.
 *
 * <p>Decision 4 in {@code docs/decisions.md} governs the threading here.
 */
public final class DesktopScreen extends GobScreen {

    private static final String THE_PROPERTY_MACOS_NAMES_THE_APPLICATION_BY = "apple.awt.application.name";
    private static final List<Integer> ICON_SIZES = List.of(16, 32, 64, 128, 256, 512);

    private final JebolsIcon icon = new JebolsIcon();
    private final boolean present;
    private final Map<GobValue, JFrame> windows = new IdentityHashMap<>();

    private DesktopScreen(boolean present) {
        this.present = present;
    }

    public static DesktopScreen onThisMachine() {
        System.setProperty(THE_PROPERTY_MACOS_NAMES_THE_APPLICATION_BY, JebolsIcon.THE_APPLICATIONS_NAME);
        return new DesktopScreen(!GraphicsEnvironment.isHeadless());
    }

    private void showJebolsIconOn(JFrame frame) {
        List<Image> everySize = ICON_SIZES.stream().<Image>map(icon::drawnAt).toList();
        frame.setIconImages(everySize);
        if (Taskbar.isTaskbarSupported()
                && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(everySize.getLast());
        }
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
        return furnitureOfAWindowTheJdkWillNotStateSoItIsMeasured(true);
    }

    private Insets furnitureOfAFixedWindow() {
        return furnitureOfAWindowTheJdkWillNotStateSoItIsMeasured(false);
    }

    private Insets furnitureOfAWindowTheJdkWillNotStateSoItIsMeasured(
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
    protected Denied nothingToShowOn() {
        return new Denied("no-service", "this machine has no display to put a window on");
    }

    @Override
    protected List<GobValue> gobsWithWindows() {
        return List.copyOf(windows.keySet());
    }

    @Override
    protected void openTheWindowFor(GobValue gob) {
        onTheToolkitThreadAndWaitedFor(() -> windows.put(gob, aWindowShowing(gob)));
    }

    @Override
    protected void repaintTheWindowFor(GobValue gob) {
        windowFor(gob).ifPresent(standing -> onTheToolkitThreadAndWaitedFor(standing::repaint));
    }

    @Override
    protected void closeTheWindowFor(GobValue gob) {
        windowFor(gob).ifPresent(standing -> {
            windows.entrySet().removeIf(each -> each.getValue() == standing);
            onTheToolkitThreadAndWaitedFor(standing::dispose);
        });
    }

    private Optional<JFrame> windowFor(GobValue gob) {
        return windows.entrySet().stream()
                .filter(each -> each.getKey().sharesStorageWith(gob))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private JFrame aWindowShowing(GobValue gob) {
        JFrame frame = new JFrame(titleOf(gob));
        showJebolsIconOn(frame);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(aSurfacePainting(gob));
        frame.pack();
        frame.setLocationRelativeTo(null);
        everyListenerOnlyQueuesAndReturns(frame, gob);
        frame.setVisible(true);
        return frame;
    }

    private static String titleOf(GobValue gob) {
        Value text = gob.storage().contentIfKind(
                org.jebol.domain.value.GobStorage.Content.STRING);
        return text instanceof AnyStringValue title && !title.text().isEmpty()
                ? title.text()
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
                DesktopPainting.paintTheContentsOfLeavingItsTitleToTheTitleBar(
                        (Graphics2D) onto, gob, drawDialect);
            }
        };
        surface.setPreferredSize(new Dimension(
                (int) Math.round(gob.storage().size().x()),
                (int) Math.round(gob.storage().size().y())));
        return surface;
    }

    private void everyListenerOnlyQueuesAndReturns(JFrame frame, GobValue gob) {
        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent shutting) {
                queue(ScreenEventKind.CLOSE, gob);
            }
        });
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {

            @Override
            public void componentResized(java.awt.event.ComponentEvent changed) {
                Dimension drawable = frame.getContentPane().getSize();
                queue(ScreenEventKind.RESIZE, gob,
                        new ScreenEventDetail.At(drawable.width, drawable.height));
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent changed) {
                queue(ScreenEventKind.OFFSET, gob,
                        new ScreenEventDetail.At(frame.getX(), frame.getY()));
            }
        });
        frame.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent typed) {
            }

            @Override
            public void keyPressed(KeyEvent down) {
                queueTheKey(down, ScreenEventKind.KEY, ScreenEventKind.CONTROL, gob);
            }

            @Override
            public void keyReleased(KeyEvent up) {
                queueTheKey(up, ScreenEventKind.KEY_UP, ScreenEventKind.CONTROL_UP, gob);
            }
        });
        frame.getContentPane().addMouseListener(new MouseListener() {

            @Override
            public void mousePressed(MouseEvent down) {
                queue(ScreenEventKind.DOWN, gob, whereThePointerIs(down));
            }

            @Override
            public void mouseReleased(MouseEvent up) {
                queue(ScreenEventKind.UP, gob, whereThePointerIs(up));
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
                queue(ScreenEventKind.MOVE, gob, whereThePointerIs(moved));
            }

            @Override
            public void mouseDragged(MouseEvent dragged) {
                queue(ScreenEventKind.MOVE, gob, whereThePointerIs(dragged));
            }
        });
    }

    private ScreenEventDetail whereThePointerIs(MouseEvent pointer) {
        return new ScreenEventDetail.At(pointer.getX(), pointer.getY());
    }

    private void queueTheKey(KeyEvent pressed, ScreenEventKind typing, ScreenEventKind naming,
            GobValue window) {

        Optional<String> named = Optional.ofNullable(KEYS_THAT_TYPE_NOTHING.get(pressed.getKeyCode()));
        if (named.isPresent()) {
            queue(naming, window, new ScreenEventDetail.NamedKey(named.get()));
            return;
        }
        if (pressed.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            queue(typing, window, new ScreenEventDetail.Typed(pressed.getKeyChar()));
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

    private void queue(ScreenEventKind kind, GobValue window) {
        queued.add(kind, window);
    }

    private void queue(ScreenEventKind kind, GobValue window, ScreenEventDetail detail) {
        queued.add(kind, window, detail);
    }

    private static void onTheToolkitThreadAndWaitedFor(Runnable work) {
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
