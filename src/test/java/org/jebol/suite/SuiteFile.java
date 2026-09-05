package org.jebol.suite;

import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One of Rebol's own test scripts, read into the assertions it makes.
 *
 * <p>The scripts are ordinary REBOL, so nothing here parses REBOL: the
 * reader does that, and this walks the values it produced. The six words
 * the test dialect uses ({@code ~~~start-file~~~}, {@code ===start-group===}
 * and so on) are the only ones treated specially, and everything between
 * two of them is either an assertion or setup.
 *
 * <p>Assertions are sliced syntactically rather than by DO/NEXT, which is
 * sound for this suite because it writes one expression per {@code --assert}
 * and never two. Each slice is molded back to source and run on its own, so
 * one failure cannot take the rest of the file with it.
 */
record SuiteFile(String name, List<Assertion> assertions, List<Step> steps) {

    /**
     * One thing to run, in file order.
     *
     * <p>A test file is a script, not a list of independent expressions:
     * assertions lean on words set up above them, sometimes many lines
     * above. Running each assertion in a fresh interpreter loses that, and
     * it showed up as roughly four hundred failures on words called a, b,
     * i and obj -- which is the shape of a harness bug rather than of a
     * language one.
     */
    record Step(Assertion assertion, String setup, List<Assertion> nested,
            String numberedSetup) {

        Step(Assertion assertion, String setup) {
            this(assertion, setup, List.of(), null);
        }

        Step(Assertion assertion, String setup, List<Assertion> nested) {
            this(assertion, setup, nested, null);
        }

        boolean isAssertion() {
            return assertion != null;
        }

        /**
         * The source to run: the source as written, with each nested
         * {@code --assert} told which assertion it is.
         *
         * <p>Falls back to the source exactly as written wherever the
         * numbering could not be shown to mean the same thing, which costs
         * the exactness and keeps the step.
         */
        String sourceToRun() {
            return numberedSetup == null
                    ? (assertion != null ? assertion.source() : setup)
                    : numberedSetup;
        }
    }

    /**
     * One {@code --assert}, with enough context to say where it came from.
     *
     * @param from  where the assertion begins in its file, counted in code
     *              points, so that whatever wants to cut it out again cuts
     *              in the right place. The {@code --assert} word itself is
     *              not included: this is the expression it asserts.
     * @param to    one past where it ends
     */
    record Assertion(String file, String group, String test, int ordinal, String source,
            int from, int to, boolean redOnly) {

        Assertion(String file, String group, String test, int ordinal, String source,
                int from, int to) {
            this(file, group, test, ordinal, source, from, to, false);
        }

        /**
         * Unique within its file, because the ordinal counts assertions
         * across the whole file rather than within a test.
         *
         * <p>Numbering within a test looked tidier and produced duplicate
         * ids wherever two tests shared a name, which made a gap list
         * unusable: one assertion under a shared id passed while another
         * failed.
         */
        @Override
        public String toString() {
            return file + " / " + group + " / " + test + " #" + ordinal;
        }
    }

    private static final String START_FILE = "~~~start-file~~~";
    private static final String END_FILE = "~~~end-file~~~";
    private static final String START_GROUP = "===start-group===";
    private static final String END_GROUP = "===end-group===";
    private static final String TEST = "--test--";
    private static final String ASSERT = "--assert";

    /**
     * Rebol's mark for an assertion that describes Red rather than Rebol.
     *
     * <p>{@code quick-test-module.r3} binds it to {@code as-red-only}, and a
     * failing assertion under the flag is reported as "not like Red" instead of
     * being counted a failure. It is a harness word here so the mark reaches
     * the assertion; left as an ordinary word it was swept into the setup and
     * lost, and eight assertions that a real Rebol fails were graded as gaps.
     */
    private static final String RED_ONLY = "--red--";

    private static boolean isHarnessWord(Value value) {
        return value instanceof WordValue word && switch (word.spelling()) {
            case START_FILE, END_FILE, START_GROUP, END_GROUP, TEST, ASSERT,
                    RED_ONLY -> true;
            default -> word.spelling().startsWith("--assertf");
        };
    }


    /** What a nested {@code --assert} becomes, so its report carries its number. */
    static final String NUMBERED_ASSERT = "--assert-numbered";

