package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QueryingAFilesDatesFromTheSourceTest {

    @TempDir
    Path directory;

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                OutputPort.discard(),
                AnInterpreter.stretchForThePlatform(Bounds.standard().grant(HostService.FILES)));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("modified, created and accessed are each a date, and stay as they were when nothing touches the file")
    void staysStillUntouched() {
        assertThat(answerTo("""
                write %dates "test"
                fields: [:modified :created :accessed]
                before: query %dates fields
                wait 1
                after: query %dates fields
                reduce [date? before/1 date? before/2 date? before/3 before = after]""")).isEqualTo(
                "[#(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("an append moves modified on and leaves created where it was, as file-test asserts")
    void anAppendMovesOnlyModified() {
        assertThat(answerTo("""
                write %dates "test"
                fields: [:modified :created :accessed]
                before: query %dates fields
                wait 1
                write/append %dates "!"
                after: query %dates fields
                reduce [before/1 < after/1 before/2 = after/2]""")).isEqualTo("[#(true) #(true)]");
    }
}
