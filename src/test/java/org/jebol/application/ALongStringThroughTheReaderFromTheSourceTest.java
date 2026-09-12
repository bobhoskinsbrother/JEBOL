package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ALongStringThroughTheReaderFromTheSourceTest {

    private static String answerTo(Path directory, String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String SAVED_AND_READ_BACK = """
            data: make string! 40000
            insert/dup data {ABCD} 10000
            save %tmp.data reduce [1 data]
            back: transcode sys/read-decode %tmp.data none
            """;

    @Test
    @DisplayName("comes back whole, with no NUL anywhere in it")
    void itcomesBackWhole(@TempDir Path directory) {
        assertThat(answerTo(directory, SAVED_AND_READ_BACK + """
                reduce [
                    length? back
                    back/1
                    string? back/2
                    length? back/2
                    none? find back/2 #"^@"
                    back/2 == data
                ]"""))
                .isEqualTo("[2 1 #(true) 40000 #(true) #(true)]");
    }

    @Test
    @DisplayName("and its last four characters are the ones that went in")
    void itsendIsIntact(@TempDir Path directory) {
        assertThat(answerTo(directory, SAVED_AND_READ_BACK + """
                copy/part skip back/2 39996 4"""))
                .isEqualTo("\"ABCD\"");
    }

    @Test
    @DisplayName("a string either side of the same size reads back whole too")
    void eitherSideOfTheSameSize(@TempDir Path directory) {
        for (String howMany : new String[] {"9999", "10001"}) {
            assertThat(answerTo(directory, """
                    data: copy {}
                    insert/dup data {ABCD} %s
                    save %%tmp.data reduce [1 data]
                    back: transcode sys/read-decode %%tmp.data none
                    reduce [length? back/2 none? find back/2 #"^@" back/2 == data]"""
                    .formatted(howMany)))
                    .as(howMany + " repetitions")
                    .isEqualTo("[" + (Integer.parseInt(howMany) * 4)
                            + " #(true) #(true)]");
        }
    }
}
