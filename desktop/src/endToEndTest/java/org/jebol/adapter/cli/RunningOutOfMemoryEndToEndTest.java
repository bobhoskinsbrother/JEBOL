package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(300)
@Execution(ExecutionMode.CONCURRENT)
class RunningOutOfMemoryEndToEndTest {

    private static final String SMALL_HEAP = "-Xmx48m";
    private static final String QUICK_COLLECTOR = "-XX:+UseSerialGC";
    private static final int PROCESS_TIMEOUT_SECONDS = 240;
    private static final int LARGE_FILE_BYTES = 64 * 1024 * 1024;

    private record Ran(int exitStatus, String output) {
    }

    private Ran runInSmallHeap(Path directory, String script) throws IOException, InterruptedException {
        Path file = directory.resolve("script.r3");
        Files.writeString(file, script);
        Path output = directory.resolve("output.txt");
        Process process = new ProcessBuilder(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                SMALL_HEAP, QUICK_COLLECTOR,
                "-cp", System.getProperty("java.class.path"),
                Repl.class.getName(), file.toString()))
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("the script was still running after "
                    + PROCESS_TIMEOUT_SECONDS + " seconds");
        }
        return new Ran(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
    }

    private String catchTheErrorOf(String work) {
        return """
                Rebol []
                grown: none
                failure: try [%s]
                grown: none
                print either error? failure [join {caught: } failure/id] [{no failure}]
                print join {still working: } length? append copy [] 1
                """.formatted(work);
    }

    private void assertCaughtAndCarriedOn(Ran ran) {
        assertThat(ran.output())
                .doesNotContain("java.lang.OutOfMemoryError")
                .contains("caught: no-memory")
                .endsWith("still working: 1\n");
        assertThat(ran.exitStatus()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "appending to a block               | grown: copy [] forever [append grown 1]",
            "appending to a string              | grown: copy {} forever [append grown {abcdefghijklmnop}]",
            "appending to a binary              | grown: copy #{} forever [append grown #{0102030405060708}]",
            "appending a fresh block each time  | grown: copy [] forever [append/only grown copy [1 2 3 4]]",
            "joining a string to itself         | grown: {abcdefgh} forever [grown: join grown grown]",
            "rejoining a string with itself     | grown: {abcdefgh} forever [grown: rejoin [grown grown]]",
            "ajoining a string with itself      | grown: {abcdefgh} forever [grown: ajoin [grown grown {ab}]]",
            "copying a block onto itself        | grown: [1 2 3 4] forever [grown: append copy grown grown]",
            "inserting one string many times    | grown: insert/dup copy {} {abcdefgh} 100000000",
            "molding a growing structure        | grown: {ab} forever [grown: mold reduce [grown grown]]",
            "forming a growing structure        | grown: {ab} forever [grown: form reduce [grown grown]]",
            "filling a map                      | grown: make map! [] count: 0 forever [count: count + 1 grown/:count: count]",
            "expanding a tab past the heap      | grown: detab/size {^-a} 2000000000",
    })
    @DisplayName("running out of memory is a no-memory error the script catches, and it carries on afterwards")
    void catchesWhileGrowing(String what, String work, @TempDir Path directory) throws Exception {
        assertCaughtAndCarriedOn(runInSmallHeap(directory, catchTheErrorOf(work)));
    }

    @Test
    @DisplayName("reading a file larger than the heap is a no-memory error the script catches")
    void readsAFileLargerThanTheHeap(@TempDir Path directory) throws Exception {
        Path large = directory.resolve("large.bin");
        Files.write(large, new byte[LARGE_FILE_BYTES]);
        assertThat(Files.size(large)).isEqualTo(LARGE_FILE_BYTES);

        assertCaughtAndCarriedOn(runInSmallHeap(directory, catchTheErrorOf("grown: read %large.bin")));
    }

    @Test
    @DisplayName("running out of memory with nothing to catch it ends the script with REBOL's error, never a Java one")
    void uncaughtIsReportedAsRebolsError(@TempDir Path directory) throws Exception {
        Ran ran = runInSmallHeap(directory, """
                Rebol []
                grown: copy [] forever [append grown 1]
                """);

        assertThat(ran.output())
                .doesNotContain("java.lang.OutOfMemoryError")
                .doesNotContain("Exception in thread")
                .contains("not enough memory");
        assertThat(ran.exitStatus()).isNotZero();
    }
}