    /**
     * The source as written, with each nested {@code --assert} told which
     * assertion it is.
     *
     * <p>An assertion inside a block cannot be sliced out and run on its own,
     * so the enclosing expression runs and each {@code --assert} inside
     * reports as it goes. Reporting only whether it held means the reports
     * have to be matched to the assertions by counting, and counting is wrong
     * twice over: a function defined in one step and called in another
     * reports where it ran rather than where it was written, and a loop
     * reports three assertions a hundred times. Carrying the number makes
     * both exact.
     *
     * <p>The number goes into the text rather than into a molded copy of the
     * values. Molding would put the port's own MOLD between the suite and
     * what the suite actually runs -- the measure would depend on a part of
     * the thing being measured, and a mold that broke would quietly change
     * the tests rather than fail. The reader is already unavoidable here,
     * since nothing can slice the file without reading it; MOLD is not, so it
     * stays out.
     *
     * <p>Scanning text for a word is a guess, so the result is checked
     * against the source it came from: read both, walk them together, and
     * every value must be the same except the numbered ones. A step that
     * fails that check keeps the source it was written with.
     */
    private static String numberedSource(String written, int firstOrdinal) {
        StringBuilder out = new StringBuilder();
        int ordinal = firstOrdinal;
        int at = 0;
        while (at < written.length()) {
            char letter = written.charAt(at);
            if (letter == ';') {
                at = copyToEndOfLine(written, at, out);
            } else if (letter == '"') {
                at = copyQuoted(written, at, out);
            } else if (letter == '{') {
                at = copyBraced(written, at, out);
            } else if (opensAnAssertion(written, at)) {
                out.append(NUMBERED_ASSERT).append(' ').append(++ordinal);
                at += ASSERT.length();
            } else {
                out.append(letter);
                at++;
            }
        }
        String numbered = out.toString();
        return saysTheSameThing(written, numbered, firstOrdinal) ? numbered : null;
    }

    private static boolean opensAnAssertion(String written, int at) {
        if (!written.startsWith(ASSERT, at)) {
            return false;
        }
        if (at > 0 && !isSeparator(written.charAt(at - 1))) {
            return false;
        }
        int after = at + ASSERT.length();
        return after >= written.length() || isSeparator(written.charAt(after));
    }

    private static boolean isSeparator(char letter) {
        return Character.isWhitespace(letter) || "[]()".indexOf(letter) >= 0;
    }

    private static int copyToEndOfLine(String written, int at, StringBuilder out) {
        while (at < written.length() && written.charAt(at) != '\n') {
            out.append(written.charAt(at++));
        }
        return at;
    }

    private static int copyQuoted(String written, int at, StringBuilder out) {
        out.append(written.charAt(at++));
        while (at < written.length() && written.charAt(at) != '"') {
            if (written.charAt(at) == '^' && at + 1 < written.length()) {
                out.append(written.charAt(at++));
            }
            out.append(written.charAt(at++));
        }
        return at < written.length() ? at + copyOne(written, at, out) : at;
    }

    private static int copyBraced(String written, int at, StringBuilder out) {
        int depth = 0;
        do {
            char letter = written.charAt(at);
            if (letter == '^' && at + 1 < written.length()) {
                out.append(written.charAt(at++));
            } else if (letter == '{') {
                depth++;
            } else if (letter == '}') {
                depth--;
            }
            out.append(written.charAt(at++));
        } while (at < written.length() && depth > 0);
        return at;
    }

    private static int copyOne(String written, int at, StringBuilder out) {
        out.append(written.charAt(at));
        return 1;
    }

    /**
     * Whether the numbered source reads as the source it came from, allowing
     * for the numbers.
     */
    private static boolean saysTheSameThing(
            String written, String numbered, int firstOrdinal) {

        BlockValue before = Transcoder.transcode(written).values().orElse(null);
        BlockValue after = Transcoder.transcode(numbered).values().orElse(null);
        return before != null && after != null
                && sameValues(before.remaining(), after.remaining(), new int[] {firstOrdinal});
    }

