package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SeriesSearch {

    private final RebolSeries series;
    private final Value wanted;
    private final Set<String> refinements;
    private final int limit;
    private final long stride;
    private final Wildcards wildcards;

    public SeriesSearch(
            RebolSeries series, Value wanted, Set<String> refinements,
            Optional<Value> part, Optional<Value> skip, Optional<Value> wild) {
        this.series = series;
        this.wanted = wanted;
        this.refinements = refinements;
        this.stride = skip.map(size -> ((IntegerValue) size).magnitude()).orElse(1L);
        this.limit = part.map(range -> (int) series.countUpTo(range))
                .orElse(Integer.MAX_VALUE);
        this.wildcards = Wildcards.STARS_AND_QUESTION_MARKS.orThoseChosenBy(wild);
    }

    public long stride() {
        return stride;
    }

    public boolean stridesForwardByLessThanOne() {
        return refinements.contains("skip") && stride < 1 && !refinements.contains("reverse");
    }

    public int position() {
        boolean forcedToSingleStep = refinements.contains("reverse")
                || refinements.contains("last");
        return forcedToSingleStep || stride == 1 || stride == 0
                ? positionOfMatch()
                : positionOfMatchInRecords();
    }

    public int end() {
        List<Value> items = series.head().items();
        return limit < 0
                ? items.size()
                : (int) Math.min(items.size(), (long) series.index() - 1 + limit);
    }

    public int lengthMatchedAt(int found) {
        if (series instanceof AnyStringValue patterned && refinements.contains("any")) {
            int from = found - 1;
            int reached = wildcards.patternEnd(patterned.head().text(), from, end(),
                    Molder.form(wanted), refinements.contains("case"));
            return reached < 0 ? 1 : reached - from;
        }
        if (wanted instanceof BitsetValue) {
            return 1;
        }
        if (series instanceof BinaryValue && wanted instanceof BinaryValue run) {
            return run.lengthFromHere();
        }
        if (series instanceof BinaryValue
                && (wanted instanceof CharacterValue || wanted instanceof AnyStringValue)) {
            return theBytesThatSpell().size();
        }
        if (series instanceof AnyBlockValue
                && wanted instanceof BlockValue run
                && !refinements.contains("only")) {
            return run.remaining().size();
        }
        return series instanceof AnyStringValue && !refinements.contains("only")
                ? theCharactersIn(Molder.form(wanted))
                : 1;
    }

    private int positionOfMatch() {
        boolean lookingBehind = refinements.contains("reverse");
        boolean takingTheLast = refinements.contains("last");
        List<Value> items = series.head().items();
        int here = series.index() - 1;
        int end = end();
        int step = 1;
        int start = here;
        int at = here;
        if (lookingBehind || takingTheLast) {
            step = -1;
            if (takingTheLast) {
                at = end - widthOfNeedle();
            } else {
                start = 0;
                at = here - 1;
            }
        }
        for (; at >= start && at < end; at += step) {
            if (matchesHere(items, at, end)) {
                return at + 1;
            }
            if (refinements.contains("match")) {
                break;
            }
        }
        return -1;
    }

    private int widthOfNeedle() {
        if (refinements.contains("only")) {
            return 1;
        }
        if (series instanceof AnyBlockValue) {
            return wanted instanceof BlockValue run
                    ? run.remaining().size()
                    : 1;
        }
        if (wanted instanceof BitsetValue) {
            return 1;
        }
        if (wanted instanceof CharacterValue && !(series instanceof BinaryValue)) {
            return 1;
        }
        return itemsOfNeedle().size();
    }

    private boolean matchesHere(List<Value> items, int at, int end) {
        if (series instanceof AnyStringValue text && refinements.contains("any")) {
            return wildcards.patternEnd(text.head().text(), at, end, Molder.form(wanted),
                    refinements.contains("case")) >= 0;
        }
        if ((wanted instanceof Datatype || wanted instanceof TypesetValue)
                && !refinements.contains("only")) {
            return wanted instanceof Datatype represents
                    ? items.get(at).datatype() == represents
                    : ((TypesetValue) wanted).holds(items.get(at).datatype());
        }
        if (wanted instanceof BlockValue run
                && !refinements.contains("only")) {
            return runMatchesAt(items, at, run.remaining(), refinements.contains("same"));
        }
        if (refinements.contains("same")
                && !(series instanceof AnyStringValue)
                && !(series instanceof BinaryValue)) {
            return refinements.contains("only")
                    ? Comparison.isSameValue(items.get(at), wanted)
                    : sameRunAt(items, at);
        }
        if (series instanceof AnyStringValue || series instanceof BinaryValue) {
            return textRunMatchesAt(items, at);
        }
        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue(int codepoint)
                    && members.holds(codepoint);
        }
        return matches(items.get(at), wanted, refinements.contains("case"));
    }

    private boolean textRunMatchesAt(List<Value> items, int at) {
        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue(int codepoint)
                    && members.holds(codepoint);
        }
        List<Value> run = itemsOfNeedle();
        if (at + run.size() > items.size()) {
            return false;
        }
        boolean mindingCase = refinements.contains("case") || refinements.contains("same");
        for (int step = 0; step < run.size(); step++) {
            if (!matches(items.get(at + step), run.get(step), mindingCase)) {
                return false;
            }
        }
        return true;
    }

    private List<Value> theBytesThatSpell() {
        if (wanted instanceof CharacterValue(int codepoint) && codepoint <= 0xFF) {
            return List.of(IntegerValue.of(codepoint));
        }
        String text = wanted instanceof CharacterValue(int codepoint)
                ? new String(Character.toChars(codepoint))
                : ((AnyStringValue) wanted).text();
        List<Value> octets = new ArrayList<>();
        for (byte octet : text.getBytes(StandardCharsets.UTF_8)) {
            octets.add(IntegerValue.of(octet & 0xFF));
        }
        return octets;
    }

    private List<Value> itemsOfNeedle() {
        if (series instanceof BinaryValue && wanted instanceof BinaryValue bytes) {
            return bytes.items();
        }
        if (series instanceof BinaryValue
                && (wanted instanceof CharacterValue || wanted instanceof AnyStringValue)) {
            return theBytesThatSpell();
        }
        if (wanted instanceof CharacterValue letter) {
            return List.of(letter);
        }
        if (series instanceof BinaryValue && wanted instanceof IntegerValue byteValue) {
            return List.of(byteValue);
        }
        return Molder.form(wanted).codePoints()
                .<Value>mapToObj(series instanceof BinaryValue
                        ? IntegerValue::of
                        : CharacterValue::of)
                .toList();
    }

    private boolean runMatchesAt(
            List<Value> items, int at, List<Value> run, boolean mindingIdentity) {
        if (at + run.size() > items.size()) {
            return false;
        }
        for (int step = 0; step < run.size(); step++) {
            boolean same = mindingIdentity
                    ? Comparison.isSameValue(items.get(at + step), run.get(step))
                    : Comparison.looselyEqual(items.get(at + step), run.get(step));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private boolean sameRunAt(List<Value> items, int at) {
        List<Value> run = wanted instanceof BlockValue block
                ? block.remaining()
                : List.of(wanted);
        return runMatchesAt(items, at, run, true);
    }

    private int positionOfMatchInRecords() {
        boolean backwards = stride < 0 || refinements.contains("reverse")
                || refinements.contains("last");
        int width = (int) Math.abs(stride);
        List<Value> items = series.head().items();
        int from = backwards ? series.index() - 2 : series.index() - 1;
        int end = end();
        for (int at = from; at >= 0 && at < end; at += backwards ? -width : width) {
            if (matchesAtRecord(items, at, end)) {
                return at + 1;
            }
        }
        return -1;
    }

    private boolean matchesAtRecord(List<Value> items, int at, int end) {
        if (series instanceof AnyStringValue text && refinements.contains("any")) {
            return wildcards.patternEnd(text.head().text(), at, end, Molder.form(wanted),
                    refinements.contains("case")) >= 0;
        }
        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue(int codepoint)
                    && members.holds(codepoint);
        }
        if (wanted instanceof BlockValue run
                && !refinements.contains("only")) {
            return runMatchesAt(items, at, run.remaining(), refinements.contains("same"));
        }
        if (wanted instanceof AnyStringValue needle && !refinements.contains("only")) {
            return theTextRunMatchesIgnoringCaseAt(items, at, needle);
        }
        return matches(items.get(at), wanted, refinements.contains("case"));
    }

    private boolean theTextRunMatchesIgnoringCaseAt(
            List<Value> items, int at, AnyStringValue needle) {
        int[] sought = (needle instanceof StringValue
                ? needle.text()
                : Molder.form(needle)).codePoints().toArray();
        if (at + sought.length > items.size()) {
            return false;
        }
        for (int step = 0; step < sought.length; step++) {
            if (!(items.get(at + step) instanceof CharacterValue(int codepoint))
                    || Character.toLowerCase(codepoint)
                            != Character.toLowerCase(sought[step])) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Value item, Value sought, boolean mindingCase) {
        if (item instanceof HandleValue found && sought instanceof HandleValue looking) {
            return found.compareWith(looking) == 0;
        }
        return mindingCase
                ? Comparison.identicallyEqual(item, sought)
                : Comparison.looselyEqual(item, sought);
    }

    private int theCharactersIn(String text) {
        return text.codePointCount(0, text.length());
    }
}
