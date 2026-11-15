package org.jebol.adapter.web;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

final class PageWait extends WebDriverWait {

    private static final Duration POLLING = Duration.ofMillis(50);

    PageWait(WebDriver browser, Duration patience) {
        super(browser, patience, POLLING);
    }
}
