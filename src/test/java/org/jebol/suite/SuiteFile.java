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

record SuiteFile(String name, List<Assertion> assertions, List<Step> steps) {

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

        String sourceToRun() {
            return numberedSetup == null
                    ? (assertion != null ? assertion.source() : setup)
                    : numberedSetup;
        }
    }

    record Assertion(String file, String group, String test, int ordinal, String source,
            int from, int to, boolean redOnly) {

        Assertion(String file, String group, String test, int ordinal, String source,
                int from, int to) {
            this(file, group, test, ordinal, source, from, to, false);
        }

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

    private static final String RED_ONLY = "--red--";

    private static boolean isHarnessWord(Value value) {
        return value instanceof WordValue word && switch (word.spelling()) {
            case START_FILE, END_FILE, START_GROUP, END_GROUP, TEST, ASSERT,
                    RED_ONLY -> true;
            default -> word.spelling().startsWith("--assertf");
        };
    }


    static final String NUMBERED_ASSERT = "--assert-numbered";

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
                        || !(after.get(here++) instanceof IntegerValue(long magnitude))
                        || magnitude != ++next[0]) {
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

    private static int skipScriptHeader(List<Value> values) {
        if (values.size() >= 2
                && values.get(0) instanceof WordValue word
                && word.canonical().equals("rebol")
                && values.get(1) instanceof BlockValue) {
            return 2;
        }
        return 0;
    }


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
