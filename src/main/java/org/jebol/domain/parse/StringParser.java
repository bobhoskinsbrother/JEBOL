package org.jebol.domain.parse;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * PARSE over a string, which matches substrings rather than characters.
 *
 * <p>{@code parse "abc" ["a" "b" "c"]} matches, and it would not if a rule
 * matched one character at a time.
 *
 * <p>Whitespace is never skipped. REBOL 2 skipped it unless you passed
 * /all, and this class was written from documentation describing that;
 * R3 reversed the default and dropped the refinement, so {@code parse
 * "a b c" ["a" "b" "c"]} is false and the rule has to account for the
 * spaces itself. There is no longer any flag, because there is no longer
 * anything that could turn skipping on.
 *
 * <p>Kept apart from {@link Parser} rather than generalised into it. The two
 * share their keywords and nothing else: one walks values and the other walks
 * characters, and a single class doing both would spend most of its length
 * asking which it was.
 */
public final class StringParser {

    private final Evaluator evaluator;
    private final Context context;
    private String text;

    /**
     * The series being matched, when there is one to change.
     *
     * <p>REMOVE takes what it matched out of the input, so the parser needs
     * the series and not only its text. This walked a copy for a long time,
     * which is why REMOVE worked on a block parse and not on a string one.
     */
    private SeriesValue source;

    /**
     * Whether the input is bytes rather than characters.
     *
     * <p>A binary is walked exactly as a string is, with each byte
     * standing in for a character, so every rule word works unchanged.
     * What differs is only what comes back out: a span is a binary, a
     * single item is the byte's number, and a rule written as a binary is
     * compared byte for byte rather than as the text "01".
     */
    private boolean walkingBytes;

    /**
     * Whether the rules from here on mind case.
     *
     * <p>A mode rather than a refinement, set by CASE and unset by
     * NO-CASE, which is what lets one rule mind case in one place and not
     * in another. Parsing folds case until something says otherwise.
     */
    private boolean mindingCase;

    private int position;

    /**
     * The collections COLLECT has open, innermost last.
     *
     * <p>A stack because collects nest, and the answer to
     * {@code parse "ab" [collect [collect [keep skip]]]} depends on which
     * one a KEEP belongs to.
     */
    private final java.util.Deque<List<Value>> collecting = new java.util.ArrayDeque<>();

    /** What the outermost COLLECT gathered, or null if there was none. */
    private List<Value> gathered;

    private StringParser(Evaluator evaluator, Context context, SeriesValue source) {
        this.evaluator = evaluator;
        this.context = context;
        this.source = source;
        this.walkingBytes = source instanceof BinaryValue;
        this.text = textOfSeries(source);
    }

    private void adoptInput(SeriesValue newInput) {
        this.source = newInput;
        this.walkingBytes = newInput instanceof BinaryValue;
        this.text = textOfSeries(newInput);
        this.position = 0;
    }

    /**
     * Takes one item out of the input, whichever kind of series it is.
     *
     * <p>A string and a binary keep their contents in different storage
     * and neither reaches the other through {@link SeriesValue}, so the
     * two ends of every change to the input go through here.
     */
    private void removeFromSource(int oneBasedIndex) {
        switch (source) {
            case StringValue text0 -> text0.storage().removeAt(oneBasedIndex);
            case BinaryValue bytes -> bytes.storage().removeAt(oneBasedIndex);
            default -> { }
        }
    }

    /** Puts one item into the input, whichever kind of series it is. */
    private void insertIntoSource(int oneBasedIndex, int item) {
        switch (source) {
            case StringValue text0 -> text0.storage().insertAt(oneBasedIndex, item);
            case BinaryValue bytes -> bytes.storage().insertAt(oneBasedIndex, item);
            default -> { }
        }
    }

