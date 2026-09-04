package org.jebol.suite;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.OutputPort;
import org.jebol.domain.host.HostService;

/**
 * Runs a script the way the suite harness does, for the differential sweep.
 *
 * <p>The REPL grants one service and the harness grants all of them, so a
 * sweep run through the REPL reports every clock, filesystem and environment
 * question as a refusal and buries whatever the real difference was. This
 * grants what the harness grants and roots the filesystem where the suite's
 * own files are, so the only thing left between the two runs is the port.
 */
public final class SweepRunner {

    private SweepRunner() {
    }

    public static void main(String[] argued) throws Exception {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.granting(service);
        }
        Interpreter interpreter = Interpreter.writingTo(new OutputPort() {
            @Override
            public void write(String text) {
                System.out.print(text);
            }

            @Override
            public void writeLine(String text) {
                System.out.println(text);
            }
        }, bounds);
        Path root = Files.createTempDirectory("jebol-sweep");
        Path into = root.resolve("units").resolve("files");
        Files.createDirectories(into);
        Path from = Path.of("src", "test", "resources", "rebol-suite", "units", "files");
        if (Files.isDirectory(from)) {
            try (var each = Files.list(from)) {
                for (Path one : each.toList()) {
                    Files.copy(one, into.resolve(one.getFileName().toString()));
                }
            }
        }
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        String source = Files.readString(Path.of(argued[0]));
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
    }
}
