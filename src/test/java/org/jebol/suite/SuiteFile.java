package org.jebol.suite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    record Step(Assertion assertion, String setup, List<Assertion> nested) {

        Step(Assertion assertion, String setup) {
            this(assertion, setup, List.of());
        }

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
            default -> word.spelling().startsWith("--assertf");
        };
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
        int at = skipScriptHeader(values);

        while (at < values.size()) {
            Value current = values.get(at);
            if (!(current instanceof WordValue word) || !isHarnessWord(current)) {
                List<Value> run = valuesUntilNextHarnessWord(values, at);
                String setup = sourceOf(source, spans, at, run.size());
                List<Assertion> nested = new ArrayList<>();
                for (int more = assertionsNestedIn(run); more > 0; more--) {
                    ordinal++;
                    nested.add(new Assertion(file, group, test, ordinal, setup,
                            beginningOf(spans, at), endOf(spans, at, run.size())));
                }
                found.add(new Step(null, setup, List.copyOf(nested)));
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
                    String setup = sourceOf(source, spans, at + 2, howMany);
                    List<Assertion> nested = new ArrayList<>();
                    List<Value> body = until.isEmpty()
                            ? List.of()
                            : until.subList(1, until.size());
                    for (int more = assertionsNestedIn(body); more > 0; more--) {
                        ordinal++;
                        nested.add(new Assertion(file, group, test, ordinal, setup,
                                beginningOf(spans, at + 2), endOf(spans, at + 2, howMany)));
                    }
                    found.add(new Step(null, setup, List.copyOf(nested)));
                }
                case ASSERT -> {
                    ordinal++;
                    String written = sourceOf(source, spans, at + 1, until.size());
                    Assertion asserted = new Assertion(file, group, test, ordinal,
                            written, beginningOf(spans, at + 1),
                            endOf(spans, at + 1, until.size()));
                    List<Assertion> alsoInside = new ArrayList<>();
                    for (int more = assertionsNestedIn(until); more > 0; more--) {
                        ordinal++;
                        alsoInside.add(new Assertion(file, group, test, ordinal,
                                written, beginningOf(spans, at + 1),
                                endOf(spans, at + 1, until.size())));
                    }
                    found.add(new Step(asserted, null, List.copyOf(alsoInside)));
                }
                default -> {
                    String setup = sourceOf(source, spans, at + 1, until.size());
                    List<Assertion> nested = new ArrayList<>();
                    for (int more = assertionsNestedIn(until); more > 0; more--) {
                        ordinal++;
                        nested.add(new Assertion(file, group, test, ordinal, setup,
                                beginningOf(spans, at + 1),
                                endOf(spans, at + 1, until.size())));
                    }
                    found.add(new Step(null, setup, List.copyOf(nested)));
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
