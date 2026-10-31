package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TheRunnerPageTest {

    private static final Path RUNNER = Path.of("site", "runner.html");

    private static final Pattern SCRIPT_SOURCE = Pattern.compile("<script src=\"([^\"]+)\"></script>");

    private static List<String> scriptsNamedIn(String page) {
        List<String> named = new ArrayList<>();
        Matcher found = SCRIPT_SOURCE.matcher(page);
        while (found.find()) {
            named.add(found.group(1));
        }
        return named;
    }

    @Test
    @DisplayName("carries no script of its own: every script it runs is a file it names")
    void carriesNoInlineScript() throws IOException {
        String page = Files.readString(RUNNER);

        assertThat(page).doesNotContain("<script>");
        assertThat(scriptsNamedIn(page)).containsExactly(
                "jebol-asks.js", "jebol-navigation.js", "jebol-paint.js",
                "jebol-screen.js", "jebol-input.js", "jebol-loading.js", "jebol-runner.js");
    }

    @Test
    @DisplayName("names only scripts the project holds, so a site built around it is whole")
    void namesScriptsThatExist() throws IOException {
        for (String script : scriptsNamedIn(Files.readString(RUNNER))) {
            boolean held = Files.isRegularFile(Path.of("site", script))
                    || Files.isRegularFile(Path.of("src", "main", "resources", "jebol-page", script));
            assertThat(held).as(script).isTrue();
        }
    }
}
