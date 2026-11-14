package org.jebol.adapter.web;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class ArrangingAWindowOnAPageEndToEndTest {

    private static final int JUST = 2;
    private static final Color THE_SCREEN = new Color(0, 0, 0);
    private static final Color THE_WINDOW = new Color(232, 93, 74);

    private static final String A_CENTRED_WINDOW = """
            system/view/screen-gob/color: 0.0.0
            win: view/no-wait/options make gob! [size: 200x100 color: 232.93.74] [offset: 'center]
            """;

    private final PageBrowsers browsers;
    private WebScreenServer serving;
    private BrowserScreen screen;
    private AnOperatorAtThePage operator;
    private WebDriver browser;
    private Interpreter interpreter;

    ArrangingAWindowOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @BeforeEach
    void openAPage() throws IOException {
        serving = WebScreenServer.on(0, new ClasspathFiles());
        screen = BrowserScreen.showTo(serving, new ClasspathFiles());
        serving.reportTo(screen);
        operator = new AnOperatorAtThePage(browsers, "chrome", serving.address()).opensThePage();
        browser = operator.browser();
        new PageWait(browser, Duration.ofSeconds(20)).until(anything -> screen.hasDisplay());
        interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS));
        run(A_CENTRED_WINDOW);
    }

    @AfterEach
    void closeThePage() {
        if (operator != null) {
            operator.close();
        }
        serving.close();
    }

    private String run(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private int pageWide() {
        return ((Number) operator.runsInThePage("return window.innerWidth")).intValue();
    }

    private int pageHigh() {
        return ((Number) operator.runsInThePage("return window.innerHeight")).intValue();
    }

    private Color readColourAt(int across, int down) {
        try {
            return new Color(ImageIO.read(new ByteArrayInputStream(
                    browser.findElement(By.id("screen")).getScreenshotAs(OutputType.BYTES)))
                    .getRGB(across, down));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void waitUntilColourAt(int across, int down, Color wanted) {
        new PageWait(browser, Duration.ofSeconds(20)).until(anything -> readColourAt(across, down).equals(wanted));
    }

    private int middleAcross() {
        return pageWide() / 2;
    }

    private int middleDown() {
        return pageHigh() / 2;
    }

    @Test
    @DisplayName("a maximised window is the page's size, with its content from the page's corner")
    void showsAMaximisedWindowFromTheCorner() {
        waitUntilColourAt(middleAcross(), middleDown(), THE_WINDOW);

        run("gui-metric/set 'maximize win");

        waitUntilColourAt(JUST, JUST, THE_WINDOW);
        assertThat(run("reduce [win/offset win/size]"))
                .isEqualTo("[0x0 " + pageWide() + "x" + pageHigh() + "]");
        assertThat(readColourAt(middleAcross() + 101, middleDown())).isEqualTo(THE_SCREEN);
    }

    @Test
    @DisplayName("a minimised window is not shown at all")
    void showsAMinimisedWindowNowhere() {
        waitUntilColourAt(middleAcross(), middleDown(), THE_WINDOW);

        run("gui-metric/set 'minimize win");

        waitUntilColourAt(middleAcross(), middleDown(), THE_SCREEN);
        assertThat(readColourAt(JUST, JUST)).isEqualTo(THE_SCREEN);
    }

    @Test
    @DisplayName("a restored window is shown where it was, after either")
    void showsARestoredWindowWhereItWas() {
        waitUntilColourAt(middleAcross(), middleDown(), THE_WINDOW);
        run("gui-metric/set 'maximize win");
        waitUntilColourAt(JUST, JUST, THE_WINDOW);

        run("gui-metric/set 'restore win");
        waitUntilColourAt(JUST, JUST, THE_SCREEN);
        assertThat(readColourAt(middleAcross(), middleDown())).isEqualTo(THE_WINDOW);

        run("gui-metric/set 'minimize win");
        waitUntilColourAt(middleAcross(), middleDown(), THE_SCREEN);
        run("gui-metric/set 'restore win");
        waitUntilColourAt(middleAcross(), middleDown(), THE_WINDOW);
    }
}
