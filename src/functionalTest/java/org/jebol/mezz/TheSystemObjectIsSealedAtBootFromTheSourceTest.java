package org.jebol.mezz;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheSystemObjectIsSealedAtBootFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("PROTECT-SYSTEM has run and taken itself away")
    void protectSystemHasRunAndTakenItselfAway() {
        assertThat(answerTo("value? 'protect-system")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("the catalogue and the build are protected all the way down")
    void protectsTheCatalogueAndTheBuild() {
        assertThat(answerTo("""
                reduce [protected? 'system/catalog  protected? 'system/build]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("so writing into either of them says locked-word")
    void saysLockedWordOnWritingIntoEither() {
        assertThat(answerTo("""
                collect [
                    foreach writing [
                        [system/catalog: none]
                        [system/catalog/errors: none]
                        [system/catalog/errors/Math: none]
                        [system/build: none]
                    ] [
                        keep either error? e: try writing [e/id] ['accepted]
                    ]
                ]""")).isEqualTo("[locked-word locked-word locked-word locked-word]");
    }

    @Test
    @DisplayName("and the two things PROTECT-SYSTEM names are still writable")
    void leavesTheTwoThingsItNamesWritable() {
        assertThat(answerTo("""
                collect [
                    foreach writing [
                        [system/options/quiet: true]
                        [append system/catalog/file-types [%.nothing nothing]]
                        [system/script: none]
                    ] [
                        keep either error? e: try writing [e/id] ['text]
                    ]
                ]""")).isEqualTo("[text text text]");
    }
}
