package org.jebol.adapter.web;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.bidi.browsingcontext.BrowsingContext;
import org.openqa.selenium.bidi.browsingcontext.CreateContextParameters;
import org.openqa.selenium.bidi.browsingcontext.UserPromptOpened;
import org.openqa.selenium.bidi.browsingcontext.UserPromptType;
import org.openqa.selenium.bidi.module.Browser;
import org.openqa.selenium.bidi.module.BrowsingContextInspector;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    private static final List<String> PERMISSIONS_EVERY_PAGE_IS_GRANTED =
            List.of("clipboardReadWrite", "clipboardSanitizedWrite", "midi", "midiSysex");

    private final PageBrowsers browsers;
    private final String browserName;
    private final String origin;
    private final List<String> deniedPermissions = new ArrayList<>();
    private final BlockingQueue<UserPromptOpened> leavePrompts = new LinkedBlockingQueue<>();
    private Optional<UserPromptOpened> openLeavePrompt = Optional.empty();
    private Optional<BrowsingContextInspector> watchingForLeavePrompts = Optional.empty();
    private boolean everyPopupBlocked;
    private boolean leavingWatched;
    private PageBrowsers.AStartedBrowser started;
    private String userContext;
    private WebDriver browser;
    private Path downloads;

    public AnOperatorAtThePage(PageBrowsers browsers, String browserName, String address) {
        this.browsers = browsers;
        this.browserName = browserName;
        this.origin = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
    }

    AnOperatorAtThePage(PageBrowsers browsers, String browserName, ShippedRunner runner) {
        this(browsers, browserName, runner.origin());
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
        started = browsers.browserFor(
                new PageBrowsers.Configuration(browserName, everyPopupBlocked, leavingWatched));
        browser = started.driver();
        downloads = started.downloads();
        userContext = new Browser(browser).createUserContext();
        if (leavingWatched) {
            watchForLeavePrompts();
        }
        if (isFirefox()) {
            openFirefoxTabAt(hash);
        } else {
            settleChromesDownloadsAndPermissions();
            openChromeTabAt(hash);
        }
        return this;
    }

    private void openFirefoxTabAt(String hash) {
        BrowsingContext tab = new BrowsingContext(browser,
                new CreateContextParameters(WindowType.TAB).userContext(userContext));
        browser.switchTo().window(tab.getId());
        browser.get(origin + "/" + hash);
    }

    private void openChromeTabAt(String hash) {
        Map<String, Object> created = ((ChromeDriver) browser).executeCdpCommand("Target.createTarget", Map.of(
                "url", origin + "/" + hash,
                "browserContextId", userContext));
        browser.switchTo().window((String) created.get("targetId"));
    }

    private void settleChromesDownloadsAndPermissions() {
        ChromeDriver chrome = (ChromeDriver) browser;
        chrome.executeCdpCommand("Browser.setDownloadBehavior", Map.of(
                "behavior", "allow", "downloadPath", downloads.toString(),
                "browserContextId", userContext));
        chrome.executeCdpCommand("Browser.grantPermissions", Map.of(
                "origin", origin,
                "permissions", PERMISSIONS_EVERY_PAGE_IS_GRANTED,
                "browserContextId", userContext));
        for (String permission : deniedPermissions) {
            chrome.executeCdpCommand("Browser.setPermission", Map.of(
                    "permission", Map.of("name", permission),
                    "setting", "denied",
                    "origin", origin,
                    "browserContextId", userContext));
        }
    }

    private void watchForLeavePrompts() {
        BrowsingContextInspector inspector = new BrowsingContextInspector(browser);
        inspector.onUserPromptOpened(asked -> {
            if (asked.getType() == UserPromptType.BEFORE_UNLOAD) {
                leavePrompts.add(asked);
            }
        });
        watchingForLeavePrompts = Optional.of(inspector);
    }

    Object runsInThePage(String script) {
        return ((JavascriptExecutor) browser).executeScript(script);
    }

    void opensAnotherTab() {
        new BrowsingContext(browser,
                new CreateContextParameters(WindowType.TAB).userContext(userContext));
    }

    public List<Path> listDownloads() {
        try (var listed = Files.list(downloads)) {
            return listed.toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
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
        return (int) browser.getWindowHandles().stream()
                .filter(tab -> !tab.equals(started.homeTab()))
                .count();
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
        if (browser == null) {
            return;
        }
        watchingForLeavePrompts.ifPresent(BrowsingContextInspector::close);
        new Browser(browser).removeUserContext(userContext);
        browser.switchTo().window(started.homeTab());
        listDownloads().forEach(this::deleteDownload);
    }

    private void deleteDownload(Path landed) {
        try {
            Files.deleteIfExists(landed);
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
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

}
