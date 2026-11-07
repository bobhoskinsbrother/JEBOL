package org.jebol.extension.sqlite;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ASqliteInterpreter {

    private final Path directory;

    private final StringBuilder printed = new StringBuilder();

    private final Interpreter interpreter;

    ASqliteInterpreter(Path directory) {
        this.directory = directory;
        Bounds bounds = Bounds.standard().withoutTimeLimit().add(new SqliteOnTheJvm().provide());
        for (HostService service : HostService.values()) {
            if (service != HostService.ENVIRONMENT) {
                bounds = bounds.grant(service);
            }
        }
        this.interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                printed::append,
                bounds);
    }

    String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    String printedBy(String source) {
        printed.setLength(0);
        answerTo(source);
        return printed.toString();
    }

    List<String> runReference(String script, String... arguments) {
        try {
            Files.writeString(directory.resolve(script), readReference(script));
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
        StringBuilder args = new StringBuilder();
        for (String argument : arguments) {
            args.append('{').append(argument).append("} ");
        }
        return linesOf(printedBy("system/options/quiet: true system/options/args: [" + args + "] do %" + script));
    }

    Path directory() {
        return directory;
    }

    static String readReference(String name) {
        try (InputStream in = ASqliteInterpreter.class.getResourceAsStream("/reference/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("no reference file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static List<String> linesOf(String text) {
        return text.replace("\r\n", "\n").lines().toList();
    }
}
