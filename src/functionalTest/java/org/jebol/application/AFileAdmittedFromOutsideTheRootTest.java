package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.eval.LocalFileConvention;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AFileAdmittedFromOutsideTheRootTest {

    @TempDir
    Path machine;

    private static String asRebolNamesIt(Path hostFile) {
        return LocalFileConvention.forSeparator(File.separatorChar)
                .readLocalPath(hostFile.toAbsolutePath().toString()).orElseThrow();
    }

    private String answerTo(Path root, String admitted, String source) {
        FileSystemPort files = FileSystemPort.rootFileSystem(root);
        files.acceptFileFromOutsideRoot(admitted);
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(files),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a file admitted by the name Rebol writes for it is read by that name, on every platform")
    void readsAdmittedFileByItsRebolName() throws IOException {
        Path root = Files.createDirectories(machine.resolve("root"));
        Path outside = Files.write(machine.resolve("launcher.txt"), "started".getBytes(StandardCharsets.UTF_8));
        String rebolName = asRebolNamesIt(outside);

        assertThat(answerTo(root, rebolName, """
                {started} = read/string %""" + rebolName)).isEqualTo("#(true)");
    }
}
