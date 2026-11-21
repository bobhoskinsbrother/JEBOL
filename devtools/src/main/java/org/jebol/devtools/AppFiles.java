package org.jebol.devtools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class AppFiles {

    private static final Set<String> NOT_THE_APP = Set.of(AppReb.FILE, "build", ".gradle", "gradle", "gradlew",
            "gradlew.bat", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "gradle.properties", "local.properties", "android-signing.properties", ".git", ".gitignore", ".idea",
            ".kotlin");

    private AppFiles() {
    }

    public static List<String> overlay(AppDeclaration declaration, Platform platform, Path target) {
        Path app = declaration.directory();
        deleteRecursively(target);
        Set<String> names = new TreeSet<>();
        try (Stream<Path> top = Files.list(app)) {
            for (Path entry : top.toList()) {
                String name = entry.getFileName().toString();
                if (!NOT_THE_APP.contains(name) && !Platform.isProjectFolder(name)) {
                    names.addAll(copyTree(entry, target.resolve(name), target));
                }
            }
        } catch (IOException unlisted) {
            throw new UncheckedIOException(unlisted);
        }
        Path overlay = Projects.folderFor(app, platform).resolve(Projects.OVERLAY_FOLDER);
        if (Files.isDirectory(overlay)) {
            try (Stream<Path> top = Files.list(overlay)) {
                for (Path entry : top.toList()) {
                    names.addAll(copyTree(entry, target.resolve(entry.getFileName().toString()), target));
                }
            } catch (IOException unlisted) {
                throw new UncheckedIOException(unlisted);
            }
        }
        return List.copyOf(names);
    }

    private static Set<String> copyTree(Path source, Path destination, Path root) throws IOException {
        Set<String> copied = new TreeSet<>();
        try (Stream<Path> walked = Files.walk(source)) {
            for (Path each : walked.toList()) {
                Path landing = destination.resolve(source.relativize(each).toString());
                if (Files.isDirectory(each)) {
                    Files.createDirectories(landing);
                } else {
                    Files.createDirectories(landing.getParent());
                    Files.copy(each, landing, StandardCopyOption.REPLACE_EXISTING);
                    copied.add(root.relativize(landing).toString().replace('\\', '/'));
                }
            }
        }
        return copied;
    }

    public static void deleteRecursively(Path target) {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> walked = Files.walk(target)) {
            for (Path each : walked.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        } catch (IOException undeleted) {
            throw new UncheckedIOException(undeleted);
        }
    }

    public static void writeAppJson(List<String> names, String entry, Path target) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, Json.appJson(names, entry));
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }
}
