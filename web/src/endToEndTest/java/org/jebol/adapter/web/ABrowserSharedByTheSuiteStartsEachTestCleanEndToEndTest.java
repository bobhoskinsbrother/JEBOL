package org.jebol.adapter.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.Dimension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(TheSuitesPageBrowsers.class)
class ABrowserSharedByTheSuiteStartsEachTestCleanEndToEndTest {

    private final PageBrowsers browsers;
    private ShippedRunner runner;

    ABrowserSharedByTheSuiteStartsEachTestCleanEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    @BeforeAll
    void serveOnePageForBothTests() throws IOException {
        runner = new ShippedRunner().running("""
                print "ready"
                """);
    }

    @AfterAll
    void closeThePage() {
        runner.close();
    }

    @Order(1)
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a test leaves stored data, history, a second tab and a download behind")
    void leavesStateBehind(String browserName) {
        try (AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");
            operator.runsInThePage("""
                    localStorage.setItem('left', 'behind');
                    sessionStorage.setItem('left', 'behind');
                    history.pushState({}, '', '#one');
                    history.pushState({}, '', '#two');
                    const link = document.createElement('a');
                    link.href = URL.createObjectURL(new Blob(['left behind']));
                    link.download = 'left-behind.txt';
                    document.body.appendChild(link);
                    link.click();""");
            operator.opensAnotherTab();
            operator.browser().manage().window().setSize(new Dimension(500, 400));

            operator.waitForDownload("left-behind.txt");
            assertThat(operator.countTabs()).isEqualTo(2);
            assertThat(operator.runsInThePage("return localStorage.getItem('left');")).isEqualTo("behind");
        }
    }

    @Order(2)
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("and the next test on the same page in the same browser finds none of it")
    void findsNoneOfIt(String browserName) {
        try (AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, browserName, runner).opensThePage()) {
            operator.waitUntilOutputContains("ready");

            assertThat(operator.runsInThePage("return localStorage.getItem('left');")).isNull();
            assertThat(operator.runsInThePage("return sessionStorage.getItem('left');")).isNull();
            assertThat(operator.runsInThePage("return history.length;")).isEqualTo(1L);
            assertThat(operator.readTheAddressHash()).isEmpty();
            assertThat(operator.countTabs()).isEqualTo(1);
            assertThat(operator.listDownloads()).isEmpty();
            assertThat(operator.browser().manage().window().getSize().getWidth())
                    .as("the window is maximised again, not left at the 500 the last test chose")
                    .isGreaterThan(500);
        }
    }
}
