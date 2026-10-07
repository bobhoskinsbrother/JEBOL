package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class TheRootGobTakesThePageEndToEndTest {

    private static final int PAGE_WIDE = 600;
    private static final int PAGE_HIGH = 400;

    private static final int WINDOW_WIDE = 200;
    private static final int WINDOW_HIGH = 100;
    private static final int JUST = 2;

    private static final Color THE_SCREEN = new Color(0, 0, 0);
    private static final Color THE_WINDOW = new Color(232, 93, 74);

    private static final String A_CENTRED_WINDOW = """
            view/no-wait/options make gob! [size: 200x100 color: 232.93.74] [offset: 'center]
            """;

    private WebScreenServer serving;
    private BrowserScreen screen;
    private ChromeDriver browser;

    private final class ThePage {

        private BufferedImage whatTheCanvasShows() throws IOException {
            return ImageIO.read(new ByteArrayInputStream(
                    browser.findElement(By.id("screen")).getScreenshotAs(OutputType.BYTES)));
        }

        private Color colourAt(int across, int down) throws IOException {
            return new Color(whatTheCanvasShows().getRGB(across, down));
        }

        private int wide() {
            return ((Number) browser.executeScript("return window.innerWidth")).intValue();
        }

        private int high() {
            return ((Number) browser.executeScript("return window.innerHeight")).intValue();
        }

        private String sizeAsRebolWritesIt() {
            return wide() + "x" + high();
        }

        private int windowLeft() {
            return (wide() - WINDOW_WIDE) / 2;
        }

        private int windowTop() {
            return (high() - WINDOW_HIGH) / 2;
        }

        private int windowRight() {
            return windowLeft() + WINDOW_WIDE - 1;
        }

        private int windowBottom() {
            return windowTop() + WINDOW_HIGH - 1;
        }
    }

    private final ThePage thePage = new ThePage();

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

    private Interpreter aScriptOnThisPage(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
        return interpreter;
    }

    @Test
    @DisplayName("the root gob is the page's size, so a centred window lands in the middle of the page")
    void aCentredWindowLandsInTheMiddle() throws IOException {
        Interpreter interpreter = aScriptOnThisPage("system/view/screen-gob/color: 0.0.0\n" + A_CENTRED_WINDOW);

        assertThat(interpreter.display(interpreter.run("system/view/screen-gob/size")))
                .as("the size the browser itself reports for the page")
                .isEqualTo(thePage.sizeAsRebolWritesIt());
        assertThat(thePage.whatTheCanvasShows().getWidth()).isEqualTo(thePage.wide());
        assertThat(thePage.whatTheCanvasShows().getHeight()).isEqualTo(thePage.high());

        assertThat(thePage.colourAt(thePage.wide() / 2, thePage.high() / 2))
                .as("the middle of the page").isEqualTo(THE_WINDOW);
        assertThat(thePage.colourAt(thePage.windowLeft() + JUST, thePage.windowTop() + JUST))
                .as("just inside the window's top left").isEqualTo(THE_WINDOW);
        assertThat(thePage.colourAt(thePage.windowRight() - JUST, thePage.windowBottom() - JUST))
                .as("just inside its bottom right").isEqualTo(THE_WINDOW);
        assertThat(thePage.colourAt(thePage.windowLeft() - JUST, thePage.windowTop() - JUST))
                .as("just outside its top left").isEqualTo(THE_SCREEN);
        assertThat(thePage.colourAt(thePage.windowRight() + JUST, thePage.windowBottom() + JUST))
                .as("just outside its bottom right").isEqualTo(THE_SCREEN);
        assertThat(thePage.colourAt(JUST, JUST))
                .as("the page's corner, which is the root gob").isEqualTo(THE_SCREEN);
    }

    @Test
    @DisplayName("a script cannot take the page over afterwards, because init-top-window is spent")
    void aScriptCannotTakeThePageOver() throws IOException {
        Interpreter interpreter = aScriptOnThisPage("""
                system/view/screen-gob/color: 0.0.0
                spent: init-top-window make gob! [size: 42x42]
                """ + A_CENTRED_WINDOW);

        assertThat(interpreter.display(interpreter.run("spent"))).isEqualTo("done");
        assertThat(interpreter.display(interpreter.run("system/view/screen-gob/size")))
                .isEqualTo(thePage.sizeAsRebolWritesIt());
        assertThat(thePage.colourAt(thePage.wide() / 2, thePage.high() / 2))
                .as("still centred on the page, not on a forty-two pixel root")
                .isEqualTo(THE_WINDOW);
    }
}
