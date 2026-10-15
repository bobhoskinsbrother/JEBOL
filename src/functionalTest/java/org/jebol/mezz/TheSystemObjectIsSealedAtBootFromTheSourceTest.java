package org.jebol.mezz;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheSystemObjectIsSealedAtBootFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("PROTECT-SYSTEM has run and taken itself away")
    void protectSystemHasRunAndTakenItselfAway() {
        assertThat(answerTo("value? 'protect-system")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("the catalogue and the build are protected all the way down")
    void theCatalogueAndTheBuildAreProtected() {
        assertThat(answerTo("""
                reduce [protected? 'system/catalog  protected? 'system/build]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("so writing into either of them says locked-word")
    void writingIntoEitherSaysLockedWord() {
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
    void theTwoThingsItNamesAreStillWritable() {
        assertThat(answerTo("""
                collect [
                    foreach writing [
                        [system/options/quiet: true]
                        [append system/catalog/file-types [%.nothing nothing]]
                        [system/script: none]
                    ] [
                        keep either error? e: try writing [e/id] ['written]
                    ]
                ]""")).isEqualTo("[written written written]");
    }
}
