package org.jebol.adapter.web;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class PageBrowsers implements AutoCloseable {

    private static final String PROMPT_HANDLING = "unhandledPromptBehavior";

    private static final Map<String, String> LEAVE_EVERY_PROMPT_TO_THE_TEST =
            Map.of("default", "ignore", "beforeUnload", "ignore");

    private static final String BIDI = "webSocketUrl";

    record Configuration(String browserName, boolean everyPopupBlocked, boolean leavingWatched) {

        boolean isFirefox() {
            return browserName.equals("firefox");
        }
    }

    record AStartedBrowser(WebDriver driver, Path downloads, String homeTab) {
    }

    private final Map<Configuration, AStartedBrowser> started = new LinkedHashMap<>();

    AStartedBrowser browserFor(Configuration wanted) {
        return started.computeIfAbsent(wanted, this::start);
    }

    @Override
    public void close() {
        started.values().forEach(this::stop);
        started.clear();
    }

    private AStartedBrowser start(Configuration wanted) {
        Path downloads = makeDownloadsFolder();
        WebDriver driver = wanted.isFirefox()
                ? openFirefox(wanted, downloads)
                : openChrome(wanted);
        return new AStartedBrowser(driver, downloads, driver.getWindowHandle());
    }

    private void stop(AStartedBrowser browser) {
        try {
            browser.driver().quit();
        } finally {
            deleteFolder(browser.downloads());
        }
    }

    private Path makeDownloadsFolder() {
        try {
            return Files.createTempDirectory("jebol-page-downloads");
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void deleteFolder(Path folder) {
        try (Stream<Path> inside = Files.walk(folder)) {
            inside.sorted(Comparator.reverseOrder()).forEach(this::deleteOne);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void deleteOne(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException undeletable) {
            throw new UncheckedIOException(undeletable);
        }
    }

    private WebDriver openChrome(Configuration wanted) {
        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new", "--no-sandbox", "--window-size=1000,700",
                "--force-device-scale-factor=1", "--hide-scrollbars");
        asked.setExperimentalOption("prefs", Map.of("download.prompt_for_download", false));
        speakBidi(asked, wanted);
        return new ChromeDriver(asked);
    }

    private WebDriver openFirefox(Configuration wanted, Path downloads) {
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
        if (wanted.everyPopupBlocked()) {
            profile.setPreference("dom.popup_allowed_events", "");
            profile.setPreference("dom.popup_maximum", 0);
            profile.setPreference("dom.disable_open_click_delay", 0);
        }
        FirefoxOptions asked = new FirefoxOptions();
        asked.addArguments("-headless", "-width=1000", "-height=700");
        asked.setProfile(profile);
        speakBidi(asked, wanted);
        return new FirefoxDriver(asked);
    }

    private void speakBidi(MutableCapabilities asked, Configuration wanted) {
        asked.setCapability(BIDI, true);
        if (wanted.leavingWatched()) {
            asked.setCapability(PROMPT_HANDLING, LEAVE_EVERY_PROMPT_TO_THE_TEST);
        }
    }
}
