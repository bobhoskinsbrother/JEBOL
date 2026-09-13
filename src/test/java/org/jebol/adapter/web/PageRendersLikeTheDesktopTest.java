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
        assertTheyAgree(fromTheBrowser, fromJava2D);
    }

    private static void assertTheyAgree(BufferedImage one, BufferedImage other) {
        Differences found = differences(one, other);
        assertThat(found.insideAShape())
                .as("one paint list, two renderers, and nothing between them that "
                        + "either of them decides; a pixel away from any edge that "
                        + "differs at all is one of them executing the instruction "
                        + "differently; %d by %d compared",
                        one.getWidth(), one.getHeight())
                .isZero();
        assertThat(found.edgeShare())
                .as("an edge pixel may differ, because coverage is a judgement "
                        + "two rasterisers make separately, but only %s of the "
                        + "picture may be edge that needs the allowance",
                        MOST_EDGE_PIXELS_THAT_MAY_DIFFER)
                .isLessThanOrEqualTo(MOST_EDGE_PIXELS_THAT_MAY_DIFFER);
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
        Interpreter interpreter = anInterpreterOnThisPage();
        interpreter.defineFreshWordsIn(A_DRAWING_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(A_DRAWING_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = whatTheCanvasShows();
        BufferedImage fromJava2D = whatJava2DPaints(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        assertThat(howManyColoursAreIn(fromTheBrowser))
                .as("a blank canvas would match a blank surface and mean nothing")
                .isGreaterThanOrEqualTo(5);
        assertTheyAgree(fromTheBrowser, fromJava2D);
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

    private static final int THE_CHANNEL_ALLOWANCE = 24;

    private static final double MOST_EDGE_PIXELS_THAT_MAY_DIFFER = 0.025;

    private record Differences(int insideAShape, int onAnEdge, int compared) {

        double edgeShare() {
            return compared == 0 ? 0 : (double) onAnEdge / compared;
        }
    }

    private static Differences differences(BufferedImage one, BufferedImage other) {
        if (one.getWidth() != other.getWidth() || one.getHeight() != other.getHeight()) {
            int every = Math.max(one.getWidth() * one.getHeight(),
                    other.getWidth() * other.getHeight());
            return new Differences(every, every, every);
        }
        int insideAShape = 0;
        int onAnEdge = 0;
        for (int down = 0; down < one.getHeight(); down++) {
            for (int across = 0; across < one.getWidth(); across++) {
                if (theSameColour(one, other, across, down, 0)) {
                    continue;
                }
                if (!sitsOnAnEdge(one, across, down)
                        && !sitsOnAnEdge(other, across, down)) {
                    insideAShape++;
                } else if (!theSameColour(one, other, across, down,
                        THE_CHANNEL_ALLOWANCE)) {
                    onAnEdge++;
                }
            }
        }
        return new Differences(insideAShape, onAnEdge,
                one.getWidth() * one.getHeight());
    }

    private static boolean theSameColour(
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
