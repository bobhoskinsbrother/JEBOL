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

class OutputAtTheConsoleEndToEndTest {

    private String readWhatTheConsolePrints(String typed) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Interpreter interpreter = Interpreter.createWithOutput(new StreamOutput(output));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a path and a paren are printed as they stand, and a block is reduced and formed")
    void printsWhatFormGives() {
        assertThat(readWhatTheConsolePrints("""
                print 'a/b
                print quote (1 + 2)
                print [quote (1 2) [3] "x"]
                prin 'a/b prin "|" print ""
                """)).isEqualTo("""
                >> a/b
                >> 1 + 2
                >> 1 2 3 x
                >> a/b|
                >>\s""");
    }

    @Test
    @DisplayName("form drops a paren's parens, mold/part takes the largest integer, and quit/return none carries unset")
    void formsMoldsAndQuitsAsRebolDoes() {
        assertThat(readWhatTheConsolePrints("""
                form [1 () (2 3)]
                mold/part "abc" 9223372036854775807
                mold/part "abc" -9223372036854775808
                type? catch/quit [quit/return none]
                """)).isEqualTo("""
                >> == "1  2 3"

                >> == {"abc"}

                >> == ""

                >> == #(unset!)

                >>\s""");
    }

    @Test
    @DisplayName("a wrong limit, an unknown word in a printed block, and make-error are refused as r3 refuses them")
    void refusesAsRebolDoes() {
        assertThat(readWhatTheConsolePrints("""
                mold/part "abc" 2.5
                print [no-such-word-here]
                make-error 1 2
                """)).isEqualTo("""
                >> \n\
                ** Script error: mold does not allow #(decimal!) for its limit argument

                >> \n\
                ** Script error: no-such-word-here has no value
                ** Where: print
                ** Near: print [no-such-word-here]

                >> \n\
                ** Script error: make-error has no value

                >>\s""");
    }
}
