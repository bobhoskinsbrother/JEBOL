package org.jebol.application;

import org.jebol.adapter.host.JavaProcesses;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentWritingFromTheSourceTest {

    private static Interpreter reaching() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.ENVIRONMENT));
        interpreter.useEnvironment(new ProcessEnvironment());
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerTo(String source) {
        return answerTo(reaching(), source);
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a name may be written as a string or as a word, either way round")
    void aNameMayBeWrittenEitherWay() {
        assertThat(answerTo("""
                set-env "JB_A" "one"
                set-env 'JB_B "two"
                reduce [get-env "JB_A" get-env 'JB_A get-env "JB_B" get-env 'JB_B]"""))
                .isEqualTo("[\"one\" \"one\" \"two\" \"two\"]");
    }

    @Test
    @DisplayName("a name that is neither a string nor a word is refused")
    void aNameThatIsNeitherIsRefused() {
        assertThat(errorIdFrom("get-env 42")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a name nothing holds answers none, and so does the empty name")
    void aNameNothingHoldsAnswersNone() {
        assertThat(answerTo("""
                reduce [get-env "JB_NEVER_SET_AT_ALL" get-env ""]"""))
                .isEqualTo("[_ _]");
    }

    @Test
    @DisplayName("setting a name changes what GET-ENV and LIST-ENV report")
    void settingANameChangesWhatIsReported() {
        assertThat(answerTo("""
                answered: set-env "JB_C" "three"
                reduce [answered get-env "JB_C" select list-env "JB_C"]"""))
                .isEqualTo("[\"three\" \"three\" \"three\"]");
    }

    @Test
    @DisplayName("setting the same name twice keeps the second value")
    void settingANameTwiceKeepsTheSecond() {
        assertThat(answerTo("""
                set-env "JB_D" "first"
                set-env "JB_D" "second"
                get-env "JB_D\"""")).isEqualTo("\"second\"");
    }

    @Test
    @DisplayName("an empty value is a value, not a way of removing the name")
    void anEmptyValueIsStillAValue() {
        assertThat(answerTo("""
                set-env "JB_E" ""
                reduce [get-env "JB_E" true? find/only to block! list-env "JB_E"]"""))
                .isEqualTo("[\"\" #(true)]");
    }

    @Test
    @DisplayName("setting a name to none takes it away again")
    void settingANameToNoneRemovesIt() {
        assertThat(answerTo("""
                set-env "JB_F" "here"
                set-env "JB_F" none
                reduce [get-env "JB_F" true? select list-env "JB_F"]"""))
                .isEqualTo("[_ #(false)]");
    }

    @Test
    @DisplayName("removing a name that was never there is quiet")
    void removingANameThatWasNeverThere() {
        assertThat(answerTo("set-env \"JB_NEVER_EITHER\" none")).isEqualTo("_");
    }

    @Test
    @DisplayName("a value that is neither text nor nothing is refused")
    void aValueThatIsNeitherTextNorNothingIsRefused() {
        assertThat(answerTo("""
                reduce [
                    either error? e: try [set-env "JB_G" 42] [e/id] ['no-error]
                    either error? e: try [set-env "JB_G" [a b]] [e/id] ['no-error]
                    either error? e: try [set-env "JB_G" 'word] [e/id] ['no-error]
                ]"""))
                .isEqualTo("[expect-arg expect-arg expect-arg]");
    }

    @Test
    @DisplayName("setting over a name the host had shadows it, and putting it back restores it")
    void settingOverAHostNameShadowsIt() {
        assertThat(answerTo("""
                was: get-env "HOME"
                set-env "HOME" "/somewhere-else"
                now: get-env "HOME"
                set-env "HOME" was
                reduce [now = "/somewhere-else" (get-env "HOME") = was]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a program started afterwards inherits what was set")
    void aStartedProgramInheritsIt() {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .granting(HostService.ENVIRONMENT).granting(HostService.PROCESSES));
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useProcesses(new JavaProcesses());
        assertThat(answerTo(interpreter, """
                set-env "JB_CHILD" "inherited"
                seen: copy ""
                call/shell/wait/output {printf '%s' "$JB_CHILD"} seen
                seen"""))
                .isEqualTo("\"inherited\"");
    }

    @Test
    @DisplayName("without the environment grant, all three refuse")
    void withoutTheGrantItRefuses() {
        Interpreter without = Interpreter.withBounds(Bounds.standard());
        without.useEnvironment(new ProcessEnvironment());
        assertThat(answerTo(without,
                "e: try [set-env \"JB_H\" \"x\"] either error? e [e/id] ['no-error]"))
                .isEqualTo("no-service");
        assertThat(answerTo(without,
                "e: try [get-env \"HOME\"] either error? e [e/id] ['no-error]"))
                .isEqualTo("no-service");
        assertThat(answerTo(without,
                "e: try [list-env] either error? e [e/id] ['no-error]"))
                .isEqualTo("no-service");
    }
}
