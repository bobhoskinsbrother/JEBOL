package org.jebol.adapter.web;

import org.jebol.adapter.host.Java2DSurface;
import org.jebol.application.Bounds;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.OneSurfaceScreen;
import org.jebol.application.TheCarriedFace;
import org.jebol.application.TheCarriedFont;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.render.ArgbPicture;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.ParityScenes;
import org.jebol.domain.render.PixelAgreement;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(TheSuitesPageBrowsers.class)
class PageRendersLikeTheDesktopTest {

    private static final Path WHERE_A_DISAGREEMENT_IS_KEPT = Path.of("build", "reports", "browser-check");

    private final PageBrowsers browsers;
    private WebScreenServer serving;
    private OneSurfaceScreen screen;
    private AnOperatorAtThePage operator;
    private WebDriver browser;
    private GobValue theRoot;
    private ObjectValue theDrawDialect;

    PageRendersLikeTheDesktopTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @BeforeEach
    void openAPage() throws IOException {
        serving = WebScreenServer.on(0, new ClasspathFiles());
        screen = PageScreen.showTo(serving, new ClasspathFiles());
        serving.reportTo(screen);

        operator = new AnOperatorAtThePage(browsers, "chrome", serving.address()).opensThePage();
        browser = operator.browser();

        new PageWait(browser, Duration.ofSeconds(20))
                .until(anything -> screen.hasDisplay());
    }

    @AfterEach
    void closeThePage() {
        if (operator != null) {
            operator.close();
        }
        serving.close();
    }

