package org.jebol.adapter.web;

import org.jebol.adapter.host.Java2DSurface;
import org.jebol.application.Bounds;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.TheCarriedFont;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(TheSuitesPageBrowsers.class)
class PageRendersLikeTheDesktopTest {

    private final PageBrowsers browsers;
    private WebScreenServer serving;
    private BrowserScreen screen;
    private AnOperatorAtThePage operator;
    private WebDriver browser;

    PageRendersLikeTheDesktopTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @BeforeEach
    void openAPage() throws IOException {
        serving = WebScreenServer.on(0, new ClasspathFiles());
        screen = BrowserScreen.showTo(serving, new ClasspathFiles());
        serving.reportTo(screen);

        operator = new AnOperatorAtThePage(browsers, "chrome", serving.address()).opensThePage();
        browser = operator.browser();

        new WebDriverWait(browser, Duration.ofSeconds(20))
                .until(anything -> screen.hasDisplay());
    }

    @AfterEach
    void closeThePage() {
        if (operator != null) {
            operator.close();
        }
        serving.close();
    }

    private static final String A_PICTURE_WORTH_COMPARING = """
            system/view/screen-gob/color: 0.0.0
            panel: make gob! [size: 420x260 color: 30.34.44]
            append panel make gob! [offset: 0x0    size: 420x44  color: 58.140.208]
            append panel make gob! [offset: 20x70  size: 120x90  color: 232.93.74]
            append panel make gob! [offset: 155x70 size: 120x90  color: 96.198.128]
            inner: make gob! [offset: 290x70 size: 110x90 color: 244.196.66]
            append inner make gob! [offset: 20x20 size: 400x400 color: 20.20.20]
            append panel inner
            view/no-wait panel
            system/view/screen-gob
            """;

