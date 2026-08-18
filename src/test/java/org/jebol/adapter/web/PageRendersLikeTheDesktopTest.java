package org.jebol.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.jebol.adapter.host.DesktopPainting;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * The page and the window, painting the same list, compared pixel by pixel.
 *
 * <p>This is where "local and web render the same" stops being something
 * anybody says. Both renderers are handed one paint list, so the only
 * difference either of them can introduce is in how it draws a stated
 * rectangle at a stated place -- and this looks at whether it did.
 *
 * <p>Not in {@code check}, and that is a second gate rather than a skipped
 * test. It drives a real Chrome and fetches a driver the first time, so it
 * needs a browser and a network that the ordinary gate should not need. It
 * runs everything it holds every time it runs:
 * {@code ./gradlew browserCheck}.
 *
 * <p>Colours only, no writing. Two rasterisers never agree about glyph shapes
 * and no amount of shared input fixes that, so text is a thing to compare with
 * a tolerance somewhere else rather than a thing to assert here. Geometry and
 * colour need no tolerance, which is exactly why they are what is asserted.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
@Tag("browser")
class PageRendersLikeTheDesktopTest {

    /** A page big enough to hold the picture and small enough to be quick. */
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

    /**
     * A gob tree the two renderers should agree about, exactly.
     *
     * <p>Nested, offset and clipped, because those are the three things a
     * renderer that walked the tree itself would get subtly wrong. The last
     * child is deliberately bigger than its parent so that clipping is under
     * test rather than assumed.
     *
     * <p>The screen gob is given a colour on the first line, which is not
     * decoration. Without it the surface is only painted where the picture is,
     * and what shows through everywhere else is each renderer's own idea of a
     * background -- a page's body colour against whatever a window was cleared
     * to. Colouring the screen means every pixel compared came from the paint
     * list, which is the only thing this is entitled to claim.
     */
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
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        interpreter.defineFreshWordsIn(A_PICTURE_WORTH_COMPARING);
        GobValue root = (GobValue) interpreter.run(A_PICTURE_WORTH_COMPARING).value();

        BufferedImage fromTheBrowser = whatTheCanvasShows();
        BufferedImage fromJava2D = whatJava2DPaints(root,
                fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

        // Two guards before the comparison, because a comparison of two blank
        // surfaces passes and proves nothing. The picture has six colours in
        // it and the page is the size it was asked to be.
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
        // The child of `inner` is four hundred pixels square inside a parent
        // ninety high. A renderer that worked the clip out for itself would
        // paint over half the picture; both are told the answer instead.
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
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

    /** The canvas as pixels, taken from the browser. */
    private BufferedImage whatTheCanvasShows() throws IOException {
        WebElement canvas = browser.findElement(By.id("screen"));
        return ImageIO.read(new ByteArrayInputStream(
                canvas.getScreenshotAs(OutputType.BYTES)));
    }

    /** The same paint list, drawn by the renderer a desktop window uses. */
    private static BufferedImage whatJava2DPaints(GobValue root, int wide, int high) {
        BufferedImage surface =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        Graphics2D onto = surface.createGraphics();
        try {
            onto.setColor(Color.WHITE);
            onto.fillRect(0, 0, wide, high);
            DesktopPainting.execute(onto, PaintList.ofTheScreen(root, wide, high));
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private static Color colourAt(BufferedImage surface, int across, int down) {
        return new Color(surface.getRGB(across, down));
    }

    /** How many distinct colours a surface holds, as a sign it holds anything. */
    private static int howManyColoursAreIn(BufferedImage surface) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int down = 0; down < surface.getHeight(); down++) {
            for (int across = 0; across < surface.getWidth(); across++) {
                seen.add(surface.getRGB(across, down) & 0xFFFFFF);
            }
        }
        return seen.size();
    }

    /**
     * How many pixels the two disagree about.
     *
     * <p>Exactly, with no tolerance, because there is nothing here for a
     * tolerance to cover: every instruction is an axis-aligned rectangle of
     * one colour, which neither renderer anti-aliases. A tolerance would hide
     * the very drift this exists to catch.
     */
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
