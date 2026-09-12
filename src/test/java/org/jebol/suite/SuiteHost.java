package org.jebol.suite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.jebol.adapter.host.JavaImages;
import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.JavaSockets;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;

final class SuiteHost {

    private SuiteHost() {
    }

    static Bounds grantingEverything() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.granting(service);
        }
        return bounds;
    }

    static Interpreter installOn(Interpreter interpreter) {
        Path theRootOfThisRun;
        try {
            theRootOfThisRun = Files.createTempDirectory("jebol-suite");
            layOutTheFilesTheSuiteReads(theRootOfThisRun);
            interpreter.useFileSystem(FileSystemPort.rootedAt(theRootOfThisRun));
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useProcesses(new JavaProcesses());
        interpreter.useImages(new JavaImages());
        interpreter.useNetwork(new JavaSockets());
        putHomeInsideTheDirectoryTheRunCanReach(interpreter);
        putTheApplicationDataDirectoryThereToo(interpreter);
        try {
            putTheModulesTheSuiteImportsWhereImportLooks(theRootOfThisRun);
        } catch (IOException noModules) {
            throw new UncheckedIOException(noModules);
        }
        return interpreter;
    }

    private static void putHomeInsideTheDirectoryTheRunCanReach(
            Interpreter interpreter) {

        String sayingSo = "system/options/home: %./";
        interpreter.defineFreshWordsIn(sayingSo);
        interpreter.run(sayingSo);
    }

    private static void putTheApplicationDataDirectoryThereToo(Interpreter interpreter) {
        String sayingSo = """
                system/options/data: %/data/
                make-dir/deep system/options/data
                set '~ system/options/data""";
        interpreter.defineFreshWordsIn(sayingSo);
        interpreter.run(sayingSo);
        interpreter.putTheModulesDirectoryBesideTheData();
    }

    private static void putTheModulesTheSuiteImportsWhereImportLooks(Path root)
            throws IOException {

        Path from = Path.of("src", "test", "resources", "rebol-modules");
        Path into = root.resolve("data").resolve("modules");
        if (!Files.isDirectory(from) || !Files.isDirectory(into)) {
            return;
        }
        try (Stream<Path> modules = Files.list(from)) {
            for (Path one : modules.toList()) {
                Files.copy(one, into.resolve(one.getFileName().toString()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void putTheSuiteFilesWhereARunStandsAmongThem(Path root)
            throws IOException {

        Path from = Path.of("src", "test", "resources", "rebol-suite");
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> suiteFiles = Files.list(from)) {
            for (Path one : suiteFiles.toList()) {
                if (Files.isRegularFile(one) && one.getFileName().toString().endsWith(".r3")) {
                    Files.copy(one, root.resolve(one.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path theRunner = from.resolve("beside-a-run").resolve("run-tests.r3");
        if (Files.isRegularFile(theRunner)) {
            Files.copy(theRunner, root.resolve("run-tests.r3"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void layOutTheFilesTheSuiteReads(Path root) throws IOException {
        putTheSuiteFilesWhereARunStandsAmongThem(root);
        Path into = root.resolve("units").resolve("files");
        Files.createDirectories(into);
        Path from = Path.of("src", "test", "resources", "rebol-suite", "units", "files");
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> everything = Files.walk(from)) {
            for (Path one : everything.toList()) {
                Path landing = into.resolve(from.relativize(one).toString());
                if (Files.isDirectory(one)) {
                    Files.createDirectories(landing);
                } else {
                    Files.copy(one, landing, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
