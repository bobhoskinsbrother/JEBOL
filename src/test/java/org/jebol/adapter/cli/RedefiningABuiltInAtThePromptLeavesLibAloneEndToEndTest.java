package org.jebol.adapter.cli;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedefiningABuiltInAtThePromptLeavesLibAloneEndToEndTest {

    private String aSessionTyping(String... linesTyped) {
        String typed = String.join("\n", List.of(linesTyped)) + "\nquit\n";
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Interpreter interpreter = Interpreter.writingTo(new StreamOutput(output));
        new Repl(interpreter, new BufferedReader(new StringReader(typed)), output).run();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("add: func at the prompt answers 99 for the user, while lib/add still adds")
    void theUsersAddShadowsLibs() {
        assertThat(aSessionTyping(
                "add: func [a b][99]",
                "reduce [add 1 2  lib/add 1 2  same? :add :lib/add]"))
                .contains("== [99 3 #(false)]");
    }

    @Test
    @DisplayName("an operator built on lib's add keeps adding after the user's add is replaced")
    void lisOperatorsKeepWorking() {
        assertThat(aSessionTyping(
                "add: func [a b][99]",
                "1 + 2"))
                .contains("== 3");
    }

    @Test
    @DisplayName("append: func at the prompt leaves lib/append appending")
    void theUsersAppendShadowsLibs() {
        assertThat(aSessionTyping(
                "append: func [s v][42]",
                "reduce [append [] 1  lib/append [] 1]"))
                .contains("== [42 [1]]");
    }

    @Test
    @DisplayName("a built-in not redefined is still reached at the prompt as it always was")
    void anUntouchedBuiltInStillWorks() {
        assertThat(aSessionTyping("add 1 2")).contains("== 3");
    }

    @Test
    @DisplayName("a word lib does not know starts unset at the prompt, as before")
    void aFreshWordStartsUnset() {
        assertThat(aSessionTyping("value? 'never-defined-anywhere")).contains("== #(false)");
    }

    @Test
    @DisplayName("redefining across two submissions keeps the user's version and lib's apart")
    void acrossTwoSubmissions() {
        assertThat(aSessionTyping(
                "add: func [a b][99]",
                "add: func [a b][77]",
                "reduce [add 1 2  lib/add 1 2]"))
                .contains("== [77 3]");
    }

    @Test
    @DisplayName("setting lib/add on purpose still changes lib, which is what the path says")
    void settingLibOnPurposeStillWorks() {
        assertThat(aSessionTyping(
                "lib/add: func [a b][55]",
                "lib/add 1 2"))
                .contains("== 55");
    }
}