    @Test
    @DisplayName("what the browser paints is what Java2D paints, pixel for pixel")
    void matchesTheWindow() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(A_PICTURE_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(A_PICTURE_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = captureWhatTheCanvasShows();
        BufferedImage fromJava2D = paintWithJava2D(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(fromTheBrowser.getWidth()).isEqualTo(fromJava2D.getWidth());
        assertThat(fromTheBrowser.getHeight()).isEqualTo(fromJava2D.getHeight());
        assertThat(countTheColoursIn(fromTheBrowser))
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(6);
        assertTheyAgree(fromTheBrowser, fromJava2D);
    }

    private static final int EXACTLY = 0;

    private static final int HOW_FAR_TWO_STACKED_BLENDS_MAY_ROUND_APART = 1;

    private static final Path WHERE_A_DISAGREEMENT_IS_KEPT = Path.of("build", "reports", "browser-check");

    private static void assertTheyAgree(BufferedImage one, BufferedImage other) throws IOException {
        assertTheyAgree("a picture", one, other, EXACTLY);
    }

    private static void assertTheyAgree(String scene, BufferedImage fromTheBrowser, BufferedImage fromJava2D,
            int insideAShapeAllowance) throws IOException {

        Differences found = differences(fromTheBrowser, fromJava2D, insideAShapeAllowance);
        if (found.insideAShape() > 0 || found.edgeShare() > MOST_EDGE_PIXELS_THAT_MAY_DIFFER) {
            keepBothPicturesOf(scene, fromTheBrowser, fromJava2D);
        }
        BufferedImage one = fromTheBrowser;
        assertThat(found.insideAShape())
                .as("one paint list, two renderers, and nothing between them that "
                        + "either of them decides; a pixel away from any edge that "
                        + "differs by more than %d is one of them executing the instruction "
                        + "differently; %d by %d compared; both pictures are in %s",
                        insideAShapeAllowance, one.getWidth(), one.getHeight(), WHERE_A_DISAGREEMENT_IS_KEPT)
                .isZero();
        assertThat(found.edgeShare())
                .as("an edge pixel may differ, because coverage is a judgement "
                        + "two rasterisers make separately, but only %s of the "
                        + "picture may be edge that needs the allowance",
                        MOST_EDGE_PIXELS_THAT_MAY_DIFFER)
                .isLessThanOrEqualTo(MOST_EDGE_PIXELS_THAT_MAY_DIFFER);
    }

    private static void keepBothPicturesOf(String scene, BufferedImage fromTheBrowser, BufferedImage fromJava2D)
            throws IOException {

        Files.createDirectories(WHERE_A_DISAGREEMENT_IS_KEPT);
        String named = scene.replaceAll("[^a-z0-9]+", "-");
        ImageIO.write(fromTheBrowser, "png", WHERE_A_DISAGREEMENT_IS_KEPT.resolve(named + "-browser.png").toFile());
        ImageIO.write(fromJava2D, "png", WHERE_A_DISAGREEMENT_IS_KEPT.resolve(named + "-java2d.png").toFile());
    }

    @Test
    @DisplayName("and a gob outside its parent is clipped away in both")
    void clipsTheSameWay() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(A_PICTURE_WORTH_COMPARING);
        interpreter.run(A_PICTURE_WORTH_COMPARING);

        BufferedImage shown = captureWhatTheCanvasShows();

        assertThat(readTheColourAt(shown, 350, 120))
                .as("inside the clipped child, its own dark colour")
                .isEqualTo(new Color(20, 20, 20));
        assertThat(readTheColourAt(shown, 350, 200))
                .as("below the parent's bottom edge, the panel and not the child")
                .isEqualTo(new Color(30, 34, 44));
    }

    private static final String A_DRAWING_WORTH_COMPARING = """
            system/view/screen-gob/color: 0.0.0
            view/no-wait make gob! [
                size: 420x260
                draw: [
                    fill-pen 30.34.44  pen off  box 0x0 420x260
                    fill-pen 58.140.208 box 0x0 420x44
                    fill-pen 232.93.74  box 20x70 140x160
                    fill-pen 96.198.128 box 155x70 275x160
                    push [
                        translate 20x190
                        fill-pen 70.76.92 box 0x0 380x40
                    ]
                    fill-pen 244.196.66 pen 20.20.20 line-width 3
                    circle 90x115 40
                    pen 255.255.255 line-width 5 fill-pen off
                    line 180x90 340x150
                    arc 250x115 40x40 200 140
                ]
            ]
            system/view/screen-gob
            """;

    @Test
    @DisplayName("and a DRAW block paints the same in both, shape for shape")
    void adrawBlockMatchesToo() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(A_DRAWING_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(A_DRAWING_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = captureWhatTheCanvasShows();
        BufferedImage fromJava2D = paintWithJava2D(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(countTheColoursIn(fromTheBrowser))
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(5);
        assertTheyAgree(fromTheBrowser, fromJava2D);
    }

    private static final String STROKED_CURVES_WORTH_COMPARING = """
            system/view/screen-gob/color: 0.0.0
            view/no-wait make gob! [
                size: 420x260
                draw: [
                    pen off fill-pen 30.34.44 box 0x0 420x260
                    fill-pen off
                    pen 130.170.255 line-width 4 curve 20x120 90x10 160x200 220x40
                    pen 244.196.66 line-width 3 curve 230x120 310x10 400x120
                    pen 96.198.128 line-width 4 spline 20 20x240 80x160 150x250 210x170
                    pen 232.93.74 line-width 5 line 230x240 300x150 330x230 400x160
                    pen 255.255.255 line-width 2 arc 330x200 50x40 200 140
                    pen 232.93.74 line-width 4 line-pattern 96.198.128 14 8 line 20x140 400x145
                    pen 244.196.66 line-width 3 fill-pen 244.196.66 arrow 244.196.66 1x1 line 240x60 400x20
                ]
            ]
            system/view/screen-gob
            """;

    @Test
    @DisplayName("and stroked curves, a spline, a joined line, a dash and an arrow match inside every stroke and stay within the allowance at its edges")
    void strokedCurvesMatchWithinTheAllowance() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(STROKED_CURVES_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(STROKED_CURVES_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = captureWhatTheCanvasShows();
        BufferedImage fromJava2D = paintWithJava2D(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(countTheColoursIn(fromTheBrowser))
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(6);
        assertTheyAgree("stroked curves", fromTheBrowser, fromJava2D, EXACTLY);
    }

    private static final String SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS = """
            system/view/screen-gob/color: 0.0.0
            view/no-wait make gob! [
                size: 420x260
                draw: [
                    pen off
                    fill-pen 244.196.66 box 0x0 420x260
                    fill-pen 0.0.0.128 box 20x20 200x120 12
                    fill-pen 200.0.0.64 box 120x60 380x220 30
                    fill-pen 58.140.208 pen 20.20.20 line-width 2
                    box 240x30 400x110 8
                ]
            ]
            system/view/screen-gob
            """;

    @Test
    @DisplayName("and a see-through colour and a rounded box paint the same in both")
    void matchesOpacityAndRoundedCorners() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS);
        GobValue root = (GobValue) interpreter.run(SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS).value();

        BufferedImage fromTheBrowser = captureWhatTheCanvasShows();
        BufferedImage fromJava2D = paintWithJava2D(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        Color halved = readTheColourAt(fromTheBrowser, 60, 40);
        assertThat(halved.getRed()).as("half-transparent black over the yellow halves its red").isCloseTo(122, within(1));
        assertThat(halved.getGreen()).as("and its green").isCloseTo(98, within(1));
        assertThat(halved.getBlue()).as("and its blue").isCloseTo(33, within(1));
        assertThat(readTheColourAt(fromTheBrowser, 21, 21))
                .as("inside the box's square corner but outside its rounded one")
                .isEqualTo(new Color(244, 196, 66));
        assertTheyAgree("see-through colours and rounded corners", fromTheBrowser, fromJava2D,
                HOW_FAR_TWO_STACKED_BLENDS_MAY_ROUND_APART);
    }

    private static final String A_CENTRED_LABEL = """
            system/view/screen-gob/color: 255.255.255
            labelled: make gob! [size: 300x200]
            labelled/text: compose [
                font (make object! [size: 20 color: 0.0.0])
                para (make object! [origin: 0x0 margin: 0x0 align: 'center valign: 'middle])
                "MMMMMM"
            ]
            view/no-wait labelled
            system/view/screen-gob/1/offset: 20x10
            show system/view/screen-gob
            """;

    @Test
    @DisplayName("and a centred label's ink is in the middle of its gob in the browser too")
    void centresACentredLabel() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(A_CENTRED_LABEL);
        interpreter.run(A_CENTRED_LABEL);

        BufferedImage shown = captureWhatTheCanvasShows();
        int left = Integer.MAX_VALUE;
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int down = 0; down < shown.getHeight(); down++) {
            for (int across = 0; across < shown.getWidth(); across++) {
                Color seen = readTheColourAt(shown, across, down);
                if (seen.getRed() + seen.getGreen() + seen.getBlue() < 3 * 128) {
                    left = Math.min(left, across);
                    right = Math.max(right, across);
                    top = Math.min(top, down);
                    bottom = Math.max(bottom, down);
                }
            }
        }

        assertThat(right).as("some ink was written").isGreaterThanOrEqualTo(0);
        assertThat((left + right) / 2.0).as("across, the gob's middle being 20 + 150")
                .isCloseTo(170.0, within(2.0));
        assertThat((top + bottom) / 2.0).as("down, the gob's middle being 10 + 100")
                .isCloseTo(110.0, within(4.0));
    }

    private static final String A_LABEL_TOO_LONG_FOR_ITS_GOB = """
            system/view/screen-gob/color: 255.255.255
            labelled: make gob! [size: 300x200]
            labelled/text: compose [
                font (make object! [size: 20 color: 0.0.0])
                para (make object! [origin: 0x0 margin: 0x0 wrap?: true])
                "MMMM MMMM MMMM MMMM MMMM MMMM MMMM MMMM"
            ]
            view/no-wait labelled
            system/view/screen-gob/1/offset: 20x10
            show system/view/screen-gob
            """;

    @Test
    @DisplayName("and a label too long for its gob wraps inside it in the browser too, onto more than one line")
    void wrapsALongLabel() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(A_LABEL_TOO_LONG_FOR_ITS_GOB);
        interpreter.run(A_LABEL_TOO_LONG_FOR_ITS_GOB);

        BufferedImage shown = captureWhatTheCanvasShows();
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int down = 0; down < shown.getHeight(); down++) {
            for (int across = 0; across < shown.getWidth(); across++) {
                Color seen = readTheColourAt(shown, across, down);
                if (seen.getRed() + seen.getGreen() + seen.getBlue() < 3 * 128) {
                    right = Math.max(right, across);
                    top = Math.min(top, down);
                    bottom = Math.max(bottom, down);
                }
            }
        }

        assertThat(right).as("the ink stays inside the gob, whose right edge is 20 + 300").isBetween(0, 320);
        assertThat(bottom - top).as("the ink runs over more than one 20-point line").isGreaterThan(30);
    }

