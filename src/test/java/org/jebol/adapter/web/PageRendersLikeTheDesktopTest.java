package org.jebol.adapter.web;

import org.jebol.adapter.host.DesktopPainting;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class PageRendersLikeTheDesktopTest {

    private static final int PAGE_WIDE = 600;
    private static final int PAGE_HIGH = 400;

    private WebScreenServer serving;
    private BrowserScreen screen;
    private ChromeDriver browser;

    @BeforeEach
    void openAPage() throws IOException {
        serving = WebScreenServer.on(0);
        screen = BrowserScreen.seenBy(serving);
        serving.reportTo(screen);

        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new",
                "--window-size=" + PAGE_WIDE + "," + PAGE_HIGH,
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--no-sandbox");
        browser = new ChromeDriver(asked);
        browser.get(serving.address());

        new WebDriverWait(browser, Duration.ofSeconds(20))
                .until(anything -> screen.hasADisplay());
    }

    @AfterEach
    void closeThePage() {
        if (browser != null) {
            browser.quit();
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
    void thePageMatchesTheWindow() throws Exception {
        Interpreter interpreter = anInterpreterOnThisPage();
        interpreter.defineFreshWordsIn(A_PICTURE_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(A_PICTURE_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = whatTheCanvasShows();
        BufferedImage fromJava2D = whatJava2DPaints(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(fromTheBrowser.getWidth()).isEqualTo(fromJava2D.getWidth());
        assertThat(fromTheBrowser.getHeight()).isEqualTo(fromJava2D.getHeight());
        assertThat(howManyColoursAreIn(fromTheBrowser))
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(6);
        assertThat(howManyPixelsDiffer(fromTheBrowser, fromJava2D))
                .as("one paint list, two renderers, and nothing between them "
                        + "that either of them decides; %d by %d pixels compared",
                        fromTheBrowser.getWidth(), fromTheBrowser.getHeight())
                .isZero();
    }

    @Test
    @DisplayName("and a gob outside its parent is clipped away in both")
    void bothClipTheSameWay() throws Exception {
        Interpreter interpreter = anInterpreterOnThisPage();
        interpreter.defineFreshWordsIn(A_PICTURE_WORTH_COMPARING);
        interpreter.run(A_PICTURE_WORTH_COMPARING);

        BufferedImage shown = whatTheCanvasShows();

        assertThat(colourAt(shown, 350, 120))
                .as("inside the clipped child, its own dark colour")
                .isEqualTo(new Color(20, 20, 20));
        assertThat(colourAt(shown, 350, 200))
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
                ]
            ]
            system/view/screen-gob
            """;

    @Test
    @DisplayName("and a DRAW block paints the same in both, shape for shape")
    void adrawBlockMatchesToo() throws Exception {
        Interpreter interpreter = anInterpreterOnThisPage();
        interpreter.defineFreshWordsIn(A_DRAWING_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(A_DRAWING_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = whatTheCanvasShows();
        BufferedImage fromJava2D = whatJava2DPaints(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(howManyColoursAreIn(fromTheBrowser))
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(5);
        assertThat(howManyPixelsDiffer(fromTheBrowser, fromJava2D))
                .as("the dialect was read once and executed twice, so the only "
                        + "way these can differ is in the drawing itself")
                .isZero();
    }

    private BufferedImage whatTheCanvasShows() throws IOException {
        WebElement canvas = browser.findElement(By.id("screen"));
        return ImageIO.read(new ByteArrayInputStream(
                canvas.getScreenshotAs(OutputType.BYTES)));
    }

    private BufferedImage whatJava2DPaints(GobValue root, int wide, int high) {
        BufferedImage surface =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        Graphics2D onto = surface.createGraphics();
        try {
            onto.setColor(Color.WHITE);
            onto.fillRect(0, 0, wide, high);
            DesktopPainting.execute(onto,
                    PaintList.ofTheScreen(root, wide, high, theDrawDialect));
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private org.jebol.domain.value.ObjectValue theDrawDialect;

    private Interpreter anInterpreterOnThisPage() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        theDrawDialect = (org.jebol.domain.value.ObjectValue)
                interpreter.run("system/dialects/draw").value();
        return interpreter;
    }

    private static Color colourAt(BufferedImage surface, int across, int down) {
        return new Color(surface.getRGB(across, down));
    }

    private static int howManyColoursAreIn(BufferedImage surface) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int down = 0; down < surface.getHeight(); down++) {
            for (int across = 0; across < surface.getWidth(); across++) {
                seen.add(surface.getRGB(across, down) & 0xFFFFFF);
            }
        }
        return seen.size();
    }

    private static int howManyPixelsDiffer(BufferedImage one, BufferedImage other) {
        if (one.getWidth() != other.getWidth() || one.getHeight() != other.getHeight()) {
            return Math.max(one.getWidth() * one.getHeight(),
                    other.getWidth() * other.getHeight());
        }
        int differing = 0;
        for (int down = 0; down < one.getHeight(); down++) {
            for (int across = 0; across < one.getWidth(); across++) {
                if ((one.getRGB(across, down) & 0xFFFFFF)
                        != (other.getRGB(across, down) & 0xFFFFFF)) {
                    differing++;
                }
            }
        }
        return differing;
    }
}