    private static boolean sameValues(
            List<Value> before, List<Value> after, int[] next) {

        int here = 0;
        for (Value one : before) {
            if (here >= after.size()) {
                return false;
            }
            Value other = after.get(here++);
            if (one instanceof WordValue word && ASSERT.equals(word.spelling())) {
                if (!(other instanceof WordValue numbered)
                        || !NUMBERED_ASSERT.equals(numbered.spelling())
                        || here >= after.size()
                        || !(after.get(here++) instanceof IntegerValue which)
                        || which.magnitude() != ++next[0]) {
                    return false;
                }
                continue;
            }
            if (one instanceof BlockValue nested) {
                if (!(other instanceof BlockValue alsoNested)
                        || nested.datatype() != alsoNested.datatype()
                        || !sameValues(nested.remaining(), alsoNested.remaining(), next)) {
                    return false;
                }
                continue;
            }
            if (one.datatype() != other.datatype() || !one.toString().equals(other.toString())) {
                return false;
            }
        }
        return here == after.size();
    }

    /**
     * How many {@code --assert} words a run of values holds inside blocks.
     *
     * <p>The slicer takes top-level values, so an assertion written inside a
     * FOREACH or an IF was never anybody's step: not sliced, not run, not
     * counted. Thirty-seven of Rebol's files put assertions there, and
     * crypt-port-camelia-test.r3 puts all four of its inside two nested
     * loops, so it reported zero of four while a real Rebol ran them two
     * thousand times.
     */
    private static int assertionsNestedIn(List<Value> values) {
        int found = 0;
        for (Value value : values) {
            if (value instanceof BlockValue block) {
                found += assertionsNestedIn(block.remaining());
            } else if (value instanceof WordValue word && ASSERT.equals(word.spelling())) {
                found++;
            }
        }
        return found;
    }

    static SuiteFile read(Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        String name = path.getFileName().toString();
        try {
            String readable = Transcoder.transcode(source).succeeded()
                    ? source
                    : longestReadablePrefix(source);
            return Transcoder.transcode(readable).values()
                    .map(block -> build(name, readable, block.remaining(),
                            Transcoder.topLevelSpans(readable)))
                    .orElseGet(() -> new SuiteFile(name, List.of(), List.of()));
        } catch (RuntimeException thrown) {
            throw new IllegalStateException(
                    "reading " + name + " threw " + thrown.getClass().getSimpleName()
                            + ": " + thrown.getMessage()
                            + ". The reader is meant to answer a REBOL error for source "
                            + "it cannot take in, so a Java exception here escapes the "
                            + "interpreter and takes the whole run with it rather than "
                            + "failing one file", thrown);
        }
    }

    private static SuiteFile build(String name, String source,
            List<Value> values, List<Transcoder.SourceSpan> spans) {
        if (values.size() != spans.size()) {
            throw new IllegalStateException(
                    "the reader gave " + values.size() + " values and " + spans.size()
                            + " spans for " + name + ", so no assertion can be trusted "
                            + "to be the one the file wrote");
        }
        List<Step> steps = stepsIn(name, source, values, spans);
        List<Assertion> everyOne = new ArrayList<>();
        for (Step step : steps) {
            if (step.isAssertion()) {
                everyOne.add(step.assertion());
            }
            everyOne.addAll(step.nested());
        }
        return new SuiteFile(name, List.copyOf(everyOne), steps);
    }

    /**
     * As much of a file as the reader can take in.
     *
     * <p>Walks forward rather than bisecting. Bisection needs the question
     * "does this prefix read" to stay false once it turns false, and it does
     * not: a prefix cut in the middle of a multi-line block fails for the
     * missing bracket rather than for anything wrong, and a longer prefix
     * that closes the block reads again. Bisecting that predicate stops at
     * the first open bracket it lands on -- it cost error-test.r3 thirteen
     * assertions the reader could already have had, and it named line 14 of
     * copy-test.r3 as the stop when the refusal is on line 30.
     */
    private static String longestReadablePrefix(String source) {
        List<String> lines = source.lines().toList();
        int readable = 0;
        for (int upTo = 1; upTo <= lines.size(); upTo++) {
            if (Transcoder.transcode(String.join("\n", lines.subList(0, upTo))).succeeded()) {
                readable = upTo;
            }
        }
        return String.join("\n", lines.subList(0, readable));
    }

