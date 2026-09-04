package org.jebol.domain.eval;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refusing to change a protected object, as a REBOL error rather than a
 * Java exception.
 *
 * <p>Specified in {@code spec/natives.allium} and confirmed against a real
 * R3. Three routes to the same refusal give two different errors, because
 * the error names the route: assigning through a name is {@code
 * locked-word}, changing the object as a container is {@code protected}.
 *
 * <p>All three threw {@code IllegalStateException} straight out of JEBOL,
 * which {@code spec/embed.allium} promises cannot happen. A host cannot
 * tell a script being refused from JEBOL having a bug if both arrive as a
 * throwable.
 */
class ProtectedObjectTest {

    private static final String PROTECTED = "p: make object! [a: 1] protect p ";

    private static String errorIdOf(String setup, String attempt) {
        String source = setup + "e: try [" + attempt + "] "
                + "either error? e [e/id] ['no-error]";
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("`%s` must arrive as an outcome, never as a host exception", attempt)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    @Test
    @DisplayName("SET through a word into a protected object raises locked-word")
    void settingThroughAWordRaisesLockedWord() {
        assertThat(errorIdOf(PROTECTED, "set in p 'a 99")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("assigning through a set-path raises locked-word")
    void assigningThroughASetPathRaisesLockedWord() {
        assertThat(errorIdOf(PROTECTED, "p/a: 5")).isEqualTo("locked-word");
    }

    @Test
    @DisplayName("APPEND onto a protected object raises protected")
    void appendingToAProtectedObjectRaisesProtected() {
        assertThat(errorIdOf(PROTECTED, "append p [a: 2]")).isEqualTo("protected");
    }

    @Test
    @DisplayName("the value is unchanged after each refusal")
    void nothingIsChangedByARefusedAssignment() {
        Interpreter interpreter = Interpreter.create();
        String source = PROTECTED
                + "try [set in p 'a 99] try [p/a: 5] try [append p [a: 2]] p/a";
        interpreter.defineFreshWordsIn(source);

        assertThat(interpreter.display(interpreter.run(source)))
                .as("a refused change must leave nothing half-done")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("an unprotected object still takes all three")
    void anUnprotectedObjectIsUnaffected() {
        String open = "p: make object! [a: 1] ";
        assertThat(errorIdOf(open, "set in p 'a 99")).isEqualTo("no-error");
        assertThat(errorIdOf(open, "p/a: 5")).isEqualTo("no-error");
        assertThat(errorIdOf(open, "append p [b: 2]")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("UNPROTECT lets the changes through again")
    void unprotectingRestoresTheObject() {
        assertThat(errorIdOf(PROTECTED + "unprotect p ", "p/a: 5"))
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("PUT onto a protected object raises protected, new word or old")
    void puttingIntoAProtectedObjectRaisesProtected() {
        assertThat(errorIdOf(PROTECTED, "put p to-set-word 'a 2")).isEqualTo("protected");
        assertThat(errorIdOf(PROTECTED, "put p to-set-word 'c 3")).isEqualTo("protected");
    }

    @Test
    @DisplayName("UNPROTECT/WORDS frees an assignment but not a PUT")
    void releasingTheWordsLeavesTheObjectClosed() {
        String released = "o: unprotect/words protect/deep o: object [a: 10] ";
        assertThat(errorIdOf(released, "o/a: 0")).isEqualTo("no-error");
        assertThat(errorIdOf(released, "put o to-set-word 'a 0")).isEqualTo("protected");
    }

    @Test
    @DisplayName("EXTEND is refused for the same reason, being written as a PUT")
    void extendingAProtectedObjectIsRefused() {
        assertThat(errorIdOf("o: unprotect/words protect/deep o: object [a: 10] ",
                "extend o 'c 3")).isEqualTo("protected");
    }

    @Test
    @DisplayName("PUT onto an open object still works")
    void puttingIntoAnOpenObjectIsUnaffected() {
        assertThat(errorIdOf("o: object [a: 1] ", "put o to-set-word 'c 3"))
                .isEqualTo("no-error");
    }
}
