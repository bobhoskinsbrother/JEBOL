package org.jebol.adapter.cli;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ATaskBodyRunsOnTheHostsPortsTest {

    private static final Duration A_WHILE = Duration.ofSeconds(10);

    @TempDir
    Path root;

    private final StringBuffer written = new StringBuffer();

    private Interpreter parent;

    @BeforeEach
    void startTheParent() throws IOException {
        Files.createDirectories(root.resolve("scripts"));
        Files.writeString(root.resolve("scripts/beside.txt"), "written beside the script");
        Bounds bounds = grantEverything();
        parent = Interpreter.createOn(
                Repl.portsFor(bounds, new String[]{Repl.ROOT_SWITCH, root.toString()},
                        new PrintStream(OutputStream.nullOutputStream()), false, root, new ProcessEnvironment(),
                        ConsolePort.none()),
                written::append, bounds);
        ScriptOutcome started = parent.start();
        assertThat(started.succeeded()).as(started.display()).isTrue();
    }

    private static Bounds grantEverything() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.grant(service);
        }
        return bounds.withoutTimeLimit();
    }

    private String run(String source) {
        return parent.run(source).display();
    }

    private void waitUntilWrittenSays(String mark) {
        long givenUpAt = System.nanoTime() + A_WHILE.toNanos();
        while (!written.toString().contains(mark)) {
            assertThat(System.nanoTime()).as("the body never wrote %s, only %s", mark, written).isLessThan(givenUpAt);
            Thread.onSpinWait();
        }
    }

    @Test
    @DisplayName("a body reads a file where its parent stands, having changed directory")
    void readsBesideTheParent() {
        run("change-dir %scripts/");

        run("do make task! [print [{read:} read/string %beside.txt]]");

        waitUntilWrittenSays("read: written beside the script");
    }

    @Test
    @DisplayName("a body answers the parent's platform, from the host's own machine")
    void answersTheParentsPlatform() {
        String parentsPlatform = run("form system/platform");

        run("do make task! [print [{platform:} system/platform]]");

        waitUntilWrittenSays("platform: " + parentsPlatform.replace("\"", ""));
    }

    @Test
    @DisplayName("a body sees what its parent set in the environment before DO")
    void seesTheParentsEnvironment() {
        run("set-env {JEBOL_TASK_PROBE} {set by the parent}");

        run("do make task! [print [{env:} get-env {JEBOL_TASK_PROBE}]]");

        waitUntilWrittenSays("env: set by the parent");
    }
}
