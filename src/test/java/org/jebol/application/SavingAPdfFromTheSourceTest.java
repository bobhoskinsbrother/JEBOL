package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

class SavingAPdfFromTheSourceTest {

    private static final Path THE_VENDORED_FIXTURE =
            Path.of("src", "test", "resources", "pdf", "hello-linearized.pdf");

    private static Interpreter reaching(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.FILES)
                .granting(HostService.CLOCK));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Path directory, String source) {
        Interpreter interpreter = reaching(directory);
        String whole = "try [import 'pdf]\n" + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static final String A_DOCUMENT_WHOSE_OBJECT_NUMBERS_HAVE_GAPS = """
            gappy: object [
                trailer: #[Root: 2x0]
                objects: #[
                    2x0 #[Type: Catalog Pages: 5x0]
                    5x0 #[Type: Pages Kids: [9x0] Count: 1]
                    9x0 #[
                        Type: Page
                        Parent: 5x0
                        MediaBox: [0 0 612 792]
                        Contents: 12x0
                        Resources: #[]
                    ]
                    12x0 #(object! [data: ""])
                ]
            ]
            """;

    @Test
    @DisplayName("the codec is there to be imported")
    void theCodecIsThereToBeImported(@TempDir Path directory) {
        assertThat(answerTo(directory, "true? find codecs 'pdf")).isEqualTo("#(true)");
    }

    @Test
    @Timeout(30)
    @DisplayName("a document whose object numbers have gaps in them is written and read back")
    void aDocumentWhoseObjectNumbersHaveGapsIsWrittenAndReadBack(@TempDir Path directory) {
        assertThat(answerTo(directory, A_DOCUMENT_WHOSE_OBJECT_NUMBERS_HAVE_GAPS + """
                save %gappy.pdf gappy
                again: load %gappy.pdf
                (sort keys-of gappy/objects) = (sort keys-of again/objects)"""))
                .isEqualTo("#(true)");
    }

    @Test
    @Timeout(30)
    @DisplayName("and the cross-reference table it writes names every one of them")
    void theCrossReferenceTableNamesEveryOneOfThem(@TempDir Path directory) {
        assertThat(answerTo(directory, A_DOCUMENT_WHOSE_OBJECT_NUMBERS_HAVE_GAPS + """
                save %gappy.pdf gappy
                written: read %gappy.pdf
                collect [
                    foreach run ["2 1" "5 1" "9 1" "12 1"] [
                        keep true? find written to binary! run
                    ]
                ]"""))
                .isEqualTo("[#(true) #(true) #(true) #(true)]");
    }

    @Test
    @Timeout(60)
    @DisplayName("a real linearized document loads with every object it names")
    void aRealLinearizedDocumentLoadsWithEveryObjectItNames(@TempDir Path directory)
            throws IOException {

        putTheFixtureIn(directory);

        assertThat(answerTo(directory, """
                doc: load %hello-linearized.pdf
                sort keys-of doc/objects"""))
                .isEqualTo("[1x0 4x0 5x0 6x0 7x0 8x0 9x0 11x0 12x0 13x0 14x0 15x0]");
    }

    @Test
    @Timeout(60)
    @DisplayName("its information dictionary carries the dates the object stream held")
    void itsInformationDictionaryCarriesTheDatesTheObjectStreamHeld(@TempDir Path directory)
            throws IOException {

        putTheFixtureIn(directory);

        assertThat(answerTo(directory, """
                doc: load %hello-linearized.pdf
                info: doc/objects/6x0
                reduce [info/CreationDate  info/CreationDate/zone]"""))
                .isEqualTo("[13-Sep-2021/10:28:42+2:00 2:00]");
    }

    @Test
    @Timeout(60)
    @DisplayName("and it is written back out with the same objects in it")
    void itIsWrittenBackOutWithTheSameObjects(@TempDir Path directory) throws IOException {
        putTheFixtureIn(directory);

        assertThat(answerTo(directory, """
                first-reading: load %hello-linearized.pdf
                save %again.pdf first-reading
                second-reading: load %again.pdf
                (sort keys-of first-reading/objects)
                    = (sort keys-of second-reading/objects)"""))
                .isEqualTo("#(true)");
    }

    private static void putTheFixtureIn(Path directory) throws IOException {
        Files.copy(THE_VENDORED_FIXTURE,
                directory.resolve("hello-linearized.pdf"),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
