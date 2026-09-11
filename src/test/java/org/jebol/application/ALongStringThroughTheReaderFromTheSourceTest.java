package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A string longer than the reader's own buffer, saved and read back.
 *
 * <p>Rebol's own lexer test calls this "NULLs inside loaded string", and its
 * comment says what the shape is for: "using CALL as it could be reproduced
 * only when the internal buffer is being extended during load". The C scans
 * into a fixed buffer and grows it when a token runs past the end, and the
 * defect it is guarding against left the grown half full of zero bytes -- so
 * a forty thousand character string came back the right length and wrong from
 * the middle onwards.
 *
 * <p>It shells out to a second interpreter because Rebol's buffer is global
 * and one that had already grown would not show the fault. JEBOL's reader
 * holds no such buffer, so a fresh interpreter here is enough, and the
 * assertions are the ones the shelled-out script makes: no NUL anywhere in
 * what came back, and the text the same as what went out.
 *
 * <p>Forty thousand is Rebol's own figure and is the boundary: it is well past
 * the buffer's starting size, so the growth happens at least once.
 */
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

    /**
     * The end is where a buffer that grew and did not copy shows: the front of
     * the string is right whatever happened, and the last characters are the
     * ones that came out of the new half.
     */
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
