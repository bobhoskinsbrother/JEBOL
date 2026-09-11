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

/**
 * The one definition of the interpreter Rebol's suite runs in.
 *
 * <p>It exists because there were three, and they disagreed. {@code
 * RebolSuiteTest} is the gate; {@code SuiteStops} says where a file stops and
 * {@code SweepRunner} diffs a file against a real Rebol, and both were written
 * by copying the gate's setup. Each granted every {@link HostService} and
 * rooted a filesystem, and neither installed the environment or the process
 * runner -- and granting a service is not providing one.
 *
 * <p>So both tools reported stops the gate never sees. Every one reading "given
 * no environment to read" or "given no way to start a program" was the tool's
 * own doing, and four entries in {@code goals.md} were written from them:
 * a goal to make the environment work that nothing was waiting on, a
 * dependency on it that did not exist, three stops in {@code port-test.r3} that
 * do not happen, and a shared CALL blocker that was not shared and was not a
 * blocker.
 *
 * <p>Copying it a fourth time would be the same mistake, so the tools now call
 * this. A capability added here reaches all three at once, which is the only
 * arrangement in which a measuring tool cannot drift from the thing it
 * measures.
 */
final class SuiteHost {

    private SuiteHost() {
    }

    /** Everything granted, which is what a suite file may ask for. */
    static Bounds grantingEverything() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.granting(service);
        }
        return bounds;
    }

    /**
     * Gives an interpreter the host a suite file expects.
     *
     * <p>Files are confined to a directory made for the run, so a test that
     * writes one cannot reach anything the build did not make. The environment
     * and the processes are the real ones: a suite file asks for {@code PWD}
     * and shells out to the boot image, and answering "not granted" to either
     * is a wrong answer rather than a safe one.
     */
    static Interpreter installOn(Interpreter interpreter) {
        try {
            Path root = Files.createTempDirectory("jebol-suite");
            layOutTheFilesTheSuiteReads(root);
            interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        } catch (IOException noDirectory) {
            throw new UncheckedIOException(noDirectory);
        }
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useProcesses(new JavaProcesses());
        interpreter.useImages(new JavaImages());
        interpreter.useNetwork(new JavaSockets());
        putHomeInsideTheDirectoryTheRunCanReach(interpreter);
        putTheApplicationDataDirectoryThereToo(interpreter);
        return interpreter;
    }

    /**
     * Says that home is the directory made for the run, because otherwise it
     * is a place this interpreter cannot touch.
     *
     * <p>The filesystem above is confined to a temporary directory, and
     * {@code system/options/home} is read from the machine rather than from
     * that filesystem -- so a suite file asking where home is got an answer it
     * was then refused permission to write. REBOL's own SAFE tests do exactly
     * that: {@code set-user} keeps a user's storage file at
     * {@code system/options/home}, and every assertion after it was lost to a
     * path the run was never allowed to reach.
     *
     * <p>A sandbox whose home lies outside the sandbox is an incoherent host,
     * not a strict one. Which of the two answers is right belongs to whoever
     * installs the filesystem, and that is here.
     */
    private static void putHomeInsideTheDirectoryTheRunCanReach(
            Interpreter interpreter) {

        String sayingSo = "system/options/home: %./";
        interpreter.defineFreshWordsIn(sayingSo);
        interpreter.run(sayingSo);
    }

    /**
     * Says where an application keeps its own files, and makes the directory.
     *
     * <p>The same incoherence as the home directory above, one field along.
     * {@code system/options/data} names a hidden folder in the operator's home
     * -- where modules, caches and a REPL history go -- and the run is confined
     * to a temporary directory that cannot reach it. Rebol's own boot makes
     * that folder before anything asks for it; nothing here can, because this
     * interpreter has no filesystem until the line above gives it one.
     *
     * <p>The word {@code ~} is the shortcut for it and is bound while the
     * library loads, long before any of this, so moving the field alone leaves
     * {@code cd ~} pointing at the old place. Both are set, which is what
     * {@code mezz-tail.reb} does in one line.
     */
    private static void putTheApplicationDataDirectoryThereToo(Interpreter interpreter) {
        String sayingSo = """
                system/options/data: %/data/
                make-dir/deep system/options/data
                set '~ system/options/data""";
        interpreter.defineFreshWordsIn(sayingSo);
        interpreter.run(sayingSo);
    }

    /**
     * Puts every vendored data file where the tests look for it.
     *
     * <p>Named individually once, six of them, while seventy-two sat in the
     * repository. Every test that read one of the other sixty-six answered
     * {@code cannot-open} and took the rest of its block with it -- 191
     * assertions that were never run and read as failures of the port. Copying
     * the directory means a file that arrives is a file the tests can find,
     * without anybody remembering to add a line.
     *
     * <p>The whole tree, not the top of it. One of Rebol's data directories
     * holds a directory of its own -- fourteen icons the ICO codec builds an
     * icon file out of and the ZIP codec archives whole -- and listing only
     * the top left it behind, so six assertions asked for a directory that was
     * not there. A copy that stops at the first level is a copy that quietly
     * depends on nobody ever nesting anything.
     */
    private static void layOutTheFilesTheSuiteReads(Path root) throws IOException {
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
