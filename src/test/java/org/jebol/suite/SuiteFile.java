package org.jebol.suite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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
    record Step(Assertion assertion, String setup) {

        boolean isAssertion() {
            return assertion != null;
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
            int from, int to) {

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

    private static boolean isHarnessWord(Value value) {
        return value instanceof WordValue word && switch (word.spelling()) {
            case START_FILE, END_FILE, START_GROUP, END_GROUP, TEST, ASSERT -> true;
            // --assertf~= compares floats approximately. It is a different
            // question from the one this runner asks, and there are only
            // seventeen of them, so they are left out rather than guessed at.
            default -> word.spelling().startsWith("--assertf");
        };
    }

    static SuiteFile read(Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        String name = path.getFileName().toString();
        String readable = Transcoder.transcode(source).succeeded()
                ? source
                : longestReadablePrefix(source);
        return Transcoder.transcode(readable).values()
                .map(block -> build(name, readable, block.remaining(),
                        Transcoder.topLevelSpans(readable)))
                .orElseGet(() -> new SuiteFile(name, List.of(), List.of()));
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
        return new SuiteFile(
                name,
                steps.stream().filter(Step::isAssertion).map(Step::assertion).toList(),
                steps);
    }

    /**
     * As much of a file as the reader can take in.
     *
     * <p>A file used to be all-or-nothing: one construct the reader could
     * not manage anywhere in it hid every assertion in the file, including
     * the ones before the problem. series-test.r3 spent a while reporting
     * zero of 1,532 while failing on line 2,000.
     *
     * <p>Bisects on lines rather than scanning, because these files run to
     * a few thousand lines and a linear scan transcodes the whole prefix
     * every time. What is dropped is reported by SuiteCoverageTest, so
     * nothing goes missing quietly.
     */
    private static String longestReadablePrefix(String source) {
        List<String> lines = source.lines().toList();
        int readable = 0;
        int low = 0;
        int high = lines.size();
        while (low <= high) {
            int middle = (low + high) / 2;
            if (Transcoder.transcode(String.join("\n", lines.subList(0, middle))).succeeded()) {
                readable = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
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
        int at = skipScriptHeader(values);

        while (at < values.size()) {
            Value current = values.get(at);
            if (!(current instanceof WordValue word) || !isHarnessWord(current)) {
                // Anything outside the dialect is setup the assertions below
                // it depend on, so it is kept in order rather than dropped.
                List<Value> run = valuesUntilNextHarnessWord(values, at);
                found.add(new Step(null, sourceOf(source, spans, at, run.size())));
                at += Math.max(1, run.size());
                continue;
            }
            String spelling = word.spelling();
            List<Value> until = valuesUntilNextHarnessWord(values, at + 1);
            int next = at + 1 + until.size();

            switch (spelling) {
                // The name is the first value; anything after it and before
                // the next dialect word is setup the assertions below lean
                // on. Taking only the name and dropping the rest is what
                // lost every `a:` and `obj:` in the suite.
                case START_GROUP -> {
                    group = onlyString(until, group);
                    found.add(new Step(null,
                            sourceOf(source, spans, at + 2, Math.max(0, until.size() - 1))));
                }
                case TEST -> {
                    test = onlyString(until, test);
                    found.add(new Step(null,
                            sourceOf(source, spans, at + 2, Math.max(0, until.size() - 1))));
                }
                case ASSERT -> {
                    ordinal++;
                    found.add(new Step(new Assertion(file, group, test, ordinal,
                            sourceOf(source, spans, at + 1, until.size()),
                            beginningOf(spans, at + 1),
                            endOf(spans, at + 1, until.size())), null));
                }
                default -> {
                    // start-file, end-file, end-group and the float variants
                    // carry nothing this runner needs.
                }
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
