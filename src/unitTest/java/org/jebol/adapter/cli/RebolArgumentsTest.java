package org.jebol.adapter.cli;

import org.jebol.adapter.cli.RebolArguments.Flag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RebolArgumentsTest {

    private RebolArguments asked(String... words) {
        return new RebolArguments(List.of(words));
    }

    private EnumSet<Flag> flagsNamed(String spelled) {
        EnumSet<Flag> named = EnumSet.noneOf(Flag.class);
        if (spelled.isBlank()) {
            return named;
        }
        Arrays.stream(spelled.split(" "))
                .map(spelling -> Arrays.stream(Flag.values())
                        .filter(flag -> flag.spelling().equals(spelling))
                        .findFirst().orElseThrow())
                .forEach(named::add);
        return named;
    }

    @Nested
    @DisplayName("the script and what follows it")
    class TheScript {

        @Test
        @DisplayName("no words ask for nothing")
        void noWordsAskForNothing() {
            RebolArguments nothing = asked();

            assertThat(nothing.flags()).isEmpty();
            assertThat(nothing.script()).isEmpty();
            assertThat(nothing.argumentsForTheScript()).isEmpty();
        }

        @Test
        @DisplayName("the first word that is not an option is the script")
        void theFirstPlainWordIsTheScript() {
            assertThat(asked("s.r3").script()).contains("s.r3");
        }

        @Test
        @DisplayName("an empty word is a script with an empty name, as r3 reads it")
        void anEmptyWordIsAScript() {
            assertThat(asked("").script()).contains("");
        }

        @Test
        @DisplayName("every word after the script is its own, options included")
        void everythingAfterTheScriptIsAnArgument() {
            RebolArguments line = asked("-q", "s.r3", "--do", "x", "-v");

            assertThat(line.script()).contains("s.r3");
            assertThat(line.argumentsForTheScript()).containsExactly("--do", "x", "-v");
            assertThat(line.flags()).containsExactly(Flag.QUIET);
        }

        @Test
        @DisplayName("-- ends the options and names no script")
        void aDoubleDashEndsTheOptions() {
            RebolArguments line = asked("--", "s.r3", "x");

            assertThat(line.script()).isEmpty();
            assertThat(line.argumentsForTheScript()).containsExactly("s.r3", "x");
        }

        @Test
        @DisplayName("-- after the script is one of its arguments")
        void aDoubleDashAfterTheScriptIsAnArgument() {
            assertThat(asked("s.r3", "--", "x").argumentsForTheScript())
                    .containsExactly("--", "x");
        }

        @Test
        @DisplayName("--script names the script, and the next plain word is then its first argument")
        void anExplicitScriptLeavesThePlainWordToTheArguments() {
            RebolArguments line = asked("--script", "s.r3", "g", "h");

            assertThat(line.script()).contains("s.r3");
            assertThat(line.argumentsForTheScript()).containsExactly("g", "h");
            assertThat(line.flags()).containsExactly(Flag.SCRIPT);
        }

        @Test
        @DisplayName("--args comes first among the arguments")
        void theArgsValueComesFirst() {
            RebolArguments line = asked("--args", "a b", "s.r3", "c");

            assertThat(line.argumentsForTheScript()).containsExactly("a b", "c");
            assertThat(line.valueOf(Flag.ARGS)).contains("a b");
        }
    }

    @Nested
    @DisplayName("an option that takes a value")
    class OptionsWithAValue {

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource(delimiter = '|', textBlock = """
                --args    | a        | args
                --boot    | base     | boot-level
                --debug   | abc      | debug
                --do      | print 1  | do
                --import  | m.reb    | import
                --script  | s.r3     | script
                --secure  | allow    | secure
                --version | 1.2.3    | version
                -b        | sys      | boot-level
                """)
        void takesTheNextWord(String option, String value, String flag) {
            RebolArguments line = asked(option, value);

            assertThat(line.flags()).isEqualTo(flagsNamed(flag));
            assertThat(line.valueOf(flagsNamed(flag).iterator().next())).contains(value);
        }

        @Test
        @DisplayName("with no word after it asks for help instead, and is not set itself")
        void withNothingAfterItAsksForHelp() {
            RebolArguments line = asked("--do");

            assertThat(line.flags()).containsExactly(Flag.HELP);
            assertThat(line.valueOf(Flag.DO)).isEmpty();
        }

        @Test
        @DisplayName("is set without a value when the next word's second character is a dash, which is then read as itself")
        void aSecondCharacterDashLeavesTheWord() {
            RebolArguments line = asked("--do", "a-b");

            assertThat(line.flags()).containsExactly(Flag.DO);
            assertThat(line.valueOf(Flag.DO)).isEmpty();
            assertThat(line.script()).contains("a-b");
        }

        @Test
        @DisplayName("and so --do --x sets do and reads --x as an unknown option")
        void aDoubleDashWordAfterAValueOption() {
            assertThat(asked("--do", "--x").flags()).isEqualTo(EnumSet.of(Flag.DO, Flag.HELP));
        }

        @Test
        @DisplayName("but takes a word whose first character alone is a dash")
        void aWordStartingWithOneDashIsTaken() {
            assertThat(asked("--do", "-x").valueOf(Flag.DO)).contains("-x");
        }

        @Test
        @DisplayName("and takes a single dash, which has no second character")
        void aLoneDashIsTaken() {
            assertThat(asked("--do", "-").valueOf(Flag.DO)).contains("-");
        }
    }

    @Nested
    @DisplayName("an option that is only a flag")
    class FlagsOnly {

        @ParameterizedTest(name = "{0} sets [{1}]")
        @CsvSource(delimiter = '|', textBlock = """
                --cgi         | cgi quiet
                --halt        | halt
                --help        | help
                --legacy-repl | legacy-repl
                --no-color    | no-color
                --quiet       | quiet
                --trace       | trace
                --verbose     | verbose
                --unknown     | help
                --DO          | help
                -?            | help
                -V            | vers
                -c            | cgi quiet
                -h            | halt
                -q            | quiet
                -s            | secure-min
                -t            | trace
                -v            | vers
                -w            | no-window
                -x            | help
                -cq           | cgi quiet
                -qh           | quiet halt
                +s            | secure-max
                +x            | help
                """)
        void setsTheFlags(String option, String flags) {
            assertThat(asked(option).flags()).isEqualTo(flagsNamed(flags));
        }

        @Test
        @DisplayName("a lone dash sets nothing")
        void aLoneDashSetsNothing() {
            assertThat(asked("-").flags()).isEmpty();
        }

        @Test
        @DisplayName("a word option starting with a line ending is ignored, as some shells pass one")
        void aLineEndingWordIsIgnored() {
            assertThat(asked("--\r").flags()).isEmpty();
        }

        @Test
        @DisplayName("and so is a character option that is a line ending")
        void aLineEndingCharacterIsIgnored() {
            assertThat(asked("-\n").flags()).isEmpty();
        }

        @Test
        @DisplayName("a flag set twice is set once")
        void aFlagTwiceIsOnce() {
            assertThat(asked("-q", "--quiet").flags()).containsExactly(Flag.QUIET);
        }

        @Test
        @DisplayName("an option that takes no value leaves the next word to be the script")
        void aFlagDoesNotTakeTheNextWord() {
            assertThat(asked("-q", "s.r3").script()).isEqualTo(Optional.of("s.r3"));
        }
    }
}