    /**
     * A series as the characters this walker steps through.
     *
     * <p>A binary's bytes become code points 0 to 255, one for one, so
     * the whole of the matching machinery works on a binary without
     * knowing it is one. Nothing outside this class sees the standing-in:
     * every value handed back is built from the bytes again.
     */
    private static String textOfSeries(SeriesValue series) {
        if (series instanceof StringValue text) {
            return text.text();
        }
        BinaryValue bytes = (BinaryValue) series;
        StringBuilder standingIn = new StringBuilder();
        for (int at = 0; at < bytes.lengthFromHere(); at++) {
            standingIn.append((char) bytes.storage().at(bytes.index() + at));
        }
        return standingIn.toString();
    }

    /**
     * What a string parse answered: a logic, or the block COLLECT built.
     *
     * <p>Leftover input stops being a failure once COLLECT is involved,
     * because the question is no longer whether the rule accounted for
     * all of it.
     */
    public static Value answer(
            Evaluator evaluator, Context context, SeriesValue source, BlockValue rule,
            boolean mindingCase) {

        StringParser parser = new StringParser(evaluator, context, source);
        parser.mindingCase = mindingCase || parser.walkingBytes;
        boolean matched = parser.matchSequence(rule.remaining());
        if (parser.gathered != null) {
            return BlockValue.block(parser.gathered);
        }
        return LogicValue.of(matched && parser.atEnd());
    }

    /** Whether the whole string matches the whole rule. */
    public static boolean matches(
            Evaluator evaluator, Context context, SeriesValue source, BlockValue rule) {
        return matches(evaluator, context, source, rule, false);
    }

    /**
     * The same, starting with case either minded or not.
     *
     * <p>{@code parse/case} is the CASE rule word applied before the rule
     * starts rather than inside it, so it needs no separate machinery: it
     * sets the mode the rule words already switch between.
     */
    public static boolean matches(
            Evaluator evaluator, Context context, SeriesValue source, BlockValue rule,
            boolean mindingCase) {

        StringParser parser = new StringParser(evaluator, context, source);
        parser.mindingCase = mindingCase || parser.walkingBytes;
        return parser.matchSequence(rule.remaining()) && parser.atEnd();
    }

    private boolean atEnd() {
        return position >= text.length();
    }

    /** CASE and NO-CASE last until the other one appears. */
    private int setCaseMode(boolean minding) {
        mindingCase = minding;
        return 1;
    }

