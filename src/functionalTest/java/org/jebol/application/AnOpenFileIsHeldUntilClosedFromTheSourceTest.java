package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnOpenFileIsHeldUntilClosedFromTheSourceTest {

    private final AHostThatLocksOpenFiles files = new AHostThatLocksOpenFiles(new MemoryFiles());

    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts().withFiles(files),
            OutputPort.discard(),
            Bounds.standard().grant(HostService.FILES));

    private String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a file opened is held, so a host that keeps open files where they are refuses to delete it, as R3 on Windows does")
    void refusesToDeleteAnOpenFile() {
        assertThat(answerTo("""
                write %held.txt {a}
                p: open %held.txt
                e: try [delete %held.txt]
                reduce [error? e e/id exists? %held.txt]""")).isEqualTo("[#(true) no-delete file]");
    }

    @Test
    @DisplayName("and once it is closed the file is let go, and DELETE answers the port")
    void deletesTheFileOnceClosed() {
        assertThat(answerTo("""
                write %held.txt {a}
                p: open %held.txt
                close p
                reduce [port? delete %held.txt none? exists? %held.txt]""")).isEqualTo("[#(true) #(true)]");
        assertThat(files.countHeld()).isZero();
    }

    @Test
    @DisplayName("a folder holding an open file cannot be deleted either, until the file is closed")
    void refusesToDeleteAFolderHoldingAnOpenFile() {
        assertThat(answerTo("""
                make-dir %folder/
                p: open/write %folder/file
                e: delete-dir %folder/
                refused: reduce [error? e e/id exists? %folder/]
                close p
                reduce [refused error? try [delete-dir %folder/] none? exists? %folder/]"""))
                .isEqualTo("[[#(true) no-delete dir] #(false) #(true)]");
    }

    @Test
    @DisplayName("reading a file that was never opened holds nothing afterwards")
    void readingWithoutOpeningHoldsNothing() {
        answerTo("""
                write %once.txt {a}
                read %once.txt""");
        assertThat(files.countHeld()).isZero();
    }

    @Test
    @DisplayName("opening the same file twice holds it twice, and each CLOSE lets one go")
    void eachOpenIsLetGoByItsOwnClose() {
        answerTo("""
                write %twice.txt {a}
                p: open %twice.txt
                q: open %twice.txt
                close p""");
        assertThat(files.countHeld()).isEqualTo(1);
        answerTo("close q");
        assertThat(files.countHeld()).isZero();
    }
}
