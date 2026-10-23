package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TheRootGobTakesThePageEndToEndTest {

    private static final int WINDOW_WIDE = 200;
    private static final int WINDOW_HIGH = 100;
    private static final int JUST = 2;

    private static final Color THE_SCREEN = new Color(0, 0, 0);
    private static final Color THE_WINDOW = new Color(232, 93, 74);

    private static final String A_CENTRED_WINDOW = """
            view/no-wait/options make gob! [size: 200x100 color: 232.93.74] [offset: 'center]
            """;

    private final PageBrowsers browsers;
    private WebScreenServer serving;
    private BrowserScreen screen;
    private AnOperatorAtThePage operator;
    private WebDriver browser;

    TheRootGobTakesThePageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private final class ThePage {

        private BufferedImage captureWhatTheCanvasShows() throws IOException {
            return ImageIO.read(new ByteArrayInputStream(
                    browser.findElement(By.id("screen")).getScreenshotAs(OutputType.BYTES)));
        }

        private Color readTheColourAt(int across, int down) throws IOException {
            return new Color(captureWhatTheCanvasShows().getRGB(across, down));
        }

        private int wide() {
            return ((Number) operator.runsInThePage("return window.innerWidth")).intValue();
        }

        private int high() {
            return ((Number) operator.runsInThePage("return window.innerHeight")).intValue();
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

    private Interpreter makeAScriptOnThisPage(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return interpreter;
    }

    @Test
    @DisplayName("the root gob is the page's size, so a centred window lands in the middle of the page")
    void landsACentredWindowInTheMiddle() throws IOException {
        Interpreter interpreter = makeAScriptOnThisPage("system/view/screen-gob/color: 0.0.0\n" + A_CENTRED_WINDOW);

        assertThat(interpreter.display(interpreter.run("system/view/screen-gob/size")))
                .as("the size the browser itself reports for the page")
                .isEqualTo(thePage.sizeAsRebolWritesIt());
        assertThat(thePage.captureWhatTheCanvasShows().getWidth()).isEqualTo(thePage.wide());
        assertThat(thePage.captureWhatTheCanvasShows().getHeight()).isEqualTo(thePage.high());

        assertThat(thePage.readTheColourAt(thePage.wide() / 2, thePage.high() / 2))
                .as("the middle of the page").isEqualTo(THE_WINDOW);
        assertThat(thePage.readTheColourAt(thePage.windowLeft() + JUST, thePage.windowTop() + JUST))
                .as("just inside the window's top left").isEqualTo(THE_WINDOW);
        assertThat(thePage.readTheColourAt(thePage.windowRight() - JUST, thePage.windowBottom() - JUST))
                .as("just inside its bottom right").isEqualTo(THE_WINDOW);
        assertThat(thePage.readTheColourAt(thePage.windowLeft() - JUST, thePage.windowTop() - JUST))
                .as("just outside its top left").isEqualTo(THE_SCREEN);
        assertThat(thePage.readTheColourAt(thePage.windowRight() + JUST, thePage.windowBottom() + JUST))
                .as("just outside its bottom right").isEqualTo(THE_SCREEN);
        assertThat(thePage.readTheColourAt(JUST, JUST))
                .as("the page's corner, which is the root gob").isEqualTo(THE_SCREEN);
    }

    @Test
    @DisplayName("a script cannot take the page over afterwards, because init-top-window is spent")
    void keepsThePageFromAScript() throws IOException {
        Interpreter interpreter = makeAScriptOnThisPage("""
                system/view/screen-gob/color: 0.0.0
                spent: init-top-window make gob! [size: 42x42]
                """ + A_CENTRED_WINDOW);

        assertThat(interpreter.display(interpreter.run("spent"))).isEqualTo("done");
        assertThat(interpreter.display(interpreter.run("system/view/screen-gob/size")))
                .isEqualTo(thePage.sizeAsRebolWritesIt());
        assertThat(thePage.readTheColourAt(thePage.wide() / 2, thePage.high() / 2))
                .as("still centred on the page, not on a forty-two pixel root")
                .isEqualTo(THE_WINDOW);
    }
}