    private static List<Step> stepsIn(String file, String source,
            List<Value> values, List<Transcoder.SourceSpan> spans) {
        List<Step> found = new ArrayList<>();
        String group = "(no group)";
        String test = "(no test)";
        int ordinal = 0;
        boolean nextAssertionDescribesRed = false;
        int at = skipScriptHeader(values);

        while (at < values.size()) {
            Value current = values.get(at);
            if (!(current instanceof WordValue word) || !isHarnessWord(current)) {
                List<Value> run = valuesUntilNextHarnessWord(values, at);
                ordinal = addSetupSteps(found, file, group, test, ordinal,
                        source, values, spans, at, run.size());
                at += Math.max(1, run.size());
                continue;
            }
            String spelling = word.spelling();
            List<Value> until = valuesUntilNextHarnessWord(values, at + 1);
            int next = at + 1 + until.size();

            switch (spelling) {
                case START_GROUP, TEST -> {
                    if (spelling.equals(START_GROUP)) {
                        group = onlyString(until, group);
                    } else {
                        test = onlyString(until, test);
                    }
                    int howMany = Math.max(0, until.size() - 1);
                    ordinal = addSetupSteps(found, file, group, test, ordinal,
                            source, values, spans, at + 2, howMany);
                }
                case RED_ONLY -> nextAssertionDescribesRed = true;
                case ASSERT -> {
                    ordinal++;
                    String written = sourceOf(source, spans, at + 1, until.size());
                    Assertion asserted = new Assertion(file, group, test, ordinal,
                            written, beginningOf(spans, at + 1),
                            endOf(spans, at + 1, until.size()),
                            nextAssertionDescribesRed);
                    nextAssertionDescribesRed = false;
                    int began = ordinal;
                    List<Assertion> alsoInside = new ArrayList<>();
                    for (int more = assertionsNestedIn(until); more > 0; more--) {
                        ordinal++;
                        alsoInside.add(new Assertion(file, group, test, ordinal,
                                written, beginningOf(spans, at + 1),
                                endOf(spans, at + 1, until.size())));
                    }
                    found.add(new Step(asserted, null, List.copyOf(alsoInside),
                            alsoInside.isEmpty() ? null
                                    : numberedSource(written, began)));
                }
                default -> ordinal = addSetupSteps(found, file, group, test, ordinal,
                        source, values, spans, at + 1, until.size());
            }
            at = next;
        }
        return List.copyOf(found);
    }

    /**
     * The {@code Rebol [...]} header is data, not code, and evaluating it
     * would call whatever REBOL is bound to.
     */
    private static int skipScriptHeader(List<Value> values) {
        if (values.size() >= 2
                && values.get(0) instanceof WordValue word
                && word.canonical().equals("rebol")
                && values.get(1) instanceof BlockValue) {
            return 2;
        }
        return 0;
    }


    /**
     * Turns a run of setup into steps, one per expression it was written as.
     *
     * <p>Left whole, one raise takes the rest of the run with it, and a run is
     * everything up to the next *top-level* dialect word. codecs-test.r3 is a
     * sequence of {@code if find codecs 'wav [...]},
     * {@code if find codecs 'der [...]}, {@code if find codecs 'crt [...]}
     * whose dialect words are all nested inside those blocks, so the whole
     * tail of the file was one step: the DER codec raising took the WAV, CRT
     * and SWF groups with it, and 187 assertions were recorded as failures of
     * the port when they had never been asked.
     *
     * <p>Every place that built a setup step used to write these six lines out
     * again, and the first attempt at cutting changed only one of the three.
     * The one it missed was the one that mattered -- {@code ===end-group===}
     * falls to the default arm, and what follows it is the tail of the file.
     */
    private static int addSetupSteps(List<Step> found, String file, String group,
            String test, int ordinal, String source, List<Value> values,
            List<Transcoder.SourceSpan> spans, int from, int count) {

        for (int[] piece : expressionsIn(source, values, spans, from, count)) {
            List<Value> body = values.subList(piece[0], piece[0] + piece[1]);
            String setup = sourceOf(source, spans, piece[0], piece[1]);
            int began = ordinal;
            List<Assertion> nested = new ArrayList<>();
            for (int more = assertionsNestedIn(body); more > 0; more--) {
                ordinal++;
                nested.add(new Assertion(file, group, test, ordinal, setup,
                        beginningOf(spans, piece[0]), endOf(spans, piece[0], piece[1])));
            }
            found.add(new Step(null, setup, List.copyOf(nested),
                    nested.isEmpty() ? null : numberedSource(setup, began)));
        }
        return ordinal;
    }

