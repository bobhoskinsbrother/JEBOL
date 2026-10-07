package org.jebol.adapter.cli;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ParseAtTheConsoleEndToEndTest {

    private String whatTheConsolePrintsAfterItsBanner(String typed) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(output));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();
        String printed = captured.toString(StandardCharsets.UTF_8);
        return printed.substring(printed.indexOf('\n') + 1);
    }

    @Test
    @DisplayName("a match, a case-minded miss, and each refusal print as a real 3.22.5 console printed them")
    void printsWhatRebolPrinted() {
        assertThat(whatTheConsolePrintsAfterItsBanner("""
                parse "abc" ["abc"]
                parse/case "ABC" ["abc"]
                parse/All "a" []
                parse "a" "a"
                parse 1 ["a"]
                f: func [/a] [1] f/a/X
                """)).isEqualTo("""
                >> == #(true)

                >> == #(false)

                >> \n\
                ** Script error: parse has no refinement called All

                >> \n\
                ** Script error: parse does not allow #(string!) for its rules argument

                >> \n\
                ** Script error: parse does not allow #(integer!) for its input argument

                >> \n\
                ** Script error: f has no refinement called X

                >>\s""");
    }
}
