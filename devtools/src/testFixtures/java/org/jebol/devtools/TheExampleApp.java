package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;

public final class TheExampleApp {

    public static final String IDENTITY = "org.jebol.example.notes";
    public static final String TITLE = "Notes";

    private static final Set<String> NOT_COPIED = Set.of("build", ".gradle", ".kotlin");
    private static final char QUOTE = '"';

    private final Path directory;

    private TheExampleApp(Path directory) {
        this.directory = directory;
    }

    public static TheExampleApp copiedInto(Path scratch) {
        Path example = Path.of(System.getProperty("jebol.exampleApp"));
        Path repository = example.resolve("../../build/devtools-repo").normalize();
        Path copy = scratch.resolve("an-app-of-ones-own");
        try (Stream<Path> walked = Files.walk(example)) {
            for (Path each : walked.toList()) {
                Path relative = example.relativize(each);
                if (relative.getNameCount() > 0 && NOT_COPIED.contains(relative.getName(0).toString())) {
                    continue;
                }
                Path landing = copy.resolve(relative.toString());
                if (Files.isDirectory(each)) {
                    Files.createDirectories(landing);
                } else {
                    Files.copy(each, landing, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            String relativeRepository = """
                    maven(url = uri("../../build/devtools-repo"))""";
            String absoluteRepository = "maven(url = uri(" + QUOTE + repository.toUri() + QUOTE + "))";
            String settings = Files.readString(copy.resolve("settings.gradle.kts"))
                    .replace(relativeRepository, absoluteRepository);
            Files.writeString(copy.resolve("settings.gradle.kts"), settings);
        } catch (IOException uncopied) {
            throw new UncheckedIOException(uncopied);
        }
        return new TheExampleApp(copy);
    }

    public Path directory() {
        return directory;
    }

    public BuildResult run(String... tasks) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withArguments(tasks).forwardOutput().build();
    }
}
