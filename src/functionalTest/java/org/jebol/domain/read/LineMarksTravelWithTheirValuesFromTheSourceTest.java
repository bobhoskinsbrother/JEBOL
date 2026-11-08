package org.jebol.domain.read;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LineMarksTravelWithTheirValuesFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static final String FALSE = "#(false)";

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerWithFilesUnder(Path root, String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(root)),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("what LOAD and TRANSCODE read keeps its marks however it was read")
    class Reading {

        @Test
        @DisplayName("a binary source is marked as a string source is")
        void markABinarySource() {
            assertThat(answerTo("""
                    new-line? next transcode to binary! {a^/b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("LOAD/ALL marks what it reads")
        void markUnderAll() {
            assertThat(answerTo("""
                    equal? mold load/all {a^/b} {[a^/b]}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the line feed that ends a header is skipped, so the first value of the body is unmarked")
        void skipTheHeadersOwnLineFeed() {
            assertThat(answerTo("""
                    equal? mold load {Rebol []^/a^/b} {[a^/b]}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a second blank line after a header does mark the first value")
        void markAfterABlankLineBelowTheHeader() {
            assertThat(answerTo("""
                    new-line? load {Rebol []^/^/a^/b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("LOAD/HEADER puts the header in front, and each mark stays with its value")
        void keepTheMarksBehindTheHeader() {
            assertThat(answerTo("""
                    object? first load/header {Rebol []^/a^/b}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    new-line? next load/header {Rebol []^/a^/b}""")).isEqualTo(FALSE);
            assertThat(answerTo("""
                    new-line? next next load/header {Rebol []^/a^/b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a script read from a file keeps the lines it was written on")
        void markWhatIsLoadedFromAFile(@TempDir Path root) {
            assertThat(answerWithFilesUnder(root, """
                    write %script.r3 {Rebol []^/x: 1^/y: 2^/}
                    new-line? find load %script.r3 quote y:""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a mark belongs to its value, so it moves when the block changes")
    class MarksMoveWithTheirValues {

        @Test
        @DisplayName("INSERT at the head pushes every mark along by one")
        void moveMarksAlongOnInsert() {
            assertThat(answerTo("""
                    b: transcode {^/a^/b} insert b 'x
                    reduce [new-line? b new-line? next b new-line? next next b]""")).isEqualTo("""
                    [#(false) #(true) #(true)]""");
        }

        @Test
        @DisplayName("REMOVE takes the removed value's mark with it and pulls the rest back")
        void moveMarksBackOnRemove() {
            assertThat(answerTo("""
                    b: transcode {x^/a b} remove b
                    reduce [new-line? b new-line? next b]""")).isEqualTo("""
                    [#(true) #(false)]""");
        }

        @Test
        @DisplayName("TRIM/ALL keeps the marks of what it keeps, and drops the marks of the nones")
        void keepTheMarksOfWhatTrimKeeps() {
            assertThat(answerTo("""
                    b: transcode {_^/a^/_ b _} trim/all b
                    reduce [length? b new-line? b new-line? next b]""")).isEqualTo("""
                    [2 #(true) #(false)]""");
        }

        @Test
        @DisplayName("TRIM from the head only leaves the middle where it was")
        void keepTheMiddleOnTrimHead() {
            assertThat(answerTo("""
                    b: transcode {_^/a^/_ b _} trim/head b
                    reduce [length? b new-line? b new-line? next b]""")).isEqualTo("""
                    [4 #(true) #(true)]""");
        }

        @Test
        @DisplayName("TRIM refuses a protected block even when there is nothing to trim")
        void refuseToTrimAProtectedBlock() {
            assertThat(answerTo("""
                    e: try [trim protect []] e/id""")).isEqualTo("protected");
        }

        @Test
        @DisplayName("REMOVE-EACH keeps the marks of what it keeps")
        void keepTheMarksOfWhatRemoveEachKeeps() {
            assertThat(answerTo("""
                    b: transcode {1^/2 3^/4} remove-each n b [odd? n]
                    reduce [length? b new-line? b new-line? next b]""")).isEqualTo("""
                    [2 #(true) #(true)]""");
        }
    }

    @Nested
    @DisplayName("a mark is in the value's cell, so it goes wherever the value goes")
    class TheMarkTravels {

        @Test
        @DisplayName("through a variable into a new block")
        void throughAVariable() {
            assertThat(answerTo("""
                    b: [a
                    b] x: second b c: reduce [x]
                    new-line? c""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("through an argument and out as a function's result")
        void throughAFunction() {
            assertThat(answerTo("""
                    b: [a
                    b] f: func [v] [v] c: reduce [f second b]
                    new-line? c""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("into APPEND, INSERT and COMPOSE")
        void intoWhatBuildsABlock() {
            assertThat(answerTo("""
                    b: [a
                    b] x: second b
                    reduce [
                        new-line? append copy [] x
                        new-line? head insert copy [] x
                        new-line? compose [(x)]
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("an integer, a string, a block and a none carry it too")
        void whateverTheDatatype() {
            assertThat(answerTo("""
                    reduce [
                        new-line? reduce [second [1
                            2]]
                        new-line? reduce [second ["s"
                            "t"]]
                        new-line? reduce [second [[1]
                            [2]]]
                        new-line? reduce [second [#(none)
                            #(none)]]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("POKE puts the new value's mark there: an unmarked value takes the mark away, a marked one brings it")
        void pokePutsTheNewCellsMark() {
            assertThat(answerTo("""
                    c: [p
                    q] poke c 2 'z
                    d: [p q] poke d 1 second [a
                    b]
                    reduce [new-line? next c new-line? d]""")).isEqualTo("[#(false) #(true)]");
        }

        @Test
        @DisplayName("a value made afresh has no mark: TO WORD!, arithmetic, an evaluated lit-word")
        void notOnAFreshValue() {
            assertThat(answerTo("""
                    b: [a
                    b] x: second b
                    reduce [
                        new-line? reduce [to word! x]
                        new-line? reduce [
                            1 + 2]
                        new-line? next reduce ['a
                            'b]
                    ]""")).isEqualTo("[#(false) #(false) #(false)]");
        }

        @Test
        @DisplayName("NEW-LINE on one block leaves the same value in another as it was")
        void newLineChangesOnlyItsOwnBlock() {
            assertThat(answerTo("""
                    b: [a b] c: reduce [second b] new-line c true
                    d: [p
                    q] e: reduce [second d] new-line e false
                    reduce [new-line? next b new-line? c new-line? next d new-line? e]"""))
                    .isEqualTo("[#(false) #(true) #(true) #(false)]");
        }

        @Test
        @DisplayName("a map moved to a marked position is still the same map")
        void aMapIsStillTheSameMap() {
            assertThat(answerTo("""
                    m: make map! [k 1] c: reduce [1 m] new-line next c true
                    reduce [same? m second c new-line? next c]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("a bitset and a word stay the same value at a marked position")
        void stayTheSameValue() {
            assertThat(answerTo("""
                    s: charset "a" c: reduce [1 s] new-line next c true
                    w: 'x d: reduce [1 w] new-line next d true
                    reduce [same? s second c same? w second d]""")).isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("each probe in line-marks/probes.r3 answers as ./r3-head did")
    class TheProbes {

        @TestFactory
        Stream<DynamicTest> answerAsRebolDid() throws IOException {
            List<String> recorded = readResource("/line-marks/probes.txt").lines().toList();
            StringBuilder printed = new StringBuilder();
            Interpreter interpreter = Interpreter.createOn(
                    AnInterpreter.standardPorts(), printed::append, Bounds.standard());
            String probes = readResource("/line-marks/probes.r3");
            interpreter.defineNewWordsIn(probes);
            interpreter.run(probes);
            List<String> answered = printed.toString().lines().toList();
            return recorded.stream().map(line -> DynamicTest.dynamicTest(line.substring(0, line.indexOf(" |")), () ->
                    assertThat(answered).contains(line)));
        }

        private String readResource(String named) throws IOException {
            try (InputStream carried = getClass().getResourceAsStream(named)) {
                assertThat(carried).as("%s is not on the test classpath", named).isNotNull();
                return new String(carried.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
