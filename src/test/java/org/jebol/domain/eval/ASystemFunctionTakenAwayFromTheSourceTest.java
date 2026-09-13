package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ASystemFunctionTakenAwayFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id mold e/arg1]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a port cannot be made when MAKE-PORT* has been overwritten")
    void aPortCannotBeMadeWhenMakePortHasBeenOverwritten() {
        assertThat(failureOf("""
                system/contexts/sys/make-port*: 5
                make port! [scheme: 'file ref: %x]"""))
                .isEqualTo("""
                        [Internal bad-sys-func "5"]""");
    }

    @Test
    @DisplayName("and a module cannot be made when MAKE-MODULE* has been")
    void aModuleCannotBeMadeWhenMakeModuleHasBeenOverwritten() {
        assertThat(failureOf("""
                system/contexts/sys/make-module*: 5
                make module! [[title: "x"][]]"""))
                .isEqualTo("""
                        [Internal bad-sys-func "5"]""");
    }

    @Test
    @DisplayName("anything that is not a function does it, and the error names what was found")
    void anythingThatIsNotAFunctionDoesIt() {
        assertThat(failureOf("""
                system/contexts/sys/make-port*: none
                make port! [scheme: 'file ref: %x]"""))
                .isEqualTo("""
                        [Internal bad-sys-func "_"]""");
        assertThat(failureOf("""
                system/contexts/sys/make-port*: {no}
                make port! [scheme: 'file ref: %x]"""))
                .isEqualTo("""
                        [Internal bad-sys-func {"no"}]""");
        assertThat(failureOf("""
                unset in system/contexts/sys 'make-port*
                make port! [scheme: 'file ref: %x]"""))
                .isEqualTo("""
                        [Internal bad-sys-func "#(unset)"]""");
    }

    @Test
    @DisplayName("another function is accepted, because the check is that it is callable")
    void anotherFunctionIsAccepted() {
        assertThat(answerTo("""
                kept: :system/contexts/sys/make-port*
                system/contexts/sys/make-port*: func [spec] [kept spec]
                port? make port! [scheme: 'file ref: %x]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and an untouched interpreter makes its port as it always did")
    void anUntouchedInterpreterMakesItsPort() {
        assertThat(answerTo("""
                port? make port! [scheme: 'file ref: %x]""")).isEqualTo("#(true)");
    }
}
