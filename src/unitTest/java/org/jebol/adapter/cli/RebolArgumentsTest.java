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

    private RebolArguments ask(String... words) {
        return new RebolArguments(List.of(words));
    }

    private EnumSet<Flag> nameTheFlags(String spelled) {
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
        void isNothingWithoutWords() {
            RebolArguments nothing = ask();

            assertThat(nothing.flags()).isEmpty();
            assertThat(nothing.script()).isEmpty();
            assertThat(nothing.listTheArgumentsForTheScript()).isEmpty();
        }

        @Test
        @DisplayName("the first word that is not an option is the script")
        void isTheFirstPlainWord() {
            assertThat(ask("s.r3").script()).contains("s.r3");
        }

        @Test
        @DisplayName("an empty word is a script with an empty name, as r3 reads it")
        void mayBeAnEmptyWord() {
            assertThat(ask("").script()).contains("");
        }

        @Test
        @DisplayName("every word after the script is its own, options included")
        void takesEverythingAfterItAsArguments() {
            RebolArguments line = ask("-q", "s.r3", "--do", "x", "-v");

            assertThat(line.script()).contains("s.r3");
            assertThat(line.listTheArgumentsForTheScript()).containsExactly("--do", "x", "-v");
            assertThat(line.flags()).containsExactly(Flag.QUIET);
        }

        @Test
        @DisplayName("-- ends the options and names no script")
        void followsADoubleDashThatEndsTheOptions() {
            RebolArguments line = ask("--", "s.r3", "x");

            assertThat(line.script()).isEmpty();
            assertThat(line.listTheArgumentsForTheScript()).containsExactly("s.r3", "x");
        }

        @Test
        @DisplayName("-- after the script is one of its arguments")
        void takesADoubleDashAfterItAsAnArgument() {
            assertThat(ask("s.r3", "--", "x").listTheArgumentsForTheScript())
                    .containsExactly("--", "x");
        }

        @Test
        @DisplayName("--script names the script, and the next plain word is then its first argument")
        void leavesThePlainWordToTheArgumentsWhenExplicit() {
            RebolArguments line = ask("--script", "s.r3", "g", "h");

            assertThat(line.script()).contains("s.r3");
            assertThat(line.listTheArgumentsForTheScript()).containsExactly("g", "h");
            assertThat(line.flags()).containsExactly(Flag.SCRIPT);
        }

        @Test
        @DisplayName("--args comes first among the arguments")
        void putsTheArgsValueFirst() {
            RebolArguments line = ask("--args", "a b", "s.r3", "c");

            assertThat(line.listTheArgumentsForTheScript()).containsExactly("a b", "c");
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
            RebolArguments line = ask(option, value);

            assertThat(line.flags()).isEqualTo(nameTheFlags(flag));
            assertThat(line.valueOf(nameTheFlags(flag).iterator().next())).contains(value);
        }

        @Test
        @DisplayName("with no word after it asks for help instead, and is not set itself")
        void asksForHelpWithNothingAfterThem() {
            RebolArguments line = ask("--do");

            assertThat(line.flags()).containsExactly(Flag.HELP);
            assertThat(line.valueOf(Flag.DO)).isEmpty();
        }

        @Test
        @DisplayName("is set without a value when the next word's second character is a dash, which is then read as itself")
        void leaveTheWordWithADashSecond() {
            RebolArguments line = ask("--do", "a-b");

            assertThat(line.flags()).containsExactly(Flag.DO);
            assertThat(line.valueOf(Flag.DO)).isEmpty();
            assertThat(line.script()).contains("a-b");
        }

        @Test
        @DisplayName("and so --do --x sets do and reads --x as an unknown option")
        void takeADoubleDashWordAfterThem() {
            assertThat(ask("--do", "--x").flags()).isEqualTo(EnumSet.of(Flag.DO, Flag.HELP));
        }

        @Test
        @DisplayName("but takes a word whose first character alone is a dash")
        void takeAWordStartingWithOneDash() {
            assertThat(ask("--do", "-x").valueOf(Flag.DO)).contains("-x");
        }

        @Test
        @DisplayName("and takes a single dash, which has no second character")
        void takeALoneDash() {
            assertThat(ask("--do", "-").valueOf(Flag.DO)).contains("-");
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
            assertThat(ask(option).flags()).isEqualTo(nameTheFlags(flags));
        }

        @Test
        @DisplayName("a lone dash sets nothing")
        void ignoreALoneDash() {
            assertThat(ask("-").flags()).isEmpty();
        }

        @Test
        @DisplayName("a word option starting with a line ending is ignored, as some shells pass one")
        void ignoreALineEndingWord() {
            assertThat(ask("--\r").flags()).isEmpty();
        }

        @Test
        @DisplayName("and so is a character option that is a line ending")
        void ignoreALineEndingCharacter() {
            assertThat(ask("-\n").flags()).isEmpty();
        }

        @Test
        @DisplayName("a flag set twice is set once")
        void countATwiceGivenFlagOnce() {
            assertThat(ask("-q", "--quiet").flags()).containsExactly(Flag.QUIET);
        }

        @Test
        @DisplayName("an option that takes no value leaves the next word to be the script")
        void leaveTheNextWordAlone() {
            assertThat(ask("-q", "s.r3").script()).isEqualTo(Optional.of("s.r3"));
        }
    }
}
