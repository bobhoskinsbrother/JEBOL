package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AnAppBuild {

    private static final String SETTINGS = """
            rootProject.name = "notes"
            """;

    private static final String BUILD = """
            plugins {
                id("org.jebol.devtools")
            }
            """;

    private final ATestApp app;

    private AnAppBuild(ATestApp app) {
        this.app = app;
    }

    public static AnAppBuild of(ATestApp app) {
        app.withFile("settings.gradle.kts", SETTINGS).withFile("build.gradle.kts", BUILD);
        return new AnAppBuild(app);
    }

    public Path directory() {
        return app.directory();
    }

    public ATestApp app() {
        return app;
    }

    public BuildResult run(String... tasks) {
        return runner(tasks).build();
    }

    public BuildResult fail(String... tasks) {
        return runner(tasks).buildAndFail();
    }

    private GradleRunner runner(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(app.directory().toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    public String read(String relative) {
        try {
            return Files.readString(app.directory().resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
