package org.jebol.application;

import org.jebol.domain.eval.ConsolePort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The port datatype, far enough for Rebol's own INPUT and ASK to run.
 *
 * <p>A port is an object whose datatype sends an action to its actor. Reading a
 * console port and reading a file port are the same call to READ and two
 * different actors, which is how one verb reaches every kind of thing a script
 * can open.
 *
 * <p>The work is split the way Rebol splits it, and the split is the point.
 * MAKE-PORT* and MAKE-SCHEME are REBOL, in {@code sys-ports.reb}, and JEBOL
 * loads them. OPEN, SET-SCHEME, the actor and the datatype are the host's, in
 * Java. So OPEN calls back into the loaded library to build the port, exactly
 * as Rebol's C does: {@code Make_Port} is four lines and one of them is
 * {@code Do_Sys_Func(SYS_CTX_MAKE_PORT_P, spec, 0)}.
 */
class PortDatatypeTest {

    /** A console that answers the lines a test gives it. */
    private static final class Lines implements ConsolePort {

        private final java.util.Iterator<String> waiting;

        Lines(String... lines) {
            this.waiting = java.util.List.of(lines).iterator();
        }

        @Override
        public String readLine() {
            return waiting.hasNext() ? waiting.next() : null;
        }

        @Override
        public String readHiddenLine() {
            return readLine();
        }
    }

    private static Interpreter withAConsole(String... lines) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.CONSOLE));
        interpreter.useConsole(new Lines(lines));
        return interpreter;
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Interpreter interpreter, String source) {
        return answerFrom(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("opening and closing")
    class Lifecycle {

        @Test
        @DisplayName("OPEN on a console spec answers a port")
        void openAnswersAPort() {
            assertThat(answerFrom(withAConsole(), "port? open [scheme: 'console]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a port that was opened is open, and a closed one is not")
        void openAndClosed() {
            assertThat(answerFrom(withAConsole(), "open? open [scheme: 'console]"))
                    .isEqualTo(TRUE);
            assertThat(answerFrom(withAConsole(),
                    "p: open [scheme: 'console] close p open? p")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("CLOSE answers the port, so a caller keeps hold of it")
        void closeAnswersThePort() {
            assertThat(answerFrom(withAConsole(),
                    "p: open [scheme: 'console] port? close p")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a port molds as an object, which is what types.reb says it is")
        void aPortMoldsAsAnObject() {
            assertThat(answerFrom(withAConsole(),
                    "find mold open [scheme: 'console] \"scheme\"")).isNotEqualTo("_");
        }

        @Test
        @DisplayName("/ALLOW takes a block of attributes that nothing reads")
        void theAllowBlockIsDeclaredAndUnread() {
            assertThat(answerFrom(withAConsole(),
                    "open? open/allow [scheme: 'console] [read]")).isEqualTo(TRUE);
            assertThat(errorIdFrom(withAConsole(),
                    "open/allow [scheme: 'console] 'read")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("the port carries the scheme it was opened from")
        void thePortKnowsItsScheme() {
            assertThat(answerFrom(withAConsole(),
                    "p: open [scheme: 'console] 'console = p/scheme/name")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the scheme registry")
    class Schemes {

        @Test
        @DisplayName("the console scheme is registered, through Rebol's own MAKE-SCHEME")
        void theConsoleSchemeIsRegistered() {
            assertThat(answerFrom(withAConsole(),
                    "find mold words-of system/schemes \"console\"")).isNotEqualTo("_");
        }

        @Test
        @DisplayName("a scheme nothing can serve is refused rather than opened")
        void anUnservedSchemeIsRefused() {
            assertThat(errorIdFrom(withAConsole(), "open [scheme: 'carrier-pigeon]"))
                    .isNotEqualTo("no-error");
        }

        /**
         * Opening one carries nothing and so asks for nothing.
         *
         * <p>All an unopened console will answer is how wide a terminal is,
         * and it answers eighty whether or not there is a terminal there. The
         * grant guards the two things that move data, and those are the two
         * checked below. Refusing to open it refused HELP, which asks the
         * width on its first line, to every interpreter that had not been
         * handed a console -- and R3 has this port open from boot.
         */
        @Test
        @DisplayName("a console port opens without the grant, carrying nothing")
        void withoutTheGrantOpeningIsAllowed() {
            Interpreter walled = Interpreter.create();
            assertThat(errorIdFrom(walled, "open [scheme: 'console]"))
                    .isEqualTo("no-error");
            assertThat(answerFrom(walled, "query system/ports/output 'window-cols"))
                    .isEqualTo("80");
        }

        @Test
        @DisplayName("but reading and writing through it are still refused")
        void withoutTheGrantReadingAndWritingAreRefused() {
            Interpreter walled = Interpreter.create();
            assertThat(errorIdFrom(walled, "read system/ports/output"))
                    .isEqualTo("no-service");
            assertThat(errorIdFrom(walled, """
                    write system/ports/output {x}"""))
                    .isEqualTo("no-service");
            assertThat(errorIdFrom(walled, "input")).isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("reading through the actor")
    class Reading {

        @Test
        @DisplayName("READ on a port answers a line")
        void readAnswersALine() {
            assertThat(answerFrom(withAConsole("hello"),
                    "p: open [scheme: 'console] read p")).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("nothing more to read answers none, not an empty string")
        void nothingMoreAnswersNone() {
            assertThat(answerFrom(withAConsole(),
                    "p: open [scheme: 'console] none? read p")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("READ still reads a file, because the verb dispatches on the datatype")
        void readStillTakesAFile() {
            assertThat(errorIdFrom(withAConsole(), "read %nowhere.txt"))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("MODIFY sets a mode and answers the value it was given")
        void modifySetsAMode() {
            assertThat(answerFrom(withAConsole(),
                    "p: open [scheme: 'console] modify p 'line true")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MODIFY refuses a mode a console has not got")
        void modifyRefusesAnUnknownMode() {
            assertThat(errorIdFrom(withAConsole(),
                    "p: open [scheme: 'console] modify p 'colour true"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and refuses a value that is not a logic")
        void modifyRefusesANonLogic() {
            assertThat(errorIdFrom(withAConsole(),
                    "p: open [scheme: 'console] modify p 'line 9"))
                    .isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("what this was for: Rebol's own INPUT and ASK")
    class WhatItUnblocks {

        @Test
        @DisplayName("INPUT is Rebol's, and it reads through the port")
        void inputIsRebols() {
            assertThat(answerFrom(withAConsole("typed"), "input")).isEqualTo("\"typed\"");
        }

        @Test
        @DisplayName("INPUT keeps the port in system/ports/input, so the next call reuses it")
        void inputKeepsThePort() {
            assertThat(answerFrom(withAConsole("one", "two"),
                    "input port? system/ports/input")).isEqualTo(TRUE);
            assertThat(answerFrom(withAConsole("one", "two"),
                    "mold reduce [input input]")).isEqualTo("{[\"one\" \"two\"]}");
        }

        @Test
        @DisplayName("nothing more to read answers none")
        void inputAnswersNoneAtTheEnd() {
            assertThat(answerFrom(withAConsole(), "none? input")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("ASK writes the question and then reads")
        void askIsRebols() {
            assertThat(answerFrom(withAConsole("yes"), "ask \"go? \"")).isEqualTo("\"yes\"");
        }

        @Test
        @DisplayName("without the console grant both are refused")
        void bothNeedTheGrant() {
            assertThat(errorIdFrom(Interpreter.create(), "input")).isEqualTo("no-service");
            assertThat(errorIdFrom(Interpreter.create(), "ask \"q\"")).isEqualTo("no-service");
        }
    }
}
