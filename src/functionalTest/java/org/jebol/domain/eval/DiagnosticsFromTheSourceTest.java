package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static String readTheOutputOf(String source) {
        StringBuilder captured = new StringBuilder();
        Interpreter interpreter = AnInterpreter.createWithOutput(captured::append);
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
        return captured.toString();
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("DS prints the frame stack")
    class TheStackDump {

        @Test
        @DisplayName("one line per open frame, naming the word, the count and the datatype")
        void printsALinePerFrame() {
            String printed = readTheOutputOf("f: func [x] [ds] f 5");
            assertThat(printed).contains("STACK[").contains("f[1]").contains("function!");
        }

        @Test
        @DisplayName("and a line per argument, giving its name and value")
        void printsALinePerArgument() {
            assertThat(readTheOutputOf("f: func [x] [ds] f 5")).contains("x: 5");
        }

        @Test
        @DisplayName("the count in brackets falls as the walk goes outwards")
        void countsFewerSlotsOutwards() {
            String printed = readTheOutputOf(
                    "inner: func [y] [ds] outer: func [x] [inner 2] outer 1");
            java.util.List<Integer> counts = java.util.regex.Pattern
                    .compile("STACK\\[(\\d+)]").matcher(printed).results()
                    .map(found -> Integer.valueOf(found.group(1)))
                    .toList();
            assertThat(counts).hasSize(3);
            assertThat(counts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }

        @Test
        @DisplayName("walking outwards, innermost frame first")
        void putsTheInnermostFrameFirst() {
            String printed = readTheOutputOf("inner: func [y] [ds] outer: func [x] [inner 2] outer 1");
            assertThat(printed.indexOf("inner")).isLessThan(printed.indexOf("outer"));
        }

        @Test
        @DisplayName("it answers nothing, whatever it printed")
        void answersUnset() {
            assertThat(answerTo("f: func [] [unset? ds] f")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and prints at the top level too, where no frame is open")
        void alwaysPrintsALine() {
            assertThat(readTheOutputOf("ds")).contains("STACK[");
        }

        @Test
        @DisplayName("the innermost line is DS's own call, named and typed as a native")
        void startsWithDsItself() {
            assertThat(readTheOutputOf("ds")).contains("ds[0]").contains("native!");
        }

        @Test
        @DisplayName("and it is there ahead of the caller's own frames")
        void dsComesBeforeTheCallersFrames() {
            String printed = readTheOutputOf("f: func [x] [ds] f 5");

            assertThat(printed).contains("ds[0]").contains("f[1]");
            assertThat(printed.indexOf("ds[0]")).isLessThan(printed.indexOf("f[1]"));
        }

        @Test
        @DisplayName("which is what STACK already says, so the two agree")
        void dsAndStackDescribeTheSameStack() {
            assertThat(answerTo("'stack = stack/word 0")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("ds")).doesNotContain("?[0]");
        }

        @Test
        @DisplayName("and no frame goes missing under it")
        void stillPrintsEveryOpenCall() {
            String printed = readTheOutputOf(
                    "inner: func [y] [ds] middle: func [z] [inner 2] outer: func [x] [middle 3] outer 1");

            assertThat(printed)
                    .contains("ds[0]")
                    .contains("inner[1]")
                    .contains("middle[1]")
                    .contains("outer[1]");
        }

        @Test
        @DisplayName("a frame taking no arguments has no argument lines under it")
        void printsAFrameWithNoArguments() {
            String printed = readTheOutputOf("f: func [] [ds] f");
            assertThat(printed).contains("f[0]");
            assertThat(printed).doesNotContain(": ");
        }

        @Test
        @DisplayName("and a call no word made prints a frame with no name")
        void printsAFrameReachedWithoutAWord() {
            assertThat(readTheOutputOf("f: func [x] [ds] do reduce [:f 5]"))
                    .contains("STACK[")
                    .contains("x: 5");
        }
    }

    @Nested
    @DisplayName("DUMP answers its argument")
    class TheValueDump {

        @Test
        @DisplayName("and prints nothing, because the printing is debug-only")
        void printsNothing() {
            assertThat(readTheOutputOf("dump [1 2]")).isEmpty();
            assertThat(answerTo("dump [1 2]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("whatever it is given, series or not")
        void takesAnyValueAtAll() {
            assertThat(answerTo("dump 5")).isEqualTo("5");
            assertThat(answerTo("dump \"ab\"")).isEqualTo("\"ab\"");
            assertThat(answerTo("none? dump none")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("at the position it was given")
        void keepsThePosition() {
            assertThat(answerTo("dump skip [1 2 3] 2")).isEqualTo("[3]");
        }

        @Test
        @DisplayName("and it needs one, because the argument is not optional")
        void needsItsArgument() {
            assertThat(readTheErrorIdFrom("do [dump]")).isEqualTo("no-arg");
        }

        @Test
        @DisplayName("and /FMT reaches the same nothing")
        void changesNothingForTheFormatRefinement() {
            assertThat(readTheOutputOf("dump/fmt [1 2]")).isEmpty();
            assertThat(answerTo("dump/fmt [1 2]")).isEqualTo("[1 2]");
        }
    }

    @Nested
    @DisplayName("CHECK answers the series it was given")
    class TheSeriesCheck {

        @Test
        @DisplayName("because the invariant it tests cannot be broken here")
        void answersTheValue() {
            assertThat(answerTo("check [1 2]")).isEqualTo("[1 2]");
            assertThat(answerTo("check \"ab\"")).isEqualTo("\"ab\"");
            assertThat(answerTo("check #{0102}")).isEqualTo("#{0102}");
        }

        @Test
        @DisplayName("at the position it was given, not at the head")
        void keepsThePosition() {
            assertThat(answerTo("check skip [1 2 3] 2")).isEqualTo("[3]");
        }

        @Test
        @DisplayName("and prints nothing at all")
        void printsNothing() {
            assertThat(readTheOutputOf("prin mold check [1 2]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("an empty series and one at its tail are checked and answered too")
        void handlesTheDegenerateSeries() {
            assertThat(answerTo("check []")).isEqualTo("[]");
            assertThat(answerTo("check \"\"")).isEqualTo("\"\"");
            assertThat(answerTo("check tail [1 2]")).isEqualTo("[]");
        }

        @Test
        @DisplayName("and takes a series and nothing else")
        void refusesWhatIsNotASeries() {
            assertThat(readTheErrorIdFrom("check 5")).isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("check none")).isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("check make map! [a 1]")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("EVOKE, whose chants are mostly unavailable")
    class TheGuruMeditations {

        @Test
        @DisplayName("a debug-only chant raises feature-na, which is Rebol's own answer")
        void refuseADebugChant() {
            for (String chant : new String[] {
                    "crash-dump", "watch-recycle", "watch-alloc",
                    "watch-obj-copy", "watch-expand", "crash"}) {
                assertThat(readTheErrorIdFrom("evoke '" + chant))
                        .as("evoke '%s", chant)
                        .isEqualTo("feature-na");
            }
        }

        @Test
        @DisplayName("asking for a bigger stack is accepted and needs doing nothing")
        void treatGrowingTheStackAsAlreadyAutomatic() {
            assertThat(answerTo("unset? evoke [stack-size 100]")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("evoke [stack-size 100]")).isEmpty();
        }

        @Test
        @DisplayName("DELECT is accepted and does nothing, because its body is compiled out")
        void delectIsAcceptedAndIdle() {
            assertThat(answerTo("unset? evoke 'delect")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("evoke 'delect")).isEmpty();
        }

        @Test
        @DisplayName("an integer chant asks for checks that have nothing to check")
        void answerUnsetForAnIntegerChant() {
            assertThat(answerTo("unset? evoke 0")).isEqualTo(TRUE);
            assertThat(answerTo("unset? evoke 1")).isEqualTo(TRUE);
            assertThat(answerTo("unset? evoke 2")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("evoke 1")).isEmpty();
        }

        @Test
        @DisplayName("and an integer naming no check prints the list of chants")
        void printTheHelpForAnUnknownInteger() {
            assertThat(readTheOutputOf("evoke 9")).contains("Evoke values:");
        }

        @Test
        @DisplayName("an unknown word prints the list, and the list is the release one")
        void printTheHelpForAnUnknownWord() {
            String printed = readTheOutputOf("evoke 'nonsense");
            assertThat(printed).contains("Evoke values:").contains("[stack-size n]");
            assertThat(printed).contains("1: check memory pools")
                    .contains("2: check bind table");
            assertThat(printed).doesNotContain("watch-recycle");
        }

        @Test
        @DisplayName("a block carries several chants, taken in turn")
        void readABlockAsSeveralChants() {
            assertThat(answerTo("unset? evoke [delect delect]")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("evoke [nonsense nonsense]").split("Evoke values:", -1))
                    .as("each unknown word prints the list once")
                    .hasSize(3);
        }

        @Test
        @DisplayName("and one bad chant in a block refuses the whole call")
        void refuseTheBlockForOneBadChant() {
            assertThat(readTheErrorIdFrom("evoke [delect crash]")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("an empty block asks for nothing and gets it")
        void doNothingForAnEmptyBlock() {
            assertThat(answerTo("unset? evoke []")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("evoke []")).isEmpty();
        }

        @Test
        @DisplayName("and a block may hold integers as readily as words")
        void takeABlockOfIntegers() {
            assertThat(answerTo("unset? evoke [1 2]")).isEqualTo(TRUE);
            assertThat(readTheOutputOf("evoke [1 2]")).isEmpty();
            assertThat(readTheOutputOf("evoke [delect 9]")).contains("Evoke values:");
        }

        @Test
        @DisplayName("the chant has to be a word, a block or an integer")
        void checkTheChantsType() {
            assertThat(readTheErrorIdFrom("evoke \"crash\"")).isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("evoke 1.5")).isEqualTo("expect-arg");
        }
    }
}
