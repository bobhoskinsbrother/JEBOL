package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.UnsetValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandsFromTheSourceTest {

    private static final int THE_LARGEST_COMMAND_INDEX = 65_535;

    private Interpreter makeAnInterpreterWithADemo() {
        Extension demo = new Extension("demo", "1.0.0", List.of(
                new ExtensionCommand("twice", "value [integer!]",
                        call -> IntegerValue.of(((IntegerValue) call.arguments().getFirst()).magnitude() * 2)),
                new ExtensionCommand("fall-over", "", call -> {
                    throw new IllegalStateException("fell over");
                }),
                new ExtensionCommand("nothing", "", call -> UnsetValue.unset())));
        return Interpreter.withBounds(Bounds.standard().add(demo));
    }

    private String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String readTheRefusalIn(Interpreter interpreter, String source) {
        return answerTo(interpreter, """
                e: try [%s]
                either error? e [reduce [e/id e/arg1 e/arg2]] ['no-error]""".formatted(source));
    }

    @Nested
    @DisplayName("MAKE COMMAND! checks its definition as the C's Make_Command does")
    class MakingACommand {

        @Test
        @DisplayName("a spec and a body is bad-func-def naming the definition, as r3 answers")
        void refusesASpecAndABody() {
            assertThat(readTheRefusalIn(Interpreter.create(), "make command! [[x] [x]]"))
                    .isEqualTo("[bad-func-def [[x] [x]] _]");
        }

        @Test
        @DisplayName("TO COMMAND! answers the same")
        void answersTheSameThroughTo() {
            assertThat(readTheRefusalIn(Interpreter.create(), "to command! [[x] [x]]"))
                    .isEqualTo("[bad-func-def [[x] [x]] _]");
        }

        @Test
        @DisplayName("two items, and four, are bad-func-def")
        void refusesTheWrongNumberOfItems() {
            Interpreter interpreter = Interpreter.create();

            assertThat(readTheRefusalIn(interpreter, "make command! [[x] 1]")).isEqualTo("[bad-func-def [[x] 1] _]");
            assertThat(readTheRefusalIn(interpreter, "make command! reduce [[x] make object! [a: 1] 1 2]"))
                    .startsWith("[bad-func-def");
        }

        @Test
        @DisplayName("an object whose first field is not a dispatcher is bad-func-def")
        void refusesAnObjectWithoutADispatcher() {
            assertThat(readTheRefusalIn(Interpreter.create(), "make command! reduce [[x] make object! [a: 1] 1]"))
                    .startsWith("[bad-func-def");
        }

        @ParameterizedTest
        @ValueSource(strings = {"[]", "[[x]]", "[1 2]", "[1 [x]]", "[a: 1]"})
        @DisplayName("a block that does not open with a spec and one more item is bad-make-arg, as Make_Function refuses it first")
        void refusesABlockWithoutASpecFirst(String definition) {
            assertThat(readTheRefusalIn(Interpreter.create(), "make command! " + definition))
                    .isEqualTo("[bad-make-arg #(command!) " + definition + "]");
        }

        @Test
        @DisplayName("TO COMMAND! refuses it the same way")
        void refusesABlockWithoutASpecFirstThroughTo() {
            assertThat(readTheRefusalIn(Interpreter.create(), "to command! [1 2]"))
                    .isEqualTo("[bad-make-arg #(command!) [1 2]]");
        }

        @Test
        @DisplayName("a spec and three more items is bad-func-def, as Make_Command wants exactly three")
        void refusesASpecAndThreeMore() {
            assertThat(readTheRefusalIn(Interpreter.create(), "make command! [[x] 1 2 3]"))
                    .isEqualTo("[bad-func-def [[x] 1 2 3] _]");
        }

        @Test
        @DisplayName("a number that is not a block is bad-make-arg, as for any datatype")
        void refusesANumber() {
            assertThat(readTheRefusalIn(Interpreter.create(), "make command! 5")).isEqualTo("[bad-make-arg #(command!) 5]");
        }

        @Test
        @DisplayName("an installed extension's module makes a command, up to index 65535")
        void makesACommandAtTheLargestIndex() {
            Interpreter interpreter = makeAnInterpreterWithADemo();

            assertThat(answerTo(interpreter, "type? make command! reduce [[x] select system/modules 'demo "
                    + THE_LARGEST_COMMAND_INDEX + "]")).isEqualTo("#(command!)");
        }

        @Test
        @DisplayName("and one past it is bad-func-def")
        void refusesOnePastTheLargestIndex() {
            assertThat(readTheRefusalIn(makeAnInterpreterWithADemo(), "make command! reduce [[x] select system/modules 'demo "
                    + (THE_LARGEST_COMMAND_INDEX + 1) + "]")).startsWith("[bad-func-def");
        }

        @Test
        @DisplayName("a command made by hand calls the extension's command at that index, counting from nought as COMMAND does")
        void callsTheExtensionFromACommandMadeByHand() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), "again: make command! reduce [[value [integer!]] select system/modules 'demo 0] again 4"))
                    .isEqualTo("8");
        }

        @Test
        @DisplayName("a negative index is made, as the C checks only the upper end, and is command-fail when called")
        void failsACommandAtANegativeIndex() {
            assertThat(readTheRefusalIn(makeAnInterpreterWithADemo(), "below: make command! reduce [[] select system/modules 'demo -1] below"))
                    .startsWith("[command-fail");
        }

        @Test
        @DisplayName("an index the extension has no command for is command-fail when called")
        void failsAnIndexWithNoCommand() {
            assertThat(readTheRefusalIn(makeAnInterpreterWithADemo(), "none-such: make command! reduce [[] select system/modules 'demo 99] none-such"))
                    .startsWith("[command-fail");
        }
    }

    @Nested
    @DisplayName("LOAD-EXTENSION")
    class LoadExtension {

        @Test
        @DisplayName("a file of native code is no-extension, never portable")
        void refusesAFileAsNeverPortable() {
            assertThat(readTheRefusalIn(Interpreter.create(), "load-extension %camera.so"))
                    .isEqualTo("[no-extension %camera.so \"never portable\"]");
        }

        @Test
        @DisplayName("SECURE's extension policy is checked first")
        void secureComesFirst(@TempDir Path root) {
            Interpreter interpreter = Interpreter.withBounds(Bounds.standard().grant(HostService.FILES));
            interpreter.useFileSystem(FileSystemPort.rootFileSystem(root));
            answerTo(interpreter, "secure [extension throw]");

            assertThat(readTheRefusalIn(interpreter, "load-extension %camera.so")).isEqualTo("[security %camera.so _]");
        }

        @Test
        @DisplayName("a binary without /DISPATCH is invalid-arg, as the C's Trap_Arg makes it")
        void refusesABinaryWithoutDispatch() {
            assertThat(readTheRefusalIn(Interpreter.create(), "load-extension #{00}")).isEqualTo("[invalid-arg #{00} _]");
        }

        @Test
        @DisplayName("a string is expect-arg, as r3 answers")
        void refusesAString() {
            assertThat(readTheRefusalIn(Interpreter.create(), "load-extension {x}")).startsWith("[expect-arg load-extension name");
        }

        @Test
        @DisplayName("/DISPATCH answers an extension object holding the source and a fresh extension handle")
        void dispatchAnswersAnExtensionObject() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), """
                    m: select system/modules 'demo
                    ext: load-extension/dispatch to binary! {REBOL [name: again]} m/lib-base
                    reduce [words-of ext  ext/lib-boot  mold ext/lib-base  same? ext/lib-base m/lib-base]"""))
                    .isEqualTo("""
                            [[lib-base lib-file lib-boot command cmd-index words] #{5245424F4C205B6E616D653A20616761696E5D} "#(handle! extension)" #(false)]""");
        }

        @Test
        @DisplayName("and a command made from it reaches the same dispatcher")
        void reachesTheDispatcherFromACommandMadeFromIt() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), """
                    ext: load-extension/dispatch #{00} get in select system/modules 'demo 'lib-base
                    again: make command! reduce [[value [integer!]] ext 0]
                    again 5"""))
                    .isEqualTo("10");
        }

        @Test
        @DisplayName("/DISPATCH takes any handle, as the C does, and answers the object")
        void dispatchTakesAnyHandle() {
            assertThat(answerTo(Interpreter.create(), "object? load-extension/dispatch #{00} rc4/key #{0102}"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but a command made from an extension with no dispatcher is bad-extension")
        void refusesACommandWithoutADispatcher() {
            assertThat(readTheRefusalIn(Interpreter.create(), """
                    ext: load-extension/dispatch #{00} rc4/key #{0102}
                    make command! reduce [[] ext 0]""")).startsWith("[bad-extension");
        }
    }

    @Nested
    @DisplayName("DO-COMMANDS evaluates commands the C's quick way")
    class DoCommands {

        @Test
        @DisplayName("set-words take answers, and the last answer is the result")
        void setWordsAndTheLastAnswer() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), "do-commands [a: twice 2 twice a]")).isEqualTo("8");
        }

        @Test
        @DisplayName("a path argument is fetched")
        void fetchesAPath() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), "holder: make object! [n: 5] do-commands [twice holder/n]")).isEqualTo("10");
        }

        @Test
        @DisplayName("a paren argument is evaluated")
        void evaluatesAParen() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), "do-commands [twice (1 + 2)]")).isEqualTo("6");
        }

        @Test
        @DisplayName("a word that is not a command is expect-val naming command!: as a set-word and the word, as r3 answers")
        void refusesAWordThatIsNotACommand() {
            assertThat(readTheRefusalIn(makeAnInterpreterWithADemo(), "do-commands [print 1]")).isEqualTo("[expect-val command!: print]");
        }

        @Test
        @DisplayName("a wrong datatype is expect-arg")
        void refusesAWrongDatatype() {
            assertThat(readTheRefusalIn(makeAnInterpreterWithADemo(), "do-commands [twice {x}]")).startsWith("[expect-arg twice");
        }

        @Test
        @DisplayName("a missing argument is no-arg")
        void refusesAMissingArgument() {
            assertThat(readTheRefusalIn(makeAnInterpreterWithADemo(), "do-commands [twice]")).startsWith("[no-arg twice");
        }

        @Test
        @DisplayName("a command that fails answers unset rather than raising, as Do_Commands does")
        void answersUnsetForAFailingCommand() {
            assertThat(answerTo(makeAnInterpreterWithADemo(), "unset? do-commands [fall-over]")).isEqualTo("#(true)");
        }
    }
}