    /**
     * A run of setup, cut into the separate expressions it was written as.
     *
     * <p>The cut is where a word begins a line, because that is how these
     * files are written and because nothing here knows REBOL's arity well
     * enough to find an expression boundary properly.
     *
     * <p>Only a *word* may open one. Cutting at any value that begins a line
     * splits {@code switch-fun: func [/local i][} from its body block
     * whenever the bracket starts a line, and both halves read perfectly well
     * on their own: one is a function of one argument, the other is a block.
     * Reading is not the same as meaning the same thing, and 32 assertions
     * that had been passing said so.
     *
     * <p>The position arrives counted in code points, as every offset the
     * reader hands out does. Indexing the source in Java's sixteen-bit units
     * instead put every position after the file's first emoji in the middle
     * of some other line, so the cut never fired and left no trace of not
     * having fired.
     *
     * <p>It is still a guess, so every piece has to read on its own and a run
     * with a piece that does not is left exactly as it was.
     *
     * @return {@code {from, count\}} pairs into the value list
     */
    private static List<int[]> expressionsIn(String source, List<Value> values,
            List<Transcoder.SourceSpan> spans, int from, int count) {

        List<int[]> whole = List.of(new int[] {from, count});
        if (count <= 1) {
            return whole;
        }
        List<Integer> starts = new ArrayList<>();
        for (int at = from; at < from + count; at++) {
            if (at == from || values.get(at) instanceof WordValue
                    && beginsALine(source, beginningOf(spans, at))) {
                starts.add(at);
            }
        }
        if (starts.size() <= 1) {
            return whole;
        }
        List<int[]> pieces = new ArrayList<>();
        for (int which = 0; which < starts.size(); which++) {
            int begins = starts.get(which);
            int ends = which + 1 < starts.size() ? starts.get(which + 1) : from + count;
            pieces.add(new int[] {begins, ends - begins});
        }
        return pieces.stream().allMatch(piece ->
                readsOnItsOwn(sourceOf(source, spans, piece[0], piece[1])))
                ? pieces
                : whole;
    }

    /** Whether only whitespace stands between the start of the line and here. */
    private static boolean beginsALine(String source, int codePointsIn) {
        int at = source.offsetByCodePoints(0, codePointsIn);
        for (int back = at - 1; back >= 0; back--) {
            char letter = source.charAt(back);
            if (letter == '\n') {
                return true;
            }
            if (letter != ' ' && letter != '\t' && letter != '\r') {
                return false;
            }
        }
        return true;
    }

    private static boolean readsOnItsOwn(String piece) {
        return piece.isBlank() || Transcoder.transcode(piece).succeeded();
    }

    private static List<Value> valuesUntilNextHarnessWord(List<Value> values, int from) {
        int at = from;
        while (at < values.size() && !isHarnessWord(values.get(at))) {
            at++;
        }
        return values.subList(from, at);
    }

    private static List<Value> afterTheName(List<Value> values) {
        return values.isEmpty() ? values : values.subList(1, values.size());
    }

    private static String onlyString(List<Value> values, String fallback) {
        return values.isEmpty() ? fallback : Molder.form(values.get(0));
    }

    /**
     * The source text of a run of top-level expressions, as written.
     *
     * <p>This used to mold the values back into source, which is lossy in
     * REBOL and equally lossy in R3: molding
     * {@code 1.7976931348623157e308} gives fifteen digits, and reading
     * that back gives {@code 1.#INF}. Sixteen assertions were being run
     * in a form the file never contained, and a measuring tool built on
     * this then reported that R3 fails its own tests.
     */
    private static int beginningOf(List<Transcoder.SourceSpan> spans, int at) {
        return at < spans.size() ? spans.get(at).from() : 0;
    }

    private static int endOf(List<Transcoder.SourceSpan> spans, int from, int count) {
        int last = Math.min(from + count, spans.size()) - 1;
        return last >= from ? spans.get(last).to() : beginningOf(spans, from);
    }

    private static String sourceOf(String source,
            List<Transcoder.SourceSpan> spans, int from, int count) {
        return Transcoder.textOf(source, spans, from, count);
    }
}
