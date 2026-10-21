package org.jebol.suite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaHost;
import org.jebol.adapter.host.JavaImage;
import org.jebol.adapter.host.JavaProcess;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;

final class SuiteHost {

    private SuiteHost() {
    }

    static Bounds grantEverything() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.grant(service);
        }
        return bounds;
    }

    static Interpreter anInterpreterFor(Bounds bounds) {
        return anInterpreterFor(bounds, OutputPort.discard());
    }

    static Interpreter anInterpreterFor(Bounds bounds, OutputPort output) {
        Path theRootOfThisRun;
        try {
            theRootOfThisRun = Files.createTempDirectory("jebol-suite");
            layOutTheFilesTheSuiteReads(theRootOfThisRun);
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
        HostPorts ports = new JavaHost().ports()
                .withFiles(FileSystemPort.rootFileSystem(theRootOfThisRun))
                .withEnvironment(new ProcessEnvironment())
                .withProcesses(new JavaProcess())
                .withImages(new JavaImage())
                .withNetwork(new JavaSocket())
                .withCertificateAuthority(new JavaCertificateAuthority());
        Interpreter interpreter = Interpreter.createOn(ports, output, bounds);
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
        interpreter.defineNewWordsIn(sayingSo);
        interpreter.run(sayingSo);
    }

    private static void putTheApplicationDataDirectoryThereToo(Interpreter interpreter) {
        String sayingSo = """
                system/options/data: %/data/
                make-dir/deep system/options/data
                set '~ system/options/data""";
        interpreter.defineNewWordsIn(sayingSo);
        interpreter.run(sayingSo);
        interpreter.followApplicationDataDirectory();
    }

    private static void putTheModulesTheSuiteImportsWhereImportLooks(Path root)
            throws IOException {

        Path from = Path.of("src", "testFixtures", "resources", "rebol-modules");
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

        Path from = Path.of("src", "testFixtures", "resources", "rebol-suite");
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
        Path from = Path.of("src", "testFixtures", "resources", "rebol-suite", "units", "files");
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
