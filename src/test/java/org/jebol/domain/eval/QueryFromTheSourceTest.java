package org.jebol.domain.eval;

import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QUERY, read out of {@code Ret_Query_File} and {@code Set_File_Mode_Value} in
 * {@code src/core/p-file.c}.
 *
 * <p>Written from the C and not from the Java beside it. QUERY is the function
 * the rest of the file library is built on: {@code size?} and
 * {@code modified?} are one line each over it, and {@code list-dir} and
 * {@code dir-tree} both ask it for three fields at once. Getting it wrong
 * would be wrong in five places.
 *
 * <p>The shape of the second argument decides the shape of the answer, and
 * the block form is the one nobody guesses. A plain word in the block puts
 * itself in the answer as a set-word before its value; a get-word contributes
 * the value alone. So {@code query %a [type size]} and
 * {@code query %a [:type :size]} are two different answers to the same
 * question, and Rebol's own {@code list-dir} depends on the second.
 */
class QueryFromTheSourceTest {

    @TempDir
    private Path directory;

    /** An interpreter granted the filesystem, rooted at the test's directory. */
    private Interpreter reachingTheFilesystem() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private String answerTo(String source) {
        Interpreter interpreter = reachingTheFilesystem();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private void givenAFile(String name, String contents) throws Exception {
        Files.writeString(directory.resolve(name), contents);
    }

    private static final String TRUE = "#(true)";
    private static final String NO_ERROR = "no-error";

    @Nested
    @DisplayName("Set_File_Mode_Value: the seven field names")
    class Fields {

        @Test
        @DisplayName("size is the byte count, and none for a directory")
        void theSizeField() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("query %five.txt 'size")).isEqualTo("5");
            Files.createDirectory(directory.resolve("sub"));
            assertThat(answerTo("none? query %sub/ 'size")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("type is the word dir or the word file, never a logic")
        void theTypeField() throws Exception {
            givenAFile("a.txt", "x");
            Files.createDirectory(directory.resolve("sub"));
            assertThat(answerTo("'file = query %a.txt 'type")).isEqualTo(TRUE);
            assertThat(answerTo("'dir = query %sub/ 'type")).isEqualTo(TRUE);
            assertThat(answerTo("word? query %a.txt 'type")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("date and modified are the same fact under two names")
        void theDateAndModifiedFields() throws Exception {
            givenAFile("a.txt", "x");
            assertThat(answerTo("date? query %a.txt 'modified")).isEqualTo(TRUE);
            assertThat(answerTo("date? query %a.txt 'date")).isEqualTo(TRUE);
            assertThat(answerTo("(query %a.txt 'date) = query %a.txt 'modified"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("name is the path as REBOL spells it")
        void theNameField() throws Exception {
            givenAFile("a.txt", "x");
            assertThat(answerTo("file? query %a.txt 'name")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("accessed and created are dates, or none where the host cannot say")
        void theTimestampFields() throws Exception {
            givenAFile("a.txt", "x");
            assertThat(answerTo("any [date? query %a.txt 'accessed "
                    + "none? query %a.txt 'accessed]")).isEqualTo(TRUE);
            assertThat(answerTo("any [date? query %a.txt 'created "
                    + "none? query %a.txt 'created]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a field name that is not one of the seven raises invalid-arg")
        void anUnknownFieldRaises() throws Exception {
            givenAFile("a.txt", "x");
            assertThat(errorIdOf("query %a.txt 'colour")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("query %a.txt 'sizes")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("Ret_Query_File: three shapes of question, three shapes of answer")
    class Shapes {

        @Test
        @DisplayName("a word asks for one fact and answers it bare")
        void aWordAnswersTheFactAlone() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("query %five.txt 'size")).isEqualTo("5");
            assertThat(answerTo("integer? query %five.txt 'size")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a block of plain words labels each value with a set-word")
        void aBlockOfPlainWordsIsLabelled() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("mold query %five.txt [type size]"))
                    .isEqualTo("\"[type: file size: 5]\"");
            assertThat(answerTo("block? query %five.txt [type size]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a block of get-words gives the values alone")
        void aBlockOfGetWordsIsBare() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("mold query %five.txt [:type :size]"))
                    .isEqualTo("\"[file 5]\"");
        }

        @Test
        @DisplayName("the two may be mixed in one block")
        void aMixedBlockLabelsOnlyThePlainWords() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("mold query %five.txt [type :size]"))
                    .isEqualTo("\"[type: file 5]\"");
        }

        @Test
        @DisplayName("the block form is what list-dir asks, so it must answer by position")
        void theFormListDirDependsOn() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("info: query %five.txt [:name :size :date] "
                    + "all [file? info/1 5 = info/2 date? info/3]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("none asks for everything and answers an object")
        void noneAnswersAnObject() throws Exception {
            givenAFile("five.txt", "12345");
            assertThat(answerTo("object? query %five.txt none")).isEqualTo(TRUE);
            assertThat(answerTo("info: query %five.txt none 5 = info/size")).isEqualTo(TRUE);
            assertThat(answerTo("info: query %five.txt none 'file = info/type"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("info: query %five.txt none "
                    + "info/date = info/modified")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("something in the block that is not a word at all raises")
        void aNonWordInTheBlockRaises() throws Exception {
            givenAFile("a.txt", "x");
            assertThat(errorIdOf("query %a.txt [1]")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("query %a.txt [\"size\"]")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("the boundary: a missing file, and a service not granted")
    class TheBoundary {

        @Test
        @DisplayName("a file that is not there answers none rather than raising")
        void aMissingFileAnswersNone() {
            assertThat(answerTo("none? query %nowhere.txt 'size")).isEqualTo(TRUE);
            assertThat(answerTo("none? query %nowhere.txt none")).isEqualTo(TRUE);
            assertThat(errorIdOf("query %nowhere.txt 'size")).isEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("/MODE is declared, deprecated and does nothing")
        void theDeprecatedModeRefinement() throws Exception {
            givenAFile("a.txt", "hello");
            assertThat(answerTo("query/mode %a.txt 'size"))
                    .isEqualTo(answerTo("query %a.txt 'size"))
                    .isEqualTo("5");
        }

        @Test
        @DisplayName("a script not granted the filesystem cannot ask at all")
        void anUngrantedScriptIsRefused() {
            Interpreter walled = Interpreter.create();
            String source = "e: try [query %a.txt 'size] either error? e [e/id] ['no-error]";
            walled.defineFreshWordsIn(source);
            assertThat(walled.display(walled.run(source))).isNotEqualTo("no-error");
        }
    }

}
