package org.jebol.mezz;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which context each file of Rebol's own library defines its words in.
 *
 * <p>Read out of {@code Do_Global_Block} in {@code src/core/b-init.c}, which
 * is four lines and decides all of it:
 *
 * <pre>
 * Bind_Block(rebind &gt; 1 ? Sys_Context : Lib_Context, BLK_HEAD(block), BIND_SET);
 * if (rebind &lt; 0) Bind_Block(Sys_Context, BLK_HEAD(block), 0);
 * if (rebind &gt; 0) Bind_Block(Lib_Context, BLK_HEAD(block), BIND_DEEP);
 * if (rebind &gt; 1) Bind_Block(Sys_Context, BLK_HEAD(block), BIND_DEEP);
 * </pre>
 *
 * <p>The base files run with rebind 1 and the sys files with rebind 2, so a
 * base file adds its new words to the library and a sys file adds its to the
 * system internals. Both bind deep into the library, which is how a helper
 * calls a standard function.
 *
 * <p>JEBOL loaded every file into one context. That is not a smaller
 * interpreter, it is a wrong answer, and it cost three functions that this
 * class names one at a time. The failure is silent in every case: the word
 * still answers, it just answers something else.
 *
 * <p>Specified in {@code spec/load.allium} under "Loading Rebol's own
 * library".
 */
class LibraryFileContextTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("a private word of a module does not replace a library function")
    class ModuleFilesKeepTheirOwnNames {

        @Test
        @DisplayName("EXP is the exponent function, not a parse rule")
        void expIsAFunction() {
            assertThat(answerTo("any-function? :exp")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = exp 0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("STACK is not a block either, and it came from the same file")
        void stackIsAFunction() {
            assertThat(answerTo("block? :stack")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("but what the codec exports does reach the library")
        void theCodecsExportsReachTheLibrary() {
            assertThat(answerTo("any-function? :to-json")).isEqualTo(TRUE);
            assertThat(answerTo("any-function? :load-json")).isEqualTo(TRUE);
        }

    }

    @Nested
    @DisplayName("a sys file defines into the system internals")
    class SystemFilesAreSeparate {

        @Test
        @DisplayName("DECODE-URL is a function, not none")
        void decodeUrlIsAFunction() {
            assertThat(answerTo("any-function? :decode-url")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is the url-parser's own method, which is where Rebol builds it")
        void decodeUrlIsTheUrlParsersMethod() {
            assertThat(answerTo("same? :decode-url :sys/url-parser/parse-url"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the sys context is not the library context")
        void sysIsItsOwnContext() {
            assertThat(answerTo("same? system/contexts/sys system/contexts/lib"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("the library still holds the standard functions")
        void libHoldsTheStandardFunctions() {
            assertThat(answerTo("any-function? get in system/contexts/lib 'append"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a sys file's own helper is in sys and not in the library")
        void aSysHelperStaysInSys() {
            assertThat(answerTo("any-function? get in system/contexts/sys 'make-module*"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? in system/contexts/lib 'make-module*"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the three contexts sysobj.reb names")
    class TheContexts {

        @Test
        @DisplayName("lib, sys and user are all published")
        void allThreeArePublished() {
            assertThat(answerTo("object? system/contexts/lib")).isEqualTo(TRUE);
            assertThat(answerTo("object? system/contexts/sys")).isEqualTo(TRUE);
            assertThat(answerTo("object? system/contexts/user")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the user context is not the library either")
        void userIsItsOwnContext() {
            assertThat(answerTo("same? system/contexts/user system/contexts/lib"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("the header prototype exists, because MAKE-MODULE* builds on it")
        void theHeaderPrototypeExists() {
            assertThat(answerTo("object? system/standard/header")).isEqualTo(TRUE);
            assertThat(answerTo("0.0.0 = system/standard/header/version"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it has every field MAKE-MODULE* reads")
        void theHeaderPrototypeHasTheFieldsThatAreRead() {
            for (String field : new String[] {
                    "version", "title", "name", "type", "date", "file",
                    "author", "needs", "options", "checksum"}) {
                assertThat(answerTo("true? find words-of system/standard/header '" + field))
                        .as("system/standard/header wants a " + field + " field")
                        .isEqualTo(TRUE);
            }
        }
    }

    @Nested
    @DisplayName("what the fix buys: mezz-logger.reb")
    class TheLoggerArrives {

        @Test
        @DisplayName("all five log functions arrived")
        void theLoggerFunctionsArrived() {
            for (String name : new String[] {
                    "log-error", "log-warn", "log-info", "log-debug", "log-trace"}) {
                assertThat(answerTo("any-function? :" + name))
                        .as(name + " is one of mezz-logger.reb's five exports")
                        .isEqualTo(TRUE);
            }
        }

        @Test
        @DisplayName("and its private words did not come with them")
        void theLoggersPrivateWordsStayedPrivate() {
            assertThat(answerTo("none? in system/contexts/lib 'verbosity"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? in system/contexts/lib 'log-levels"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a lower-case type field says module just as well as an upper-case one")
        void theTypeFieldIsCaseInsensitive() {
            assertThat(answerTo("any-function? :log-info")).isEqualTo(TRUE);
            assertThat(answerTo("any-function? :to-json")).isEqualTo(TRUE);
        }
    }
}
