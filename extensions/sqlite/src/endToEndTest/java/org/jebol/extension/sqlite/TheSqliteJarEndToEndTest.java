package org.jebol.extension.sqlite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteJarEndToEndTest {

    private static final Path FOLDER = Path.of(System.getProperty("jebol.extensionFolder"));

    private static final Path JAR = Path.of(System.getProperty("jebol.extensionJar"));

    private static final Pattern NEEDS = Pattern.compile("needs: \\[([^]]*)]");

    private static List<String> readClassPath() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            String classPath = jar.getManifest().getMainAttributes().getValue("Class-Path");
            return classPath == null || classPath.isBlank() ? List.of() : Arrays.asList(classPath.split(" "));
        }
    }

    private static String readDescriptor() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile());
             InputStream in = jar.getInputStream(jar.getEntry("META-INF/jebol/extension.reb"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("the jar is named jebol-sqlite.jar")
    void isNamedForTheExtension() {
        assertThat(JAR.getFileName().toString()).isEqualTo("jebol-sqlite.jar");
        assertThat(Files.isRegularFile(JAR)).isTrue();
    }

    @Test
    @DisplayName("every library its manifest names is beside it")
    void carriesItsLibrariesBesideIt() throws IOException {
        assertThat(readClassPath()).isNotEmpty()
                .allSatisfy(library -> assertThat(FOLDER.resolve(library)).isRegularFile());
    }

    @Test
    @DisplayName("the descriptor's jvm needs are the manifest's Class-Path, so the two cannot drift apart")
    void needsAreTheClassPath() throws IOException {
        Matcher needs = NEEDS.matcher(readDescriptor());
        assertThat(needs.find()).isTrue();
        List<String> named = Arrays.stream(needs.group(1).trim().split(" "))
                .map(file -> file.substring(1))
                .toList();

        assertThat(named).isEqualTo(readClassPath());
    }

    @Test
    @DisplayName("the descriptor names the jvm provider")
    void namesTheProvider() throws IOException {
        assertThat(readDescriptor()).contains("provider: org.jebol.extension.sqlite.SqliteOnTheJvm");
    }
}