    /**
     * CHANGE: match the rule after it and put a value where the match was.
     *
     * <p>REMOVE and an insertion in one step, so the input may be a
     * different length afterwards.
     */
    private int changeMatched(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            return NO_MATCH;
        }
        Integer markOffset = sameStorageOffset(rules.get(at + 1));
        if (markOffset != null) {
            if (at + 2 >= rules.size()) {
                return NO_MATCH;
            }
            int begin = Math.min(position, markOffset);
            int count = Math.abs(position - markOffset);
            for (int taken = begin + count; taken > begin; taken--) {
                removeFromSource(source.index() + taken - 1);
            }
            String swapped = replacementFor(rules.get(at + 2));
            int[] written = swapped.codePoints().toArray();
            for (int added = 0; added < written.length; added++) {
                insertIntoSource(source.index() + begin + added, written[added]);
            }
            text = textOfSeries(source);
            position = begin + written.length;
            return 3;
        }
        int span = ruleSpan(rules, at + 1);
        int replacementAt = at + 1 + span;
        if (replacementAt >= rules.size()) {
            return NO_MATCH;
        }
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        for (int taken = position; taken > before; taken--) {
            removeFromSource(source.index() + taken - 1);
        }
        String replacement = replacementFor(rules.get(replacementAt));
        int[] codepoints = replacement.codePoints().toArray();
        for (int added = 0; added < codepoints.length; added++) {
            insertIntoSource(source.index() + before + added, codepoints[added]);
        }
        text = textOfSeries(source);
        position = before + codepoints.length;
        return 1 + span + 1;
    }

    /**
     * The offset a word names when it holds a position in the series being
     * parsed, or null when it names anything else. What lets CHANGE and
     * REMOVE take a marked span instead of a rule.
     */
    private Integer sameStorageOffset(Value item) {
        if (!(item instanceof WordValue word)
                || (word.datatype() != Datatype.WORD
                        && word.datatype() != Datatype.GET_WORD)
                || (word.datatype() == Datatype.WORD
                        && PARSE_COMMANDS.contains(word.canonical()))) {
            return null;
        }
        Context holder = word.isBound() ? word.binding() : context;
        if (!holder.knows(word.canonical())) {
            return null;
        }
        return holder.slotFor(word.canonical()).value() instanceof SeriesValue marked
                && marked.sharesStorageWith(source)
                ? marked.index() - source.index()
                : null;
    }

    /**
     * The replacement a CHANGE or an INSERT is about to write, looked up
     * first: a paren is evaluated, a lit-word drops its tick, a plain word
     * is fetched from its binding -- and an unset one raises no-value, as
     * {@code if (IS_UNSET(item)) Trap1(RE_NO_VALUE, rules-1)} does.
     */
    private String replacementFor(Value replacement) {
        if (replacement instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return textOf(evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context));
        }
        if (replacement instanceof WordValue word
                && word.datatype() == Datatype.LIT_WORD) {
            return textOf(word.as(Datatype.WORD));
        }
        if (replacement instanceof WordValue word
                && word.datatype() == Datatype.WORD) {
            Context holder = word.isBound() ? word.binding() : context;
            if (!holder.knows(word.canonical())
                    || holder.slotFor(word.canonical()).value() instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return textOf(holder.slotFor(word.canonical()).value());
        }
        return textOf(replacement);
    }

    /** REMOVE: match the rule after it and cut what matched out. */
    private int removeMatched(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            return NO_MATCH;
        }
        Integer markOffset = sameStorageOffset(rules.get(at + 1));
        if (markOffset != null) {
            int begin = Math.min(position, markOffset);
            int count = Math.abs(position - markOffset);
            for (int taken = begin + count; taken > begin; taken--) {
                removeFromSource(source.index() + taken - 1);
            }
            text = textOfSeries(source);
            position = begin;
            return 2;
        }
        int span = ruleSpan(rules, at + 1);
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        for (int taken = position; taken > before; taken--) {
            removeFromSource(source.index() + taken - 1);
        }
        text = textOfSeries(source);
        position = before;
        return 1 + span;
    }

    /** AHEAD: match the rule after it, then put the position back. */
    private int lookahead(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            return -1;
        }
        int before = position;
        boolean matched = matchOne(rules, at + 1) != NO_MATCH;
        position = before;
        return matched ? 1 + ruleSpan(rules, at + 1) : -1;
    }

    private int negate(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            return -1;
        }
        int before = position;
        boolean matched = matchOne(rules, at + 1) != NO_MATCH;
        position = before;
        return matched ? NO_MATCH : 1 + ruleSpan(rules, at + 1);
    }

    private boolean matchSequence(List<Value> rules) {
        int startedAt = position;
        for (List<Value> alternative : splitOnAlternatives(rules)) {
            position = startedAt;
            try {
                if (matchAllOf(alternative)) {
                    return true;
                }
            } catch (Rejected rejected) {
                position = startedAt;
                return false;
            }
        }
        position = startedAt;
        return false;
    }

    private boolean matchAllOf(List<Value> rules) {
        int at = 0;
        while (at < rules.size()) {
            int consumed = matchOne(rules, at);
            if (consumed < 0) {
                return false;
            }
            at += consumed;
        }
        return true;
    }

    private int matchOne(List<Value> rules, int at) {
        Value rule = rules.get(at);
        if (rule instanceof WordValue mark && mark.datatype() == Datatype.SET_WORD) {
            assign(mark, source.atIndex(source.index() + position));
            return 1;
        }
        if (rule instanceof WordValue back && back.datatype() == Datatype.GET_WORD) {
            Context holder = back.isBound() ? back.binding() : context;
            if (holder.knows(back.canonical())
                    && holder.slotFor(back.canonical()).value() instanceof StringValue marked) {
                if (!marked.sharesStorageWith(source)) {
                    adoptInput(marked);
                    return 1;
                }
                int sought = marked.index() - source.index();
                if (sought < 0 || sought > source.lengthFromHere()) {
                    throw Raised.of(EvaluationFailure.PARSE_RULE,
                            ":" + back.spelling() + " is not a position in what is "
                                    + "being parsed");
                }
                position = sought;
                return 1;
            }
            return NO_MATCH;
        }
        if (rule instanceof WordValue word && word.datatype() == Datatype.WORD) {
            return switch (word.canonical()) {
                case "end" -> endOfInput() ? 1 : -1;
                case "skip" -> advanceOne() ? 1 : -1;
                case "to" -> seek(rules, at, false);
                case "thru" -> seek(rules, at, true);
                case "any", "while" -> repeat(rules, at, 0);
                case "some" -> repeat(rules, at, 1);
                case "opt" -> optional(rules, at);
                case "and", "ahead" -> lookahead(rules, at);
                case "break" -> {
                    throw new RepeatEnded();
                }
                case "reject" -> {
                    throw new Rejected();
                }
                case "case" -> setCaseMode(true);
                case "no-case" -> setCaseMode(false);
                case "change" -> changeMatched(rules, at);
                case "remove" -> removeMatched(rules, at);
                case "insert" -> insertValue(rules, at);
                case "if" -> guard(rules, at);
                case "set" -> capture(rules, at, false);
                case "copy" -> capture(rules, at, true);
                case "collect" -> collect(rules, at);
                case "keep" -> keep(rules, at);
                case "not" -> negate(rules, at);
                case "then" -> 1;
                case "fail" -> NO_MATCH;
                case "limit" -> {
                    throw Raised.of(EvaluationFailure.NOT_DONE,
                            "limit is a parse command reserved for future use");
                }
                default -> matchNamedRule(word) ? 1 : -1;
            };
        }
        if (rule instanceof IntegerValue least) {
            int atMost = at + 1 < rules.size() && rules.get(at + 1) instanceof IntegerValue most
                    ? (int) most.magnitude()
                    : (int) least.magnitude();
            int ruleAt = at + (atMost == (int) least.magnitude()
                    && !(at + 1 < rules.size() && rules.get(at + 1) instanceof IntegerValue)
                    ? 1 : 2);
            return countedRepeat(rules, ruleAt, (int) least.magnitude(), atMost,
                    ruleAt - at);
        }
        if (rule instanceof UnsetValue || rule.datatype().isAnyFunction()) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, rule);
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            Value resolved = evaluator.evaluateOrRaise(
                    BlockValue.block(List.of(path)), context);
            return matchValue(resolved) ? 1 : -1;
        }
        return matchValue(rule) ? 1 : -1;
    }

    /** A rule applied between {@code least} and {@code most} times. */
    private int countedRepeat(
            List<Value> rules, int ruleAt, int least, int most, int countWidth) {

        if (ruleAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    rules.get(ruleAt - countWidth));
        }
        int startedAt = position;
        int matched = 0;
        while (matched < most) {
            int before = position;
            if (matchOne(rules, ruleAt) == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
        }
        if (matched < least) {
            position = startedAt;
            return NO_MATCH;
        }
        return countWidth + ruleSpan(rules, ruleAt);
    }

    /**
     * How many values of the rule block one rule occupies.
     *
     * <p>COLLECT and KEEP need this to know where the rule they apply to
     * ends, and a count needs it to know what it is counting.
     */
    private static int ruleSpan(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return 1;
        }
        if (rules.get(at) instanceof IntegerValue) {
            int counts = at + 1 < rules.size() && rules.get(at + 1) instanceof IntegerValue ? 2 : 1;
            return counts + ruleSpan(rules, at + counts);
        }
        if (rules.get(at) instanceof WordValue word && word.datatype() == Datatype.WORD) {
            if (word.canonical().equals("collect")
                    && at + 2 < rules.size()
                    && rules.get(at + 1) instanceof WordValue keyword
                    && keyword.datatype() == Datatype.WORD
                    && java.util.Set.of("set", "into", "after").contains(keyword.canonical())
                    && rules.get(at + 2) instanceof WordValue) {
                return 3 + ruleSpan(rules, at + 3);
            }
            return switch (word.canonical()) {
                case "any", "some", "opt", "to", "thru", "collect", "keep",
                     "and", "ahead", "not", "remove", "while", "insert", "if" ->
                        1 + ruleSpan(rules, at + 1);
                case "set", "copy" -> 2 + ruleSpan(rules, at + 2);
                default -> 1;
            };
        }
        return 1;
    }

    /** SET and COPY: put what the next rule matched into a word. */
    private int capture(List<Value> rules, int at, boolean wholeSlice) {
        if (at + 2 >= rules.size() || !(rules.get(at + 1) instanceof WordValue target)) {
            return NO_MATCH;
        }
        int ruleAt = at + 2;
        while (rules.get(ruleAt) instanceof WordValue mark
                && mark.datatype() == Datatype.SET_WORD) {
            assign(mark, source.atIndex(source.index() + position));
            ruleAt++;
            if (ruleAt >= rules.size()) {
                throw Raised.of(EvaluationFailure.PARSE_END,
                        "a capture has no rule after its marks to apply to");
            }
        }
        if (rules.get(ruleAt) instanceof WordValue asRule
                && asRule.datatype() == Datatype.GET_WORD) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, (Value) asRule);
        }
        int before = position;
        if (matchOne(rules, ruleAt) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        assign(target, wholeSlice ? sliceFrom(before) : oneCapturedFrom(before));
        return (ruleAt - at) + ruleSpan(rules, ruleAt);
    }

    /**
     * What SET without COPY assigns: the first matched character alone --
     * a byte's number for a binary -- and none for a match of nothing.
     * {@code GET_UTF8_CHAR(series, begin)} however long the span was.
     */
    private Value oneCapturedFrom(int before) {
        if (position == before) {
            return NoneValue.none();
        }
        return walkingBytes
                ? IntegerValue.of(text.charAt(before))
                : CharacterValue.of(text.charAt(before));
    }

    /**
     * COLLECT: gather what the KEEPs below it matched.
     *
     * <p>The block is handed to whatever encloses it whether or not the
     * rule matched, because matching backtracks and collecting does not.
     * The block walk works the same way and for the same reason.
     */
    private int collect(List<Value> rules, int at) {
        WordValue into = null;
        WordValue insertInto = null;
        WordValue appendTo = null;
        int ruleAt = at + 1;
        if (at + 2 < rules.size()
                && rules.get(at + 1) instanceof WordValue keyword
                && keyword.datatype() == Datatype.WORD
                && rules.get(at + 2) instanceof WordValue name) {
            switch (keyword.canonical()) {
                case "set" -> {
                    into = name;
                    ruleAt = at + 3;
                }
                case "into" -> {
                    insertInto = name;
                    ruleAt = at + 3;
                }
                case "after" -> {
                    appendTo = name;
                    ruleAt = at + 3;
                }
                default -> { }
            }
        }
        if (ruleAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "collect has no rule after it to apply to");
        }

        BlockValue destination = null;
        if (into != null) {
            destination = BlockValue.block(new ArrayList<>());
            assign(into, destination);
        }
        collecting.push(new ArrayList<>());
        int consumed = matchOne(rules, ruleAt);
        List<Value> mine = collecting.pop();

        if (insertInto != null) {
            deliver(insertInto, mine, false);
        } else if (appendTo != null) {
            deliver(appendTo, mine, true);
        } else if (into != null) {
            for (Value item : mine) {
                destination.storage().insertAt(destination.storageLength() + 1, item);
            }
        } else if (!collecting.isEmpty()) {
            collecting.peek().add(BlockValue.block(mine));
        } else if (gathered == null) {
            gathered = mine;
        } else {
            gathered.add(BlockValue.block(mine));
        }
        return consumed == NO_MATCH ? NO_MATCH : (ruleAt - at) + ruleSpan(rules, ruleAt);
    }

    /**
     * KEEP: add what the next rule matched to the collection.
     *
     * <p>One character is kept as a character and a run of them as a slice
     * of the input, so a file parse keeps file pieces rather than strings.
     * PICK keeps the characters one at a time instead.
     */
    private int keep(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "keep has no rule after it to apply to");
        }
        if (collecting.isEmpty()) {
            throw Raised.of(EvaluationFailure.PARSE_NO_COLLECT,
                    "keep has no collect around it");
        }

        Value kept = rules.get(at + 1);

        if (kept instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            Value produced = evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
            if (!collecting.isEmpty()) {
                collecting.peek().add(produced);
            }
            return 2;
        }
        if (kept instanceof WordValue modifier && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("pick")) {
            if (at + 2 < rules.size()
                    && rules.get(at + 2) instanceof BlockValue expression
                    && expression.datatype() == Datatype.PAREN) {
                collecting.peek().add(evaluator.evaluateOrRaise(
                        expression.as(Datatype.BLOCK), context));
                return 3;
            }
            return keepIndividually(rules, at + 2);
        }

        boolean keptViaCopy = kept instanceof WordValue copying
                && copying.datatype() == Datatype.WORD
                && copying.canonical().equals("copy");
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        if (!collecting.isEmpty() && position > before) {
            collecting.peek().add(keptViaCopy
                    ? sliceFrom(before)
                    : oneOrSliceFrom(before));
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    private int keepIndividually(List<Value> rules, int at) {
        int before = position;
        if (at >= rules.size() || matchOne(rules, at) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        if (!collecting.isEmpty()) {
            for (int character = before; character < position; character++) {
                collecting.peek().add(walkingBytes
                        ? IntegerValue.of(text.charAt(character))
                        : CharacterValue.of(text.charAt(character)));
            }
        }
        return (at - (at - 2)) + ruleSpan(rules, at);
    }

    /** One item as itself; several as a slice keeping the input's type. */
    private Value oneOrSliceFrom(int before) {
        if (position - before != 1) {
            return sliceFrom(before);
        }
        return walkingBytes
                ? IntegerValue.of(text.charAt(before))
                : CharacterValue.of(text.charAt(before));
    }

    private Value sliceFrom(int before) {
        String taken = text.substring(before, position);
        if (!walkingBytes) {
            return StringValue.of(taken, source.datatype());
        }
        int[] octets = new int[taken.length()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = taken.charAt(at);
        }
        return BinaryValue.of(octets);
    }

    /**
     * Refuses a COLLECT INTO target that cannot hold what this parse yields.
     *
     * <p>A block or a paren takes anything. A string target needs a string
     * being parsed and a binary target needs a binary, because what a
     * parse yields is pieces of its own input. Anything that is not a
     * series at all cannot be a target however the parse goes.
     *
     * <p>Left unchecked the delivery quietly did nothing, so a rule
     * collecting into the wrong kind of thing looked like it worked and
     * the target was simply never touched.
     */
    private void refuseWrongIntoTarget(Value target) {
        Datatype kind = target.datatype();
        Datatype parsing = source == null ? null : source.datatype();
        boolean suits = kind == Datatype.BLOCK || kind == Datatype.PAREN
                || kind == Datatype.HASH
                || (kind.isAnyString() && (parsing == null || parsing.isAnyString()))
                || (kind == Datatype.BINARY && (parsing == null
                        || parsing == Datatype.BINARY));
        if (!suits) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE,
                    "a " + kind.literalSpelling() + " cannot hold what this parse yields");
        }
    }

    /**
     * Puts a collection into the series a word already holds.
     *
     * <p>A block takes the values as they are; a string takes their text,
     * so collecting characters into a string gives a string rather than a
     * block of characters. INTO puts them at the series' position and
     * pushes what was there along; AFTER puts them past it.
     */
    private void deliver(WordValue word, List<Value> gathered, boolean past) {
        Context holder = word.isBound() ? word.binding() : context;
        if (!holder.knows(word.canonical())) {
            return;
        }
        Value target = holder.slotFor(word.canonical()).value();
        refuseWrongIntoTarget(target);
        switch (target) {
            case BlockValue existing -> {
                int where = past ? existing.storageLength() + 1 : existing.index();
                for (int added = gathered.size(); added > 0; added--) {
                    existing.storage().insertAt(where, gathered.get(added - 1));
                }
            }
            case StringValue existing -> {
                StringBuilder text = new StringBuilder();
                gathered.forEach(item -> text.append(Molder.form(item)));
                int where = past ? existing.storageLength() + 1 : existing.index();
                for (int at = text.length(); at > 0; at--) {
                    existing.storage().insertAt(where, text.codePointAt(at - 1));
                }
            }
            case BinaryValue existing -> {
                List<Integer> octets = new ArrayList<>();
                for (Value item : gathered) {
                    if (item instanceof IntegerValue octet) {
                        octets.add((int) octet.magnitude());
                    } else if (item instanceof BinaryValue slice) {
                        for (byte octet : slice.octetsFromHere()) {
                            octets.add(octet & 0xFF);
                        }
                    }
                }
                int where = past ? existing.storageLength() + 1 : existing.index();
                for (int at = octets.size(); at > 0; at--) {
                    existing.storage().insertAt(where, octets.get(at - 1));
                }
            }
            default -> { }
        }
    }

    private void assign(WordValue word, Value value) {
        Context target = word.isBound() ? word.binding() : context;
        target.set(word.canonical(), value);
    }

    private boolean matchNamedRule(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.knows(word.canonical())) {
            return matchValue(word);
        }
        Value named = target.slotFor(word.canonical()).value();
        if (named instanceof UnsetValue || named.datatype().isAnyFunction()) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, (Value) word);
        }
        return named instanceof BlockValue rule
                ? matchSequence(rule.remaining())
                : matchValue(named);
    }

    private boolean endOfInput() {
        return atEnd();
    }

    private boolean advanceOne() {
        if (atEnd()) {
            return false;
        }
        position++;
        return true;
    }

    /** Signals that BREAK ended the repeat around it. */
    private static final class RepeatEnded extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RepeatEnded() {
            super(null, null, false, false);
        }
    }

    /** Signals that REJECT failed the current block without trying its later alternatives. */
    private static final class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Rejected() {
            super(null, null, false, false);
        }
    }

    private int repeat(List<Value> rules, int at, int leastNeeded) {
        int matched = 0;
        while (true) {
            int before = position;
            int wasLong = text.length();
            int consumed;
            try {
                consumed = matchOne(rules, at + 1);
            } catch (RepeatEnded ended) {
                matched++;
                break;
            }
            if (consumed == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
            if (position == before && text.length() == wasLong) {
                break;
            }
        }
        return matched >= leastNeeded ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    private static final int NO_MATCH = -1;

    private int optional(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "opt has no rule after it to apply to");
        }
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    /** The parse command words TO and THRU cannot seek to. */
    private static final java.util.Set<String> PARSE_COMMANDS = java.util.Set.of(
            "skip", "to", "thru", "any", "some", "while", "opt", "and", "ahead",
            "not", "then", "break", "reject", "accept", "return", "limit",
            "case", "no-case", "change", "remove", "insert", "if", "set",
            "copy", "collect", "keep", "into");

    private int seek(List<Value> rules, int at, boolean past) {
        Value wanted = rules.get(at + 1);
        if (wanted instanceof WordValue word && word.canonical().equals("end")) {
            position = text.length();
            return 2;
        }
        if (wanted instanceof IntegerValue where) {
            long asked = where.magnitude() - (past ? 0 : 1);
            if (asked < 0 || asked > text.length()) {
                return -1;
            }
            position = (int) asked;
            return 2;
        }
        if (wanted instanceof DecimalValue
                || (wanted instanceof WordValue marker
                        && (marker.datatype() == Datatype.GET_WORD
                                || marker.datatype() == Datatype.SET_WORD))
                || (wanted instanceof WordValue keyword
                        && keyword.datatype() == Datatype.WORD
                        && PARSE_COMMANDS.contains(keyword.canonical()))) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "to and thru take a place or something to look for, not "
                            + Molder.mold(wanted));
        }
        if (wanted instanceof BlockValue || wanted instanceof BitsetValue) {
            for (int from = position; from <= text.length(); from++) {
                position = from;
                if (matchValue(wanted)) {
                    if (!past) {
                        position = from;
                    }
                    return 2;
                }
            }
            return -1;
        }
        String needle = textOf(wanted);
        int found = text.indexOf(needle, position);
        if (found < 0) {
            return -1;
        }
        position = past ? found + needle.length() : found;
        return 2;
    }

    /**
     * Whether a character is in a set, folding case unless /CASE was
     * asked for.
     *
     * <p>A parse folds case by default and a bitset is no exception, so
     * `parse "A" reduce [charset "a"]` matches. Only asking the set about
     * the character as written makes a charset the one rule in the
     * dialect that always minds case.
     */
    private boolean bitsetHolds(BitsetValue members, int character) {
        if (members.holds(character)) {
            return true;
        }
        if (mindingCase) {
            return false;
        }
        return members.holds(Character.toLowerCase(character))
                || members.holds(Character.toUpperCase(character));
    }

    /**
     * INSERT: put text in at the position, consuming nothing.
     *
     * <p>The position ends up after what was inserted, which is what
     * stops an INSERT inside a repeat from running for ever.
     */
    private int insertValue(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "insert needs a value to put in");
        }
        Value added = rules.get(at + 1);
        if (added instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            added = evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        int[] codepoints = Molder.form(added).codePoints().toArray();
        for (int step = 0; step < codepoints.length; step++) {
            insertIntoSource(source.index() + position + step, codepoints[step]);
        }
        this.text = textOfSeries(source);
        position += codepoints.length;
        return 2;
    }

    /**
     * IF: run the paren after it and carry on only if it answered true.
     *
     * <p>How a rule asks a question the input cannot answer. The block
     * parser had it and this one did not, so the same rule behaved
     * differently depending on what was being parsed.
     */
    private int guard(List<Value> rules, int at) {
        if (at + 1 >= rules.size()
                || !(rules.get(at + 1) instanceof BlockValue paren)
                || paren.datatype() != Datatype.PAREN) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "if needs a parenthesised condition after it");
        }
        return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context).isTruthy()
                ? 2
                : NO_MATCH;
    }

    private boolean matchValue(Value rule) {
        if (rule instanceof BlockValue nested && nested.datatype() == Datatype.PAREN) {
            evaluator.evaluateOrRaise(nested.as(Datatype.BLOCK), context);
            text = textOfSeries(source);
            position = Math.min(position, text.length());
            return true;
        }
        if (rule instanceof BlockValue nested) {
            return matchSequence(nested.remaining());
        }
        if (rule instanceof BitsetValue members) {
            if (position >= text.length() || !bitsetHolds(members, text.charAt(position))) {
                return false;
            }
            position++;
            return true;
        }
        String wanted = textOf(rule);
        if (wanted.isEmpty()
                || !text.regionMatches(!mindingCase, position, wanted, 0, wanted.length())) {
            return false;
        }
        position += wanted.length();
        return true;
    }

    private static String textOf(Value value) {
        if (value.datatype() == Datatype.TAG) {
            return Molder.mold(value);
        }
        if (value instanceof StringValue text) {
            return text.text();
        }
        if (value instanceof BinaryValue bytes) {
            return textOfSeries(bytes);
        }
        return Molder.form(value);
    }

    private static List<List<Value>> splitOnAlternatives(List<Value> rules) {
        List<List<Value>> alternatives = new ArrayList<>();
        List<Value> current = new ArrayList<>();
        for (Value rule : rules) {
            if (rule instanceof WordValue word && word.spelling().equals("|")) {
                alternatives.add(List.copyOf(current));
                current.clear();
                continue;
            }
            current.add(rule);
        }
        alternatives.add(List.copyOf(current));
        return alternatives;
    }
}