    private static final String A_WHITE_LABEL_WITH_A_SHADOW = """
            system/view/screen-gob/color: 255.255.255
            labelled: make gob! [size: 420x260]
            labelled/text: compose [
                font (make object! [size: 20 color: 255.255.255 shadow: 2x2])
                "MMMMMM"
            ]
            view/no-wait labelled
            system/view/screen-gob
            """;

    @Test
    @DisplayName("and a white label's shadow is the only ink on white in the browser too")
    void writesAShadow() throws Exception {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(A_WHITE_LABEL_WITH_A_SHADOW);
        interpreter.run(A_WHITE_LABEL_WITH_A_SHADOW);

        BufferedImage shown = captureWhatTheCanvasShows();
        int inked = 0;
        for (int down = 0; down < 60; down++) {
            for (int across = 0; across < 200; across++) {
                Color seen = readTheColourAt(shown, across, down);
                if (seen.getRed() + seen.getGreen() + seen.getBlue() < 3 * 128) {
                    inked++;
                }
            }
        }

        assertThat(inked).isGreaterThan(50);
    }

    private BufferedImage captureWhatTheCanvasShows() throws IOException {
        WebElement canvas = browser.findElement(By.id("screen"));
        return ImageIO.read(new ByteArrayInputStream(
                canvas.getScreenshotAs(OutputType.BYTES)));
    }

