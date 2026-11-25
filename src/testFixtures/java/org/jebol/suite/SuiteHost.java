package org.jebol.suite;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSecureTransport;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.adapter.host.WakeablePause;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.CarriedFixtures;
import org.jebol.application.DriveLetterFiles;
import org.jebol.domain.eval.LocalFileConvention;
import org.jebol.domain.host.FilePort;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;

final class SuiteHost {

    private static final char WINDOWS_SEPARATOR = '\\';

    private static final Queue<Path> ROOTS_TO_REMOVE_AT_EXIT = new ConcurrentLinkedQueue<>();

    private static final Duration LONGEST_A_STEP_MAY_RUN_ON_A_LOADED_MACHINE = Duration.ofSeconds(60);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SuiteHost::removeEveryRoot));
    }

    private SuiteHost() {
    }

    private static void removeEveryRoot() {
        ROOTS_TO_REMOVE_AT_EXIT.removeIf(SuiteHost::removeTree);
    }

    private static boolean removeTree(Path root) {
        try (Stream<Path> everything = Files.walk(root)) {
            for (Path one : everything.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(one);
            }
            return true;
        } catch (IOException | UncheckedIOException leftForTheSystem) {
            return false;
        }
    }

    static Bounds grantEverything() {
        Bounds bounds = Bounds.standard().withTimeLimit(LONGEST_A_STEP_MAY_RUN_ON_A_LOADED_MACHINE);
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
            ROOTS_TO_REMOVE_AT_EXIT.add(theRootOfThisRun);
            layOutTheFilesTheSuiteReads(theRootOfThisRun);
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
        PlatformUnderTest platform = PlatformUnderTest.current();
        HostPorts ports = platform.ports()
                .withFiles(filesStandingIn(theRootOfThisRun))
                .withEnvironment(new ProcessEnvironment())
                .withProcesses(platform.processes())
                .withImages(platform.images())
                .withNetwork(new JavaSocket(PlatformUnderTest.current().hostNames()))
                .withSecureTransport(new JavaSecureTransport())
                .withPause(new WakeablePause())
                .withCertificateAuthority(new JavaCertificateAuthority());
        Interpreter interpreter = Interpreter.createOn(ports, output, bounds);
        putTheApplicationDataDirectoryThereToo(interpreter, rebolPathOf(theRootOfThisRun) + "data/");
        try {
            putTheModulesTheSuiteImportsWhereImportLooks(theRootOfThisRun);
        } catch (IOException noModules) {
            throw new UncheckedIOException(noModules);
        }
        return interpreter;
    }

    private static boolean isWindows() {
        return File.separatorChar == WINDOWS_SEPARATOR;
    }

    private static FilePort filesStandingIn(Path root) {
        if (!isWindows()) {
            return FileSystemPort.rootFileSystem(root);
        }
        Map<String, FilePort> drives = new TreeMap<>();
        for (Path driveRoot : FileSystems.getDefault().getRootDirectories()) {
            drives.put(driveRoot.toString().substring(0, 1), FileSystemPort.rootFileSystem(driveRoot));
        }
        return new DriveLetterFiles(drives, rebolPathOf(root));
    }

    private static String rebolPathOf(Path root) {
        if (!isWindows()) {
            return "/";
        }
        String written = LocalFileConvention.WINDOWS.readLocalPath(realPathOf(root).toString()).orElseThrow();
        return written.endsWith("/") ? written : written + "/";
    }

    private static Path realPathOf(Path root) {
        try {
            return root.toRealPath();
        } catch (IOException notThere) {
            throw new UncheckedIOException(notThere);
        }
    }

    private static void putTheApplicationDataDirectoryThereToo(Interpreter interpreter, String dataDirectory) {
        String sayingSo = """
                system/options/data: %%%s
                make-dir/deep system/options/data
                set '~ system/options/data""".formatted(dataDirectory);
        interpreter.defineNewWordsIn(sayingSo);
        interpreter.run(sayingSo);
        interpreter.followApplicationDataDirectory();
    }

    private static void putTheModulesTheSuiteImportsWhereImportLooks(Path root)
            throws IOException {

        Path from = CarriedFixtures.directory("rebol-modules");
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

        Path from = CarriedFixtures.directory("rebol-suite");
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
        Path from = CarriedFixtures.directory("rebol-suite/units/files");
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