    @Test
    @DisplayName("what the browser paints is what Java2D paints, pixel for pixel")
    void matchesTheWindow() throws Exception {
        BufferedImage fromTheBrowser = paintInTheBrowser(ParityScenes.GOB_PANELS.rebol());
        BufferedImage fromJava2D = paintWithJava2D(theRoot, fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(fromTheBrowser.getWidth()).isEqualTo(fromJava2D.getWidth());
        assertThat(fromTheBrowser.getHeight()).isEqualTo(fromJava2D.getHeight());
        assertThat(asPicture(fromTheBrowser).countColours())
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(ParityScenes.GOB_PANELS.fewestColours());
        assertTheyAgree(ParityScenes.GOB_PANELS, fromTheBrowser, fromJava2D);
    }

    @Test
    @DisplayName("and a gob outside its parent is clipped away in both")
    void clipsTheSameWay() throws Exception {
        BufferedImage shown = paintInTheBrowser(ParityScenes.GOB_PANELS.rebol());

        assertThat(readTheColourAt(shown, 350, 120))
                .as("inside the clipped child, its own dark colour")
                .isEqualTo(new Color(20, 20, 20));
        assertThat(readTheColourAt(shown, 350, 200))
                .as("below the parent's bottom edge, the panel and not the child")
                .isEqualTo(new Color(30, 34, 44));
    }

    @Test
    @DisplayName("and a DRAW block paints the same in both, shape for shape")
    void adrawBlockMatchesToo() throws Exception {
        BufferedImage fromTheBrowser = paintInTheBrowser(ParityScenes.DRAW_BLOCK.rebol());
        BufferedImage fromJava2D = paintWithJava2D(theRoot, fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(asPicture(fromTheBrowser).countColours())
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(ParityScenes.DRAW_BLOCK.fewestColours());
        assertTheyAgree(ParityScenes.DRAW_BLOCK, fromTheBrowser, fromJava2D);
    }

    @Test
    @DisplayName("and stroked curves, a spline, a joined line, a dash and an arrow match inside every stroke and stay within the allowance at its edges")
    void strokedCurvesMatchWithinTheAllowance() throws Exception {
        BufferedImage fromTheBrowser = paintInTheBrowser(ParityScenes.STROKED_CURVES.rebol());
        BufferedImage fromJava2D = paintWithJava2D(theRoot, fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(asPicture(fromTheBrowser).countColours())
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(ParityScenes.STROKED_CURVES.fewestColours());
        assertTheyAgree(ParityScenes.STROKED_CURVES, fromTheBrowser, fromJava2D);
    }

    @Test
    @DisplayName("and a see-through colour and a rounded box paint the same in both")
    void matchesOpacityAndRoundedCorners() throws Exception {
        BufferedImage fromTheBrowser = paintInTheBrowser(ParityScenes.SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS.rebol());
        BufferedImage fromJava2D = paintWithJava2D(theRoot, fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        Color halved = readTheColourAt(fromTheBrowser, 60, 40);
        assertThat(halved.getRed()).as("half-transparent black over the yellow halves its red").isCloseTo(122, within(1));
        assertThat(halved.getGreen()).as("and its green").isCloseTo(98, within(1));
        assertThat(halved.getBlue()).as("and its blue").isCloseTo(33, within(1));
        assertThat(readTheColourAt(fromTheBrowser, 21, 21))
                .as("inside the box's square corner but outside its rounded one")
                .isEqualTo(new Color(244, 196, 66));
        assertTheyAgree(ParityScenes.SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS, fromTheBrowser, fromJava2D);
    }

    @Test
    @DisplayName("and a centred label's ink is in the middle of its gob in the browser too")
    void centresACentredLabel() throws Exception {
        BufferedImage shown = paintInTheBrowser(ParityScenes.A_CENTRED_LABEL);
        int left = Integer.MAX_VALUE;
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int down = 0; down < shown.getHeight(); down++) {
            for (int across = 0; across < shown.getWidth(); across++) {
                if (isInk(readTheColourAt(shown, across, down))) {
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

    @Test
    @DisplayName("and a label too long for its gob wraps inside it in the browser too, onto more than one line")
    void wrapsALongLabel() throws Exception {
        BufferedImage shown = paintInTheBrowser(ParityScenes.A_LABEL_TOO_LONG_FOR_ITS_GOB);
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int down = 0; down < shown.getHeight(); down++) {
            for (int across = 0; across < shown.getWidth(); across++) {
                if (isInk(readTheColourAt(shown, across, down))) {
                    right = Math.max(right, across);
                    top = Math.min(top, down);
                    bottom = Math.max(bottom, down);
                }
            }
        }

        assertThat(right).as("the ink stays inside the gob, whose right edge is 20 + 300").isBetween(0, 320);
        assertThat(bottom - top).as("the ink runs over more than one 20-point line").isGreaterThan(30);
    }

    @Test
    @DisplayName("and a white label's shadow is the only ink on white in the browser too")
    void writesAShadow() throws Exception {
        BufferedImage shown = paintInTheBrowser(ParityScenes.A_WHITE_LABEL_WITH_A_SHADOW);
        int inked = 0;
        for (int down = 0; down < 60; down++) {
            for (int across = 0; across < 200; across++) {
                if (isInk(readTheColourAt(shown, across, down))) {
                    inked++;
                }
            }
        }

        assertThat(inked).isGreaterThan(50);
    }

    private BufferedImage paintInTheBrowser(String scene) throws IOException {
        Interpreter interpreter = makeAnInterpreterOnThisPage();
        interpreter.defineNewWordsIn(scene);
        theRoot = (GobValue) interpreter.run(scene).value();
        return captureWhatTheCanvasShows();
    }

    private static void assertTheyAgree(ParityScenes.Shapes scene, BufferedImage fromTheBrowser,
            BufferedImage fromJava2D) throws IOException {

        PixelAgreement found = PixelAgreement.between(
                asPicture(fromTheBrowser), asPicture(fromJava2D), scene.insideAShapeAllowance());
        if (!found.holds()) {
            keepBothPicturesOf(scene.name(), fromTheBrowser, fromJava2D);
        }
        assertThat(found.insideAShape())
                .as("one paint list, two renderers, and nothing between them that "
                        + "either of them decides; a pixel away from any edge that "
                        + "differs by more than %d is one of them executing the instruction "
                        + "differently; %d by %d compared; both pictures are in %s",
                        scene.insideAShapeAllowance(), fromTheBrowser.getWidth(), fromTheBrowser.getHeight(),
                        WHERE_A_DISAGREEMENT_IS_KEPT)
                .isZero();
        assertThat(found.edgeShare())
                .as("an edge pixel may differ, because coverage is a judgement "
                        + "two rasterisers make separately, but only %s of the "
                        + "picture may be edge that needs the allowance",
                        PixelAgreement.MOST_EDGE_PIXELS_THAT_MAY_DIFFER)
                .isLessThanOrEqualTo(PixelAgreement.MOST_EDGE_PIXELS_THAT_MAY_DIFFER);
    }

    private static void keepBothPicturesOf(String scene, BufferedImage fromTheBrowser, BufferedImage fromJava2D)
            throws IOException {

        Files.createDirectories(WHERE_A_DISAGREEMENT_IS_KEPT);
        ImageIO.write(fromTheBrowser, "png", WHERE_A_DISAGREEMENT_IS_KEPT.resolve(scene + "-browser.png").toFile());
        ImageIO.write(fromJava2D, "png", WHERE_A_DISAGREEMENT_IS_KEPT.resolve(scene + "-java2d.png").toFile());
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
                    TheCarriedFont.measurement(), TheCarriedFace.face());
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private Interpreter makeAnInterpreterOnThisPage() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
        theDrawDialect = (ObjectValue)
                interpreter.run("system/dialects/draw").value();
        return interpreter;
    }

    private static ArgbPicture asPicture(BufferedImage image) {
        int wide = image.getWidth();
        int high = image.getHeight();
        return ArgbPicture.opaque(wide, high, image.getRGB(0, 0, wide, high, null, 0, wide));
    }

    private static boolean isInk(Color seen) {
        return seen.getRed() + seen.getGreen() + seen.getBlue() < 3 * 128;
    }

    private static Color readTheColourAt(BufferedImage surface, int across, int down) {
        return new Color(surface.getRGB(across, down));
    }
}