    private BufferedImage paintWithJava2D(GobValue root, int wide, int high) {
        BufferedImage surface =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        Graphics2D onto = surface.createGraphics();
        try {
            onto.setColor(Color.WHITE);
            onto.fillRect(0, 0, wide, high);
            Java2DSurface.execute(onto,
                    PaintList.ofScreen(root, wide, high, theDrawDialect),
                    TheCarriedFont.measurement(), TheCarriedFont.face());
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private ObjectValue theDrawDialect;

    private Interpreter makeAnInterpreterOnThisPage() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
        theDrawDialect = (ObjectValue)
                interpreter.run("system/dialects/draw").value();
        return interpreter;
    }

    private static Color readTheColourAt(BufferedImage surface, int across, int down) {
        return new Color(surface.getRGB(across, down));
    }

    private static int countTheColoursIn(BufferedImage surface) {
        Set<Integer> seen = new HashSet<>();
        for (int down = 0; down < surface.getHeight(); down++) {
            for (int across = 0; across < surface.getWidth(); across++) {
                seen.add(surface.getRGB(across, down) & 0xFFFFFF);
            }
        }
        return seen.size();
    }

    private static final int THE_CHANNEL_ALLOWANCE = 24;

    private static final double MOST_EDGE_PIXELS_THAT_MAY_DIFFER = 0.025;

    private record Differences(int insideAShape, int onAnEdge, int compared) {

        double edgeShare() {
            return compared == 0 ? 0 : (double) onAnEdge / compared;
        }
    }

    private static Differences differences(BufferedImage one, BufferedImage other, int insideAShapeAllowance) {
        if (one.getWidth() != other.getWidth() || one.getHeight() != other.getHeight()) {
            int every = Math.max(one.getWidth() * one.getHeight(),
                    other.getWidth() * other.getHeight());
            return new Differences(every, every, every);
        }
        int insideAShape = 0;
        int onAnEdge = 0;
        for (int down = 0; down < one.getHeight(); down++) {
            for (int across = 0; across < one.getWidth(); across++) {
                if (isTheSameColour(one, other, across, down, insideAShapeAllowance)) {
                    continue;
                }
                if (!sitsOnAnEdge(one, across, down)
                        && !sitsOnAnEdge(other, across, down)) {
                    insideAShape++;
                } else if (!isTheSameColour(one, other, across, down,
                        THE_CHANNEL_ALLOWANCE)) {
                    onAnEdge++;
                }
            }
        }
        return new Differences(insideAShape, onAnEdge,
                one.getWidth() * one.getHeight());
    }

    private static boolean isTheSameColour(
            BufferedImage one, BufferedImage other, int across, int down, int allowance) {

        int here = one.getRGB(across, down);
        int there = other.getRGB(across, down);
        for (int shift : new int[] {16, 8, 0}) {
            int mine = (here >> shift) & 0xFF;
            int yours = (there >> shift) & 0xFF;
            if (Math.abs(mine - yours) > allowance) {
                return false;
            }
        }
        return true;
    }

    private static boolean sitsOnAnEdge(BufferedImage surface, int across, int down) {
        int middle = surface.getRGB(across, down) & 0xFFFFFF;
        for (int downBy = -1; downBy <= 1; downBy++) {
            for (int acrossBy = -1; acrossBy <= 1; acrossBy++) {
                int neighbourAcross = across + acrossBy;
                int neighbourDown = down + downBy;
                if (neighbourAcross < 0 || neighbourDown < 0
                        || neighbourAcross >= surface.getWidth()
                        || neighbourDown >= surface.getHeight()) {
                    continue;
                }
                if ((surface.getRGB(neighbourAcross, neighbourDown) & 0xFFFFFF)
                        != middle) {
                    return true;
                }
            }
        }
        return false;
    }
}
