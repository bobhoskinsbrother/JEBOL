package org.jebol.adapter.web;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.bidi.browsingcontext.BrowsingContext;
import org.openqa.selenium.bidi.browsingcontext.UserPromptOpened;
import org.openqa.selenium.bidi.browsingcontext.UserPromptType;
import org.openqa.selenium.bidi.module.BrowsingContextInspector;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class AnOperatorAtThePage implements AutoCloseable {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private static final String PROMPT_HANDLING = "unhandledPromptBehavior";

    private static final Map<String, String> LEAVE_EVERY_PROMPT_TO_THE_TEST =
            Map.of("default", "ignore", "beforeUnload", "ignore");

    private static final String BIDI = "webSocketUrl";

    private final String browserName;
    private final String origin;
    private final Path downloads;
    private final List<String> deniedPermissions = new ArrayList<>();
    private final BlockingQueue<UserPromptOpened> leavePrompts = new LinkedBlockingQueue<>();
    private Optional<UserPromptOpened> openLeavePrompt = Optional.empty();
    private boolean everyPopupBlocked;
    private boolean leavingWatched;
    private WebDriver browser;

    public AnOperatorAtThePage(String browserName, String address, Path downloads) {
        this.browserName = browserName;
        this.origin = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        this.downloads = downloads;
    }

    AnOperatorAtThePage(String browserName, ShippedRunner runner, Path downloads) {
        this(browserName, runner.origin(), downloads);
    }

    public AnOperatorAtThePage whoseBrowserBlocksEveryPopup() {
        everyPopupBlocked = true;
        return this;
    }

    public AnOperatorAtThePage whoseBrowserDenies(String permission) {
        deniedPermissions.add(permission);
        return this;
    }

    public AnOperatorAtThePage whoseLeavingIsWatched() {
        leavingWatched = true;
        return this;
    }

    public AnOperatorAtThePage opensThePage() {
        return opensThePageAt("");
    }

    public AnOperatorAtThePage opensThePageAt(String hash) {
        browser = isFirefox() ? openFirefox() : openChrome();
        if (leavingWatched) {
            new BrowsingContextInspector(browser).onUserPromptOpened(asked -> {
                if (asked.getType() == UserPromptType.BEFORE_UNLOAD) {
                    leavePrompts.add(asked);
                }
            });
        }
        browser.get(origin + "/" + hash);
        return this;
    }

    public void reloadsThePage() {
        browser.navigate().refresh();
    }

    public void pressesReload() {
        ((JavascriptExecutor) browser).executeScript("setTimeout(() => location.reload(), 0);");
    }

    public void pressesBack() {
        browser.navigate().back();
    }

    public void pressesForward() {
        browser.navigate().forward();
    }

    public void typesTheAddress(String hash) {
        browser.get(origin + "/" + hash);
    }

    public void goesElsewhere(String path) {
        browser.get(origin + path);
    }

    public void waitUntilTheAddressIs(String hash) {
        waitUntil("the address became " + hash, () -> hash.equals(readTheAddressHash()));
    }

    public String readTheAddressHash() {
        return (String) ((JavascriptExecutor) browser).executeScript("return location.hash;");
    }

    public void waitUntilTheTitleIs(String title) {
        waitUntil("the tab's title became " + title, () -> title.equals(browser.getTitle()));
    }

    public void clicksTheScreen() {
        new Actions(browser).moveToElement(browser.findElement(By.id("screen")), 0, 0).click().perform();
    }

    public void waitForTheLeavePrompt() throws InterruptedException {
        UserPromptOpened asked = leavePrompts.poll(PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
        if (asked == null) {
            throw new AssertionError("in " + browserName + " the browser never asked before leaving");
        }
        openLeavePrompt = Optional.of(asked);
    }

    public boolean isAskedBeforeLeaving() {
        return !leavePrompts.isEmpty();
    }

    public void staysOnThePage() {
        answerTheLeavePrompt(false);
    }

    public void leavesThePage() {
        answerTheLeavePrompt(true);
    }

    private void answerTheLeavePrompt(boolean leaving) {
        UserPromptOpened asked = openLeavePrompt.orElseThrow(() ->
                new AssertionError("in " + browserName + " there is no leave prompt to answer"));
        openLeavePrompt = Optional.empty();
        new BrowsingContext(browser, asked.getBrowsingContextId()).handleUserPrompt(leaving);
    }

    public long countOutputLinesSaying(String line) {
        return readOutput().stream().filter(line::equals).count();
    }

    public List<String> readOutput() {
        try {
            List<WebElement> shown = browser.findElements(By.id("out"));
            return shown.isEmpty()
                    ? List.of()
                    : shown.getFirst().getDomProperty("textContent").lines().toList();
        } catch (StaleElementReferenceException pageWasReplaced) {
            return List.of();
        }
    }

    public void waitUntilOutputContains(String line) {
        waitUntil("the page said " + line, () -> readOutput().contains(line));
    }

    public void waitUntilOutputHas(String fragment) {
        waitUntil("the page said something with " + fragment,
                () -> readOutput().stream().anyMatch(each -> each.contains(fragment)));
    }

    public void waitUntilTypingIsNamed(String heading) {
        waitUntil("the form's typing was named " + heading, () -> !browser.findElements(By.id("jebol-typing")).isEmpty()
                && heading.equals(browser.findElement(By.id("jebol-typing")).getDomAttribute("aria-label")));
    }

    public boolean isOutputInView() {
        return Boolean.TRUE.equals(((JavascriptExecutor) browser).executeScript("""
                const shown = document.getElementById('out').getBoundingClientRect();
                return shown.height > 0 && shown.top >= 0 && shown.bottom <= window.innerHeight;"""));
    }

    public void waitForThePromptSaying(String fragment) {
        waitUntil("the prompt asked " + fragment, () -> !browser.findElements(By.id("jebol-ask")).isEmpty()
                && browser.findElement(By.id("jebol-ask")).isDisplayed()
                && browser.findElement(By.id("jebol-ask-text")).getText().contains(fragment));
    }

    public String readTheAttributeOf(String elementId, String attribute) {
        return browser.findElement(By.id(elementId)).getDomAttribute(attribute);
    }

    public String readTheValueOf(String elementId) {
        return browser.findElement(By.id(elementId)).getDomProperty("value");
    }

    public void accepts() {
        browser.findElement(By.id("jebol-ask-yes")).click();
    }

    public void declines() {
        browser.findElement(By.id("jebol-ask-no")).click();
    }

    public void choosesColour(String hex) {
        if (isFirefox()) {
            WebElement typed = browser.findElement(By.id("jebol-ask-hex"));
            typed.clear();
            typed.sendKeys(hex);
        } else {
            browser.findElement(By.id("jebol-ask-colour")).sendKeys(hex);
        }
        accepts();
    }

    public void choosesFiles(Path... files) {
        browser.findElement(By.id("jebol-ask-file")).sendKeys(Arrays.stream(files)
                .map(Path::toString)
                .collect(Collectors.joining("\n")));
    }

    public void choosesFolder(Path folder) {
        browser.findElement(By.id("jebol-ask-folder")).sendKeys(folder.toString());
    }

    public void types(CharSequence... keys) {
        waitUntil("the form took the keyboard", () ->
                "jebol-typing".equals(browser.switchTo().activeElement().getDomAttribute("id")));
        new Actions(browser).sendKeys(keys).perform();
    }

    public void composes(String text) {
        ((ChromeDriver) browser).executeCdpCommand("Input.imeSetComposition", Map.of(
                "text", text, "selectionStart", text.length(), "selectionEnd", text.length()));
        ((ChromeDriver) browser).executeCdpCommand("Input.insertText", Map.of("text", text));
    }

    public void pressesEnter() {
        types(Keys.ENTER);
    }

    public void pressesEscape() {
        types(Keys.ESCAPE);
    }

    public int countTabs() {
        return browser.getWindowHandles().size();
    }

    public void waitForTabs(int count) {
        waitUntil(count + " tabs open", () -> countTabs() == count);
    }

    public Path waitForDownload(String name) {
        Path landed = downloads.resolve(name);
        waitUntil(name + " downloaded", () -> Files.isRegularFile(landed) && !isStillDownloading());
        return landed;
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.quit();
        }
    }

    private boolean isFirefox() {
        return browserName.equals("firefox");
    }

    private boolean isStillDownloading() {
        try (var listed = Files.list(downloads)) {
            return listed.anyMatch(file -> file.toString().endsWith(".part")
                    || file.toString().endsWith(".crdownload"));
        } catch (IOException unreadable) {
            return true;
        }
    }

    private void waitUntil(String hoped, Supplier<Boolean> condition) {
        try {
            new WebDriverWait(browser, PATIENCE).until(anything -> hasGivenUp() || condition.get());
        } catch (TimeoutException late) {
            throw new AssertionError("in " + browserName + " " + hoped + " never happened; the page said: "
                    + readOutput(), late);
        }
        if (hasGivenUp()) {
            throw new AssertionError("in " + browserName + " the worker gave up before " + hoped
                    + "; the page said: " + readOutput());
        }
    }

    private boolean hasGivenUp() {
        return readOutput().stream().anyMatch(line ->
                line.startsWith("failed ") || line.startsWith("worker error"));
    }

    private WebDriver openChrome() {
        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new", "--no-sandbox", "--window-size=1000,700",
                "--force-device-scale-factor=1", "--hide-scrollbars");
        handOverLeavePrompts(asked);
        asked.setExperimentalOption("prefs", Map.of(
                "download.default_directory", downloads.toString(),
                "download.prompt_for_download", false));
        ChromeDriver chrome = new ChromeDriver(asked);
        chrome.executeCdpCommand("Browser.setDownloadBehavior", Map.of(
                "behavior", "allow", "downloadPath", downloads.toString()));
        chrome.executeCdpCommand("Browser.grantPermissions", Map.of(
                "origin", origin,
                "permissions", List.of("clipboardReadWrite", "clipboardSanitizedWrite", "midi", "midiSysex")));
        for (String permission : deniedPermissions) {
            chrome.executeCdpCommand("Browser.setPermission", Map.of(
                    "permission", Map.of("name", permission),
                    "setting", "denied",
                    "origin", origin));
        }
        return chrome;
    }

    private WebDriver openFirefox() {
        FirefoxProfile profile = new FirefoxProfile();
        profile.setPreference("browser.download.folderList", 2);
        profile.setPreference("browser.download.dir", downloads.toString());
        profile.setPreference("browser.download.useDownloadDir", true);
        profile.setPreference("browser.download.always_ask_before_handling_new_types", false);
        profile.setPreference("dom.events.testing.asyncClipboard", true);
        profile.setPreference("dom.webmidi.enabled", true);
        profile.setPreference("midi.testing", true);
        profile.setPreference("midi.prompt.testing", true);
        profile.setPreference("media.navigator.permission.disabled", true);
        profile.setPreference("dom.webmidi.gated", false);
        profile.setPreference("dom.disable_beforeunload", false);
        if (everyPopupBlocked) {
            profile.setPreference("dom.popup_allowed_events", "");
            profile.setPreference("dom.popup_maximum", 0);
            profile.setPreference("dom.disable_open_click_delay", 0);
        }
        FirefoxOptions asked = new FirefoxOptions();
        asked.addArguments("-headless", "-width=1000", "-height=700");
        asked.setProfile(profile);
        handOverLeavePrompts(asked);
        return new FirefoxDriver(asked);
    }

    private void handOverLeavePrompts(MutableCapabilities asked) {
        if (leavingWatched) {
            asked.setCapability(PROMPT_HANDLING, LEAVE_EVERY_PROMPT_TO_THE_TEST);
            asked.setCapability(BIDI, true);
        }
    }
}
