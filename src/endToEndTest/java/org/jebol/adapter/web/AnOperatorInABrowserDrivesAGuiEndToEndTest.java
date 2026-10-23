package org.jebol.adapter.web;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class AnOperatorInABrowserDrivesAGuiEndToEndTest {

    private final PageBrowsers browsers;
    private WebScreenServer serving;
    private BrowserScreen screen;
    private AnOperatorAtThePage operator;
    private WebDriver browser;
    private Interpreter interpreter;

    AnOperatorInABrowserDrivesAGuiEndToEndTest(PageBrowsers browsers) {
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

        Bounds everything = Bounds.standard().withTimeLimit(Duration.ofSeconds(100));
        for (HostService service : HostService.values()) {
            everything = everything.grant(service);
        }
        interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withScreen(screen)
                        .withEnvironment(new ProcessEnvironment())
                        .withFiles(FileSystemPort.rootFileSystem(
                                Path.of("src/testFixtures/resources").toAbsolutePath())),
                OutputPort.discard(),
                everything);
    }

    @AfterEach
    void closeThePage() {
        if (operator != null) {
            operator.close();
        }
        serving.close();
    }

    private String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private WebElement findTheCanvas() {
        return browser.findElement(By.id("screen"));
    }

    private Actions placeTheOperatorAt(int pageAcross, int pageDown) {
        WebElement canvas = findTheCanvas();
        return new Actions(browser).moveToElement(canvas,
                pageAcross - canvas.getRect().getWidth() / 2,
                pageDown - canvas.getRect().getHeight() / 2);
    }

    private void waitUntilTheScriptHasHeard(String question) {
        new PageWait(browser, Duration.ofSeconds(20)).until(anything -> {
            answerTo("wait [system/ports/event 0.05]");
            return "#(true)".equals(answerTo(question));
        });
    }

    private void openAWindowRecordingWhatItHears() {
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
    void carriesWhereAClickWas() {
        openAWindowRecordingWhatItHears();

        placeTheOperatorAt(20 + 40, 30 + 50).click().perform();

        waitUntilTheScriptHasHeard("""
                not none? find/only heard [down 40x50]""");
    }

    @Test
    @DisplayName("moving the mouse over the page is heard as moves, each with where the pointer is")
    void hearsAMove() {
        openAWindowRecordingWhatItHears();

        placeTheOperatorAt(20 + 70, 30 + 60).perform();

        waitUntilTheScriptHasHeard("""
                not none? find/only heard [move 70x60]""");
    }

    @Test
    @DisplayName("a typed letter is heard as a key with that character")
    void hearsATypedLetter() {
        openAWindowRecordingWhatItHears();

        new Actions(browser).sendKeys("a").perform();

        waitUntilTheScriptHasHeard("""
                not none? find/only heard [key #"a"]""");
    }

    @Test
    @DisplayName("a typed comma is heard as a key too, though the page's message is split on commas")
    void hearsATypedComma() {
        openAWindowRecordingWhatItHears();

        new Actions(browser).sendKeys(",").perform();

        waitUntilTheScriptHasHeard("""
                not none? find/only heard [key #","]""");
    }

    @Test
    @DisplayName("an arrow key is heard as a control event naming it")
    void hearsAnArrowKey() {
        openAWindowRecordingWhatItHears();

        new Actions(browser).sendKeys(Keys.ARROW_LEFT).perform();

        waitUntilTheScriptHasHeard("""
                not none? find/only heard [control left]""");
    }

    private void openTwoWindowsRecordingWhichOneHears() {
        answerTo("""
                left: view/no-wait make gob! [size: 200x200 color: 200.0.0]
                left/offset: 20x30
                right: view/no-wait make gob! [size: 200x200 color: 0.0.200]
                right/offset: 400x30
                show system/view/screen-gob
                heard: copy []
                handle-events [
                    name: 'recorder
                    priority: 90
                    handler: func [event] [
                        if find [down key] event/type [
                            append/only heard reduce [
                                event/type
                                case [same? event/window left ['left] same? event/window right ['right] true ['neither]]
                                either event/type = 'down [event/offset] [event/key]
                            ]
                        ]
                        event
                    ]
                ]""");
    }

    @Test
    @DisplayName("with two windows on the page, a real click on the second reaches the second and not the first")
    void sendsAClickToTheWindowItLandedOn() {
        openTwoWindowsRecordingWhichOneHears();

        placeTheOperatorAt(400 + 10, 30 + 20).click().perform();
        placeTheOperatorAt(20 + 30, 30 + 40).click().perform();

        waitUntilTheScriptHasHeard("""
                [[down right 10x20] [down left 30x40]] = heard""");
    }

    @Test
    @DisplayName("a real click on the bare page between two windows reaches neither")
    void sendsAClickOnTheBarePageNowhere() {
        openTwoWindowsRecordingWhichOneHears();

        placeTheOperatorAt(300, 100).click().perform();
        placeTheOperatorAt(20 + 5, 30 + 5).click().perform();

        waitUntilTheScriptHasHeard("""
                [[down left 5x5]] = heard""");
    }

    @Test
    @DisplayName("typing after a click on the first window reaches the first, though the second opened last")
    void sendsTypingWhereTheClickWent() {
        openTwoWindowsRecordingWhichOneHears();

        placeTheOperatorAt(20 + 5, 30 + 5).click().perform();
        new Actions(browser).sendKeys("a").perform();

        waitUntilTheScriptHasHeard("""
                [[down left 5x5] [key left #"a"]] = heard""");
    }

    @Test
    @DisplayName("a layout's text that looks like markup is painted as text, so nothing in it runs or joins the page, and the button still answers a click")
    void paintsMarkupAsText() {
        String opened = answerTo("""
                clicked: false
                the-window: view/no-wait [
                    title {<script>window.jebolInjected = 'title'</script>}
                    pressing: button {<img src=x onerror="window.jebolInjected = 'button'">} do [clicked: true]
                ]
                pressing: none
                walk: func [gob at] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data 'pressing = select sub/data 'name] [
                            pressing: at + sub/offset + to pair! sub/size / 2
                        ]
                        walk sub at + sub/offset
                    ]
                ]
                walk the-window the-window/offset
                to pair! reduce [to integer! pressing/x to integer! pressing/y]""");
        String[] middle = opened.split("x");

        placeTheOperatorAt(Integer.parseInt(middle[0]), Integer.parseInt(middle[1])).click().perform();

        new PageWait(browser, Duration.ofSeconds(20)).until(anything -> {
            answerTo("wait [gui-event-port 0.05]");
            return "#(true)".equals(answerTo("clicked"));
        });
        assertThat(operator.runsInThePage("return typeof window.jebolInjected")).isEqualTo("undefined");
        assertThat(browser.findElements(By.tagName("img"))).isEmpty();
    }

    @Test
    @DisplayName("the sliders example's slider is dragged by a real mouse in a browser, and its value changes")
    void dragsTheSlidersInABrowser() {
        String opened = answerTo("""
                change-dir %examples/r3-gui/
                the-layout: last load %sliders.r
                the-window: view/no-wait the-layout
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

        placeTheOperatorAt(across, down).clickAndHold().moveByOffset(80, 0).release().perform();

        new PageWait(browser, Duration.ofSeconds(20)).until(anything -> {
            answerTo("wait [gui-event-port 0.05]");
            return "#(true)".equals(answerTo("before <> get-face the-slider"));
        });
        assertThat(answerTo("(get-face the-slider) > before")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the sliders example's scroller is turned by a real wheel in a browser, and its value changes")
    void turnsTheScrollerInABrowser() {
        String opened = answerTo("""
                change-dir %examples/r3-gui/
                the-layout: last load %sliders.r
                the-window: view/no-wait the-layout
                the-scroller: none
                the-middle-of-the-scroller: none
                walk: func [gob at] [
                    foreach sub any [gob/pane []] [
                        if all [object? sub/data 'scroller = select sub/data 'style none? the-scroller] [
                            the-scroller: sub/data
                            the-middle-of-the-scroller: at + sub/offset + to pair! sub/size / 2
                        ]
                        walk sub at + sub/offset
                    ]
                ]
                walk the-window the-window/offset
                before: get-face the-scroller
                to pair! reduce [to integer! the-middle-of-the-scroller/x to integer! the-middle-of-the-scroller/y]""");
        String[] middle = opened.split("x");
        int across = Integer.parseInt(middle[0]);
        int down = Integer.parseInt(middle[1]);

        placeTheOperatorAt(across, down).perform();
        new Actions(browser)
                .scrollFromOrigin(WheelInput.ScrollOrigin.fromViewport(across, down), 0, 400)
                .perform();

        new PageWait(browser, Duration.ofSeconds(20)).until(anything -> {
            answerTo("wait [gui-event-port 0.05]");
            return "#(true)".equals(answerTo("before <> get-face the-scroller"));
        });
        assertThat(answerTo("(get-face the-scroller) > before")).isEqualTo("#(true)");
    }

    private void openTheSurvey() {
        answerTo("""
                change-dir %examples/r3-gui/
                the-layout: last load %survey.r
                the-window: view/no-wait the-layout
                the-faces-styled: func [style /local found walk] [
                    found: copy []
                    walk: func [gob at /local here] [
                        foreach sub any [gob/pane []] [
                            here: at + sub/offset
                            if all [object? sub/data style = select sub/data 'style] [
                                append found reduce [sub/data here + to pair! sub/size / 2]
                            ]
                            walk sub here
                        ]
                    ]
                    walk the-window the-window/offset
                    found
                ]
                the-face: func [style index] [pick extract the-faces-styled style 2 index]
                the-middle-of: func [style index /local middle] [
                    middle: pick extract next the-faces-styled style 2 index
                    to pair! reduce [to integer! middle/x to integer! middle/y]
                ]
                chosen?: func [style index] [true? get-face the-face style index]""");
    }

    private void clickAsTheOperator(String style, int index) {
        String[] middle = answerTo("the-middle-of '%s %d".formatted(style, index)).split("x");
        placeTheOperatorAt(Integer.parseInt(middle[0]), Integer.parseInt(middle[1])).click().perform();
    }

    private void waitUntilTheGuiHasHeard(String question) {
        try {
            new PageWait(browser, Duration.ofSeconds(20)).until(anything -> {
                answerTo("wait [gui-event-port 0.05]");
                return "#(true)".equals(answerTo(question));
            });
        } catch (TimeoutException neverHeard) {
            throw new AssertionError("the GUI never heard: " + question, neverHeard);
        }
    }

    @Test
    @DisplayName("in a browser, the survey takes a name typed into its field and a chosen radio, and Reset clears both")
    void fillsInAndResetsTheSurveyInABrowser() {
        openTheSurvey();

        clickAsTheOperator("field", 1);
        waitUntilTheGuiHasHeard("""
                same? guie/focal-face the-face 'field 1""");
        new Actions(browser).sendKeys("Ben").perform();
        waitUntilTheGuiHasHeard("""
                {Ben} = get-face the-face 'field 1""");

        clickAsTheOperator("radio", 2);
        waitUntilTheGuiHasHeard("chosen? 'radio 2");
        assertThat(answerTo("reduce [chosen? 'radio 1  chosen? 'radio 3]")).isEqualTo("[#(false) #(false)]");

        clickAsTheOperator("button", 2);
        waitUntilTheGuiHasHeard("""
                all [empty? get-face the-face 'field 1  not chosen? 'radio 2]""");
    }

    @Test
    @DisplayName("a sentence typed into the comment area arrives whole and in order, however fast it is typed")
    void deliversASentenceTypedIntoTheCommentAreaWhole() {
        openTheSurvey();

        clickAsTheOperator("area", 1);
        waitUntilTheGuiHasHeard("""
                not none? guie/focal-face""");
        new Actions(browser).sendKeys(
                "the quick brown fox jumps over the lazy dog and keeps on running for a very long time indeed")
                .perform();
        waitUntilTheGuiHasHeard("""
                not empty? get-face the-face 'area 1""");
        assertThat(answerTo("""
                get-face the-face 'area 1"""))
                .isEqualTo("{the quick brown fox jumps over the lazy dog and keeps on running for a very long time indeed}");
    }
}
