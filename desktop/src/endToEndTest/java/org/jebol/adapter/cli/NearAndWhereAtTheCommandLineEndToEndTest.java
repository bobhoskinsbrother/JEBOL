package org.jebol.adapter.cli;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NearAndWhereAtTheCommandLineEndToEndTest {

    @TempDir
    Path directory;

    private String whatAStoppingScriptReports(String source) throws Exception {
        Path script = directory.resolve("stops.r3");
        Files.writeString(script, source);
        ByteArrayOutputStream reported = new ByteArrayOutputStream();
        Repl.runCommandLine(new String[] {script.toString()},
                InputStream.nullInputStream(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(reported, true, StandardCharsets.UTF_8),
                directory.toString());
        return reported.toString(StandardCharsets.UTF_8);
    }

    private static String session(String... linesTyped) {
        String typed = String.join("\n", List.of(linesTyped)) + "\nquit\n";
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Interpreter interpreter = AnInterpreter.createWithOutput(new StreamOutput(output));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("a file that stops")
    class AFileThatStops {

        @Test
        @DisplayName("prints the call that raised and the code around it, as R3 does")
        void printsTheWhereAndNearOfTheCallThatRaised() throws Exception {
            String reported = whatAStoppingScriptReports("""
                    Rebol []
                    compress {a} (quote brotli)
                    """);

            assertThat(reported).contains("""
                    ** Script error: invalid argument: brotli
                    ** Where: compress do either either if -apply-
                    ** Near: compress "a" (quote brotli""");
        }

        @Test
        @DisplayName("prints them for a refusal found inside a call's own body too")
        void printsThemForARaiseInsideABody() throws Exception {
            String reported = whatAStoppingScriptReports("""
                    Rebol []
                    z: func [] [1 / 0]
                    z
                    """);

            assertThat(reported).contains("""
                    ** Where: / z do either either if -apply-
                    ** Near: / 0""");
        }
    }

    @Nested
    @DisplayName("the console")
    class TheConsole {

        @Test
        @DisplayName("prints the raising call and the line around it")
        void printsTheWhereAndNearAtThePrompt() {
            assertThat(session("compress {a} quote brotli"))
                    .contains("""
                            ** Where: compress
                            ** Near: compress "a" quote brotli""");
        }

        @Test
        @DisplayName("and prints none for a refusal at the top of the line")
        void printsNothingForARefusalAtTheTop() {
            String transcript = session("mold/part {abc} 2.5");

            assertThat(transcript).contains("** Script error:");
            assertThat(transcript).doesNotContain("** Where:");
            assertThat(transcript).doesNotContain("** Near:");
        }
    }
}
