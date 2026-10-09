package org.jebol.adapter.web;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class AnOperatorInABrowserDrivesAGuiEndToEndTest {

    private static final int PAGE_WIDE = 600;
    private static final int PAGE_HIGH = 500;

    private WebScreenServer serving;
    private BrowserScreen screen;
    private ChromeDriver browser;
    private Interpreter interpreter;

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
        new WebDriverWait(browser, Duration.ofSeconds(20)).until(anything -> screen.hasADisplay());

        Bounds everything = Bounds.standard().withWallClockLimit(Duration.ofSeconds(100));
        for (HostService service : HostService.values()) {
            everything = everything.granting(service);
        }
        interpreter = Interpreter.withBounds(everything);
        interpreter.useScreen(screen);
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useFileSystem(FileSystemPort.rootedAt(Path.of("src/test/resources").toAbsolutePath()));
    }

    @AfterEach
    void closeThePage() {
        if (browser != null) {
            browser.quit();
        }
        serving.close();
    }

    private String answerTo(String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private WebElement theCanvas() {
        return browser.findElement(By.id("screen"));
    }

    private Actions theOperatorAt(int pageAcross, int pageDown) {
        WebElement canvas = theCanvas();
        return new Actions(browser).moveToElement(canvas,
                pageAcross - canvas.getRect().getWidth() / 2,
                pageDown - canvas.getRect().getHeight() / 2);
    }

    private void untilTheScriptHasHeard(String question) {
        new WebDriverWait(browser, Duration.ofSeconds(20)).until(anything -> {
            answerTo("wait [system/ports/event 0.05]");
            return "#(true)".equals(answerTo(question));
        });
    }

    private void aWindowRecordingWhatItHears() {
        answerTo("""
                view/no-wait make gob! [size: 300x200 color: 200.200.200]
                system/view/screen-gob/1/offset: 20x30
                show system/view/screen-gob
                heard: copy []
                handle-events [
                    name: 'recorder
                    priority: 90
                    handler: func [event] [
                        append/only heard reduce [
                            event/type
                            either find [down up move] event/type [event/offset] [event/key]
                        ]
                        event
                    ]
                ]""");
    }

    @Test
    @DisplayName("a real click in the browser reaches the handler with where it was, counted from the window")
    void aClickCarriesWhereItWas() {
        aWindowRecordingWhatItHears();

        theOperatorAt(20 + 40, 30 + 50).click().perform();

        untilTheScriptHasHeard("""
                not none? find/only heard [down 40x50]""");
    }

    @Test
    @DisplayName("moving the mouse over the page is heard as moves, each with where the pointer is")
    void aMoveIsHeard() {
        aWindowRecordingWhatItHears();

        theOperatorAt(20 + 70, 30 + 60).perform();

        untilTheScriptHasHeard("""
                not none? find/only heard [move 70x60]""");
    }

    @Test
    @DisplayName("a typed letter is heard as a key with that character")
    void aTypedLetterIsHeard() {
        aWindowRecordingWhatItHears();

        new Actions(browser).sendKeys("a").perform();

        untilTheScriptHasHeard("""
                not none? find/only heard [key #"a"]""");
    }

    @Test
    @DisplayName("a typed comma is heard as a key too, though the page's message is split on commas")
    void aTypedCommaIsHeard() {
        aWindowRecordingWhatItHears();

        new Actions(browser).sendKeys(",").perform();

        untilTheScriptHasHeard("""
                not none? find/only heard [key #","]""");
    }

    @Test
    @DisplayName("an arrow key is heard as a control event naming it")
    void anArrowKeyIsHeard() {
        aWindowRecordingWhatItHears();

        new Actions(browser).sendKeys(Keys.ARROW_LEFT).perform();

        untilTheScriptHasHeard("""
                not none? find/only heard [control left]""");
    }

    @Test
    @DisplayName("the sliders example's slider is dragged by a real mouse in a browser, and its value changes")
    void theSlidersAreDraggedInABrowser() {
        String opened = answerTo("""
                change-dir %examples/r3-gui/
                do %gui-322.r
                the-layout: last load %sliders.r
                gui-view: get bind 'view first find gui first [view:]
                the-window: gui-view/no-wait the-layout
                the-middle-of-the-slider: none
                walk: func [gob at] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data 'slider = select sub/data 'style] [
                            the-middle-of-the-slider: at + sub/offset + to pair! sub/size / 2
                            the-slider: sub/data
                        ]
                        walk sub at + sub/offset
                    ]
                ]
                walk the-window the-window/offset
                before: get-face the-slider
                to pair! reduce [to integer! the-middle-of-the-slider/x to integer! the-middle-of-the-slider/y]""");
        String[] middle = opened.split("x");
        int across = Integer.parseInt(middle[0]);
        int down = Integer.parseInt(middle[1]);

        theOperatorAt(across, down).clickAndHold().moveByOffset(80, 0).release().perform();

        new WebDriverWait(browser, Duration.ofSeconds(20)).until(anything -> {
            answerTo("wait [gui-event-port 0.05]");
            return "#(true)".equals(answerTo("before <> get-face the-slider"));
        });
        assertThat(answerTo("(get-face the-slider) > before")).isEqualTo("#(true)");
    }
}
