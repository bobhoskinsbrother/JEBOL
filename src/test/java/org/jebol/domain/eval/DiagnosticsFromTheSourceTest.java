package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DS, DUMP, CHECK and EVOKE: the four natives that look into the interpreter.
 *
 * <p>Read out of {@code n-system.c}, {@code n-data.c} and {@code d-dump.c}, and
 * the thing to know about all four is that the source and the shipped build
 * disagree. Two of them have bodies inside {@code #ifdef DEBUG}, so a released
 * 3.22.1 runs the last line and nothing else.
 *
 * <p>What each really does:
 *
 * <ul>
 *   <li>DS prints the frame stack -- the calling word, the argument count, the
 *   function's datatype, then a line per argument. Not a C memory structure,
 *   and compiled into every build. It is what STACK answers here, printed.
 *   <li>DUMP answers its argument and prints nothing, because the walk it would
 *   print is debug-only: {@code return R_ARG1;} is all a release build reaches.
 *   <li>CHECK walks a series looking for a terminator in the wrong place. That
 *   is the C's own invariant and a JEBOL series has none, so the check holds
 *   for every series and it answers the value.
 *   <li>EVOKE is a dialect of guru switches, and most of them are debug-only.
 *   A release build raises {@code feature-na} for those -- Rebol's own error
 *   for a build that cannot do what was asked.
 * </ul>
 *
 * <p>So only one of the four refuses anything, and the error is Rebol's rather
 * than one invented here. Specified in {@code spec/natives.allium} under "The
 * four diagnostics, read again".
 */
class DiagnosticsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    /** What a script printed, which for DS and EVOKE's help is the behaviour. */
    private static String outputOf(String source) {
        StringBuilder captured = new StringBuilder();
        Interpreter interpreter = Interpreter.writingTo(captured::append);
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
        return captured.toString();
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("DS prints the frame stack")
    class TheStackDump {

        @Test
        @DisplayName("one line per open frame, naming the word, the count and the datatype")
        void aLinePerFrame() {
            // `Debug_Fmt(BOOT_STR(RS_STACK, 1), dsp, Get_Word_Name(DSF_WORD(dsf)),
            // m, Get_Type_Name(DSF_FUNC(dsf)))` against the format
            // `^/STACK[%d] %s[%d] %s` in boot/strings.reb.
            String printed = outputOf("f: func [x] [ds] f 5");
            assertThat(printed).contains("STACK[").contains("f[1]").contains("function!");
        }

        @Test
        @DisplayName("and a line per argument, giving its name and value")
        void aLinePerArgument() {
            // `Debug_Fmt("\\t%s: %72r", Get_Word_Name(args+n), DSF_ARGS(dsf, n))`
            // -- which is why this is portable at all: the arguments are in the
            // frame, and JEBOL keeps its frames on the heap.
            assertThat(outputOf("f: func [x] [ds] f 5")).contains("x: 5");
        }

        @Test
        @DisplayName("the count in brackets falls as the walk goes outwards")
        void theSlotCountFallsOutwards() {
            // The C prints its data stack pointer there: the slots in use up to
            // and including that frame, so it falls with each step outwards.
            // Measured here the way STACK/SIZE measures the same stack, since
            // the C reads the same DSP for both.
            String printed = outputOf(
                    "inner: func [y] [ds] outer: func [x] [inner 2] outer 1");
            java.util.List<Integer> counts = java.util.regex.Pattern
                    .compile("STACK\\[(\\d+)]").matcher(printed).results()
                    .map(found -> Integer.valueOf(found.group(1)))
                    .toList();
            assertThat(counts).hasSize(2);
            assertThat(counts.get(0)).isGreaterThan(counts.get(1));
        }

        @Test
        @DisplayName("walking outwards, innermost frame first")
        void theInnermostFrameComesFirst() {
            // `if (PRIOR_DSF(dsf) > 0) Dump_Stack(PRIOR_DSF(dsf), dsf-1);` after
            // the frame it is on, so the order is the order a caller reads a
            // backtrace in.
            String printed = outputOf("inner: func [y] [ds] outer: func [x] [inner 2] outer 1");
            assertThat(printed.indexOf("inner")).isLessThan(printed.indexOf("outer"));
        }

        @Test
        @DisplayName("it answers nothing, whatever it printed")
        void itAnswersUnset() {
            // `Dump_Stack(0, 0); return R_UNSET;` -- the two lines after that
            // return are dead code in the C.
            assertThat(answerTo("f: func [] [unset? ds] f")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and prints at the top level too, where no frame is open")
        void thereIsAlwaysALine() {
            // The C prints before it tests `if (dsf > 0)`, so a call with
            // nothing open still prints one line. Printing nothing would read
            // as a DS that did not run.
            assertThat(outputOf("ds")).contains("STACK[");
        }

        @Test
        @DisplayName("a frame taking no arguments has no argument lines under it")
        void aFrameWithNoArguments() {
            // `for (n = 1; n < m; n++)` where m is the argument count, so a
            // function of none prints its own line and stops.
            String printed = outputOf("f: func [] [ds] f");
            assertThat(printed).contains("f[0]");
            assertThat(printed).doesNotContain(": ");
        }

        @Test
        @DisplayName("and a call no word made prints a frame with no name")
        void aFrameReachedWithoutAWord() {
            // `if (!word) word = ROOT_NONAME;` -- a function value standing in a
            // block is called with no word attached, so the frame has nothing to
            // name. Printing the word from an earlier call would be worse than
            // printing none: it would name the wrong function.
            assertThat(outputOf("f: func [x] [ds] do reduce [:f 5]"))
                    .contains("STACK[")
                    .contains("x: 5");
        }
    }

    @Nested
    @DisplayName("DUMP answers its argument")
    class TheValueDump {

        @Test
        @DisplayName("and prints nothing, because the printing is debug-only")
        void itPrintsNothing() {
            // The whole body is inside `#ifdef DEBUG`. A released 3.22.1 reaches
            // `return R_ARG1;` and no more, so this is the behaviour to port --
            // not the debug build's series walk, which no shipped Rebol runs.
            assertThat(outputOf("dump [1 2]")).isEmpty();
            assertThat(answerTo("dump [1 2]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("whatever it is given, series or not")
        void anyValueAtAll() {
            // `v` is declared with no datatypes, so everything is allowed and
            // the debug build's `if (ANY_SERIES(arg))` is what parts them.
            assertThat(answerTo("dump 5")).isEqualTo("5");
            assertThat(answerTo("dump \"ab\"")).isEqualTo("\"ab\"");
            assertThat(answerTo("none? dump none")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("at the position it was given")
        void itKeepsThePosition() {
            // `R_ARG1` is the argument itself, index and all.
            assertThat(answerTo("dump skip [1 2 3] 2")).isEqualTo("[3]");
        }

        @Test
        @DisplayName("and it needs one, because the argument is not optional")
        void itNeedsItsArgument() {
            assertThat(errorIdFrom("do [dump]")).isEqualTo("no-arg");
        }

        @Test
        @DisplayName("and /FMT reaches the same nothing")
        void theFormatRefinementChangesNothing() {
            // `/fmt "only series format"` chooses between Dump_Series_Fmt and
            // Dump_Series, both of which are compiled out.
            assertThat(outputOf("dump/fmt [1 2]")).isEmpty();
            assertThat(answerTo("dump/fmt [1 2]")).isEqualTo("[1 2]");
        }
    }

    @Nested
    @DisplayName("CHECK answers the series it was given")
    class TheSeriesCheck {

        @Test
        @DisplayName("because the invariant it tests cannot be broken here")
        void itAnswersTheValue() {
            // It walks the series for a terminator in the wrong place and raises
            // `bad-series` when it finds one. That terminator is part of a
            // REBSER; a JEBOL series has none, so the check holds always.
            assertThat(answerTo("check [1 2]")).isEqualTo("[1 2]");
            assertThat(answerTo("check \"ab\"")).isEqualTo("\"ab\"");
            assertThat(answerTo("check #{0102}")).isEqualTo("#{0102}");
        }

        @Test
        @DisplayName("at the position it was given, not at the head")
        void itKeepsThePosition() {
            // `*D_RET = *val` copies the value, index and all.
            assertThat(answerTo("check skip [1 2 3] 2")).isEqualTo("[3]");
        }

        @Test
        @DisplayName("and prints nothing at all")
        void itPrintsNothing() {
            // Asserted with the answer beside it on purpose: a script that
            // failed prints nothing either, so emptiness alone would pass
            // against a CHECK that does not exist.
            assertThat(outputOf("prin mold check [1 2]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("an empty series and one at its tail are checked and answered too")
        void theDegenerateSeries() {
            // Both loops are `for (n = 0; n < SERIES_TAIL(ser); n++)`, so
            // nothing runs and the check after the loop is what decides. A
            // series with nothing in it is well formed.
            assertThat(answerTo("check []")).isEqualTo("[]");
            assertThat(answerTo("check \"\"")).isEqualTo("\"\"");
            assertThat(answerTo("check tail [1 2]")).isEqualTo("[]");
        }

        @Test
        @DisplayName("and takes a series and nothing else")
        void itRefusesWhatIsNotASeries() {
            // `val [series!]`, so the refusal is at the declaration.
            assertThat(errorIdFrom("check 5")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("check none")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("check make map! [a 1]")).isEqualTo("expect-arg");
        }
    }

    /**
     * EVOKE's chants, one by one.
     *
     * <p>One thing the C does that is not tested here: it opens with
     * {@code Check_Security(SYM_DEBUG, POL_READ, 0)}, so a script under
     * {@code secure [debug none]} cannot evoke anything. JEBOL has no SECURE
     * policy model, only the host's grants, and inventing a debug policy to hold
     * one native would be inventing the model rather than porting it. When
     * SECURE arrives this belongs behind it.
     */
    @Nested
    @DisplayName("EVOKE, whose chants are mostly unavailable")
    class TheGuruMeditations {

        @Test
        @DisplayName("a debug-only chant raises feature-na, which is Rebol's own answer")
        void aDebugChantIsUnavailable() {
            // The six labels are inside `#ifdef DEBUG` and the `#else` gives
            // them all one line: `Trap0(RE_FEATURE_NA)`. So a release build
            // already refuses these, and it refuses them by name rather than by
            // pretending to have done something.
            for (String chant : new String[] {
                    "crash-dump", "watch-recycle", "watch-alloc",
                    "watch-obj-copy", "watch-expand", "crash"}) {
                assertThat(errorIdFrom("evoke '" + chant))
                        .as("evoke '%s", chant)
                        .isEqualTo("feature-na");
            }
        }

        @Test
        @DisplayName("asking for a bigger stack is accepted and needs doing nothing")
        void growingTheStackIsAlreadyAutomatic() {
            // `case SYM_STACK_SIZE: arg++; Expand_Stack(Int32s(arg, 1));` -- the
            // one chant a release build performs, and it is a capacity hint
            // rather than a permission: the evaluator grows the same buffer
            // unasked whenever it runs low. A JVM grows its structures on its
            // own, so there is nothing to do and nothing to refuse.
            assertThat(answerTo("unset? evoke [stack-size 100]")).isEqualTo(TRUE);
            assertThat(outputOf("evoke [stack-size 100]")).isEmpty();
        }

        @Test
        @DisplayName("DELECT is accepted and does nothing, because its body is compiled out")
        void delectIsAcceptedAndIdle() {
            // The `case SYM_DELECT:` label is outside the ifdef and only its
            // body is inside, so the chant is recognised and breaks out of the
            // switch. That is why it is not a feature-na like the others.
            assertThat(answerTo("unset? evoke 'delect")).isEqualTo(TRUE);
            assertThat(outputOf("evoke 'delect")).isEmpty();
        }

        @Test
        @DisplayName("an integer chant asks for checks that have nothing to check")
        void anIntegerChantAnswersUnset() {
            // 0 runs both memory checks, 1 the pools, 2 the bind table. Each
            // answers unset either way, so a caller cannot tell a check that
            // passed from one that was skipped -- which is what makes doing
            // nothing the faithful answer rather than a convenient one.
            assertThat(answerTo("unset? evoke 0")).isEqualTo(TRUE);
            assertThat(answerTo("unset? evoke 1")).isEqualTo(TRUE);
            assertThat(answerTo("unset? evoke 2")).isEqualTo(TRUE);
            assertThat(outputOf("evoke 1")).isEmpty();
        }

        @Test
        @DisplayName("and an integer naming no check prints the list of chants")
        void anUnknownIntegerPrintsTheHelp() {
            // `default: Out_Str(cb_cast(evoke_help), 1, FALSE);` in the integer
            // switch as well as the word one.
            assertThat(outputOf("evoke 9")).contains("Evoke values:");
        }

        @Test
        @DisplayName("an unknown word prints the list, and the list is the release one")
        void anUnknownWordPrintsTheHelp() {
            // The help text is assembled by the preprocessor, so a release build
            // lists `stack-size` and the two numbered checks and not the watch
            // chants: those are inside the same `#ifdef DEBUG` that refuses
            // them.
            String printed = outputOf("evoke 'nonsense");
            assertThat(printed).contains("Evoke values:").contains("[stack-size n]");
            assertThat(printed).contains("1: check memory pools")
                    .contains("2: check bind table");
            assertThat(printed).doesNotContain("watch-recycle");
        }

        @Test
        @DisplayName("a block carries several chants, taken in turn")
        void aBlockIsSeveralChants() {
            // `if (IS_BLOCK(arg)) { len = VAL_LEN(arg); arg = VAL_BLK_DATA(arg); }`
            // and then one loop over the lot.
            assertThat(answerTo("unset? evoke [delect delect]")).isEqualTo(TRUE);
            assertThat(outputOf("evoke [nonsense nonsense]").split("Evoke values:", -1))
                    .as("each unknown word prints the list once")
                    .hasSize(3);
        }

        @Test
        @DisplayName("and one bad chant in a block refuses the whole call")
        void oneBadChantRefusesTheBlock() {
            assertThat(errorIdFrom("evoke [delect crash]")).isEqualTo("feature-na");
        }

        @Test
        @DisplayName("an empty block asks for nothing and gets it")
        void anEmptyBlockDoesNothing() {
            // `len = VAL_LEN(arg)` is zero, so the loop does not run.
            assertThat(answerTo("unset? evoke []")).isEqualTo(TRUE);
            assertThat(outputOf("evoke []")).isEmpty();
        }

        @Test
        @DisplayName("and a block may hold integers as readily as words")
        void aBlockOfIntegers() {
            // The loop tests `IS_WORD(arg)` and then `IS_INTEGER(arg)`
            // separately, on the same item, so both forms travel in one block.
            assertThat(answerTo("unset? evoke [1 2]")).isEqualTo(TRUE);
            assertThat(outputOf("evoke [1 2]")).isEmpty();
            assertThat(outputOf("evoke [delect 9]")).contains("Evoke values:");
        }

        @Test
        @DisplayName("the chant has to be a word, a block or an integer")
        void theChantIsTyped() {
            // `chant [word! block! integer!] "Single or block of words ('? to list)"`
            assertThat(errorIdFrom("evoke \"crash\"")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("evoke 1.5")).isEqualTo("expect-arg");
        }
    }
}
