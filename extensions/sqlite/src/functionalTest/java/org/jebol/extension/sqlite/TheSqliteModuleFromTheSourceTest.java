package org.jebol.extension.sqlite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteModuleFromTheSourceTest {

    private static final String CRASHED = "crashed";

    @TempDir
    Path directory;

    private static List<String> transcript(String name) {
        return ASqliteInterpreter.linesOf(ASqliteInterpreter.readReference(name));
    }

    private static List<String> withoutTimings(List<String> lines) {
        return lines.stream().map(line -> line.replaceAll("PROFILE: [0-9]+ns", "PROFILE: <n>ns")).toList();
    }

    @Test
    @DisplayName("every command answers as module-commands.txt records the C answering")
    void answersEveryCommandAsTheC() {
        assertThat(new ASqliteInterpreter(directory).runReference("module-commands.r3"))
                .containsExactlyElementsOf(transcript("module-commands.txt"));
    }

    @Test
    @DisplayName("files are made, read, shared and refused as module-files.txt records")
    void handlesFilesAsTheC() {
        assertThat(new ASqliteInterpreter(directory).runReference("module-files.r3"))
                .containsExactlyElementsOf(transcript("module-files.txt"));
    }

    @Test
    @DisplayName("trace prints what module-trace.txt records, timings aside")
    void tracesAsTheC() {
        assertThat(withoutTimings(new ASqliteInterpreter(directory).runReference("module-trace.r3")))
                .containsExactlyElementsOf(transcript("module-trace.txt"));
    }

    @Test
    @DisplayName("the sqlite: scheme answers as module-scheme.txt records")
    void schemeAnswersAsTheC() {
        assertThat(new ASqliteInterpreter(directory).runReference("module-scheme.r3"))
                .containsExactlyElementsOf(transcript("module-scheme.txt"));
    }

    @ParameterizedTest(name = "case {0}")
    @ValueSource(ints = {1, 2, 3, 4, 6, 7, 8, 9, 10})
    @DisplayName("each case the C survives answers as module-one-case.txt records")
    void answersEachCaseAsTheC(int index) {
        String expected = transcript("module-one-case.txt").get(index - 1);

        assertThat(new ASqliteInterpreter(directory).runReference("module-one-case.r3", String.valueOf(index)))
                .containsExactly(expected);
    }

    @Test
    @DisplayName("the cases JEBOL answers its own way are those the C crashed on or answered from another column")
    void listsTheCasesAnsweredDifferently() {
        List<String> lines = transcript("module-one-case.txt");

        assertThat(lines).hasSize(17);
        assertThat(List.of(5, 11, 12, 13, 16, 17))
                .allSatisfy(index -> assertThat(lines.get(index - 1)).endsWith(CRASHED));
        assertThat(lines.get(13)).endsWith("#{616263}]");
        assertThat(lines.get(14)).endsWith("[#{01} #{01}]");
    }
}
