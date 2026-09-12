package org.jebol.domain.parse;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.SeriesContents;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * PARSE over a string, which matches substrings rather than characters.
 *
 * <p>{@code parse "abc" ["a" "b" "c"]} matches, and it would not if a rule
 * matched one character at a time. Whitespace is never skipped: R3 reversed
 * REBOL 2's default and dropped the refinement, so {@code parse "a b c" ["a"
 * "b" "c"]} is false and the rule has to account for the spaces itself.
 */
public final class StringParser {

    private final Evaluator evaluator;
    private final Context context;

    private int[] codePoints;

    private SeriesValue source;

    private boolean walkingBytes;

    private boolean mindingCase;

    private int position;

    private final java.util.Deque<List<Value>> collectionsOpenInnermostLast =
            new java.util.ArrayDeque<>();

    private List<Value> gathered;

    private StringParser(Evaluator evaluator, Context context, SeriesValue source) {
        this.evaluator = evaluator;
        this.context = context;
        this.source = source;
        this.walkingBytes = source instanceof BinaryValue;
        this.codePoints = codePointsOfSeries(source);
    }

    private void adoptInput(SeriesValue newInput) {
        this.source = newInput;
        this.walkingBytes = newInput instanceof BinaryValue;
        this.codePoints = codePointsOfSeries(newInput);
        this.position = 0;
    }

    private void removeFromSource(int oneBasedIndex) {
        switch (source) {
            case StringValue text0 -> text0.storage().removeAt(oneBasedIndex);
            case BinaryValue bytes -> bytes.storage().removeAt(oneBasedIndex);
            default -> { }
        }
    }

    private void insertIntoSource(int oneBasedIndex, int item) {
        switch (source) {
            case StringValue text0 -> text0.storage().insertAt(oneBasedIndex, item);
            case BinaryValue bytes -> bytes.storage().insertAt(oneBasedIndex, item);
            default -> { }
        }
    }

    private static int[] codePointsOfSeries(SeriesValue series) {
        if (series instanceof StringValue text) {
            return text.text().codePoints().toArray();
        }
        BinaryValue bytes = (BinaryValue) series;
        int[] eachByteStandingInForACodePoint = new int[bytes.lengthFromHere()];
        for (int at = 0; at < eachByteStandingInForACodePoint.length; at++) {
            eachByteStandingInForACodePoint[at] = bytes.storage().at(bytes.index() + at);
        }
        return eachByteStandingInForACodePoint;
    }

    /** What a string parse answered: a logic, or the block COLLECT built. */
    public static Value answer(
            Evaluator evaluator, Context context, SeriesValue source, BlockValue rule,
            boolean mindingCase) {

        StringParser parser = new StringParser(evaluator, context, source);
        parser.mindingCase = mindingCase || parser.walkingBytes;
        boolean matched;
        try {
            matched = parser.matchSequence(rule.remaining());
        } catch (Returned decided) {
            return decided.answer;
        }
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

    /** The same, starting with case either minded or not. */
    public static boolean matches(
            Evaluator evaluator, Context context, SeriesValue source, BlockValue rule,
            boolean mindingCase) {

        StringParser parser = new StringParser(evaluator, context, source);
        parser.mindingCase = mindingCase || parser.walkingBytes;
        return parser.matchSequence(rule.remaining()) && parser.atEnd();
    }

    private boolean atEnd() {
        return position >= codePoints.length;
    }

    private int setCaseModeWhichLastsUntilTheOtherWordAppears(boolean minding) {
        mindingCase = minding;
        return 1;
    }

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
            int[] written = unitsToLayInWhichTheSeriesRatherThanTheValueDecides(replacementFor(rules.get(at + 2)));
            for (int added = 0; added < written.length; added++) {
                insertIntoSource(source.index() + begin + added, written[added]);
            }
            codePoints = codePointsOfSeries(source);
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
        int[] units = unitsToLayInWhichTheSeriesRatherThanTheValueDecides(replacementFor(rules.get(replacementAt)));
        for (int added = 0; added < units.length; added++) {
            insertIntoSource(source.index() + before + added, units[added]);
        }
        codePoints = codePointsOfSeries(source);
        position = before + units.length;
        return 1 + span + 1;
    }

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

    private Value replacementFor(Value replacement) {
        if (replacement instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        if (replacement instanceof WordValue word
                && word.datatype() == Datatype.LIT_WORD) {
            return word.as(Datatype.WORD);
        }
        if (replacement instanceof WordValue word
                && word.datatype() == Datatype.WORD) {
            Context holder = word.isBound() ? word.binding() : context;
            if (!holder.knows(word.canonical())
                    || holder.slotFor(word.canonical()).value() instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return holder.slotFor(word.canonical()).value();
        }
        return replacement;
    }

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
            codePoints = codePointsOfSeries(source);
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
        codePoints = codePointsOfSeries(source);
        position = before;
        return 1 + span;
    }

    private int matchTheNextRuleThenPutThePositionBack(List<Value> rules, int at) {
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
                case "and", "ahead" -> matchTheNextRuleThenPutThePositionBack(rules, at);
                case "break" -> {
                    throw new RepeatEnded();
                }
                case "reject" -> {
                    throw new Rejected();
                }
                case "case" -> setCaseModeWhichLastsUntilTheOtherWordAppears(true);
                case "no-case" -> setCaseModeWhichLastsUntilTheOtherWordAppears(false);
                case "change" -> changeMatched(rules, at);
                case "remove" -> removeMatched(rules, at);
                case "insert" -> insertValue(rules, at);
                case "if" -> guard(rules, at);
                case "set" -> capture(rules, at, false);
                case "copy" -> capture(rules, at, true);
                case "return" -> answerWith(rules, at);
                case "collect" -> collect(rules, at);
                case "keep" -> keep(rules, at);
                case "not" -> negate(rules, at);
                case "then" -> 1;
                case "fail" -> NO_MATCH;
                case "limit" -> {
                    throw Raised.of(EvaluationFailure.NOT_DONE,
                            "limit is a parse command reserved for future use");
                }
                default -> countBehind(word) != null
                        ? matchRepeat(rules, at)
                        : (matchNamedRule(word) ? 1 : -1);
            };
        }
        if (rule instanceof IntegerValue) {
            return matchRepeat(rules, at);
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

    private int ruleSpan(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return 1;
        }
        if (countInWhetherWrittenAsANumberOrHeldInAWord(rules,at) != null) {
            int counts = countInWhetherWrittenAsANumberOrHeldInAWord(rules,at + 1) != null ? 2 : 1;
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
                     "and", "ahead", "not", "remove", "while", "insert", "if",
                     "return" ->
                        1 + ruleSpan(rules, at + 1);
                case "set", "copy" -> 2 + ruleSpan(rules, at + 2);
                default -> 1;
            };
        }
        return 1;
    }

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
        assign(target, wholeSlice ? sliceFrom(before) : theFirstMatchedCharacterAloneHoweverLongTheSpanWas(before));
        return (ruleAt - at) + ruleSpan(rules, ruleAt);
    }

    private Value theFirstMatchedCharacterAloneHoweverLongTheSpanWas(int before) {
        if (position == before) {
            return NoneValue.none();
        }
        return walkingBytes
                ? IntegerValue.of(codePoints[before])
                : CharacterValue.of(codePoints[before]);
    }

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
        collectionsOpenInnermostLast.push(new ArrayList<>());
        int consumed = matchOne(rules, ruleAt);
        List<Value> mine = collectionsOpenInnermostLast.pop();

        if (insertInto != null) {
            deliver(insertInto, mine, false);
        } else if (appendTo != null) {
            deliver(appendTo, mine, true);
        } else if (into != null) {
            for (Value item : mine) {
                destination.storage().insertAt(destination.storageLength() + 1, item);
            }
        } else if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().add(BlockValue.block(mine));
        } else if (gathered == null) {
            gathered = mine;
        } else {
            gathered.add(BlockValue.block(mine));
        }
        return consumed == NO_MATCH ? NO_MATCH : (ruleAt - at) + ruleSpan(rules, ruleAt);
    }

    private int keep(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "keep has no rule after it to apply to");
        }
        if (collectionsOpenInnermostLast.isEmpty()) {
            throw Raised.of(EvaluationFailure.PARSE_NO_COLLECT,
                    "keep has no collect around it");
        }

        Value kept = rules.get(at + 1);

        if (kept instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            Value produced = evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
            if (!collectionsOpenInnermostLast.isEmpty()) {
                collectionsOpenInnermostLast.peek().add(produced);
            }
            return 2;
        }
        if (kept instanceof WordValue modifier && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("pick")) {
            if (at + 2 < rules.size()
                    && rules.get(at + 2) instanceof BlockValue expression
                    && expression.datatype() == Datatype.PAREN) {
                collectionsOpenInnermostLast.peek().add(evaluator.evaluateOrRaise(
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
        if (!collectionsOpenInnermostLast.isEmpty() && position > before) {
            collectionsOpenInnermostLast.peek().add(keptViaCopy
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
        if (!collectionsOpenInnermostLast.isEmpty()) {
            for (int character = before; character < position; character++) {
                collectionsOpenInnermostLast.peek().add(walkingBytes
                        ? IntegerValue.of(codePoints[character])
                        : CharacterValue.of(codePoints[character]));
            }
        }
        return (at - (at - 2)) + ruleSpan(rules, at);
    }

    private Value oneOrSliceFrom(int before) {
        if (position - before != 1) {
            return sliceFrom(before);
        }
        return walkingBytes
                ? IntegerValue.of(codePoints[before])
                : CharacterValue.of(codePoints[before]);
    }

    private Value sliceFrom(int before) {
        String taken = textBetween(before, position);
        if (!walkingBytes) {
            return StringValue.of(taken, source.datatype());
        }
        int[] octets = new int[taken.length()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = taken.charAt(at);
        }
        return BinaryValue.of(octets);
    }

    private void refuseATargetThatCannotHoldWhatThisParseYields(Value target) {
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

    private void deliver(WordValue word, List<Value> gathered, boolean past) {
        Context holder = word.isBound() ? word.binding() : context;
        if (!holder.knows(word.canonical())) {
            return;
        }
        Value target = holder.slotFor(word.canonical()).value();
        refuseATargetThatCannotHoldWhatThisParseYields(target);
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
                int[] codePoints = text.toString().codePoints().toArray();
                int where = past ? existing.storageLength() + 1 : existing.index();
                for (int at = codePoints.length; at > 0; at--) {
                    existing.storage().insertAt(where, codePoints[at - 1]);
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

    private Integer countInWhetherWrittenAsANumberOrHeldInAWord(
            List<Value> rules, int at) {
        if (at >= rules.size()) {
            return null;
        }
        Value rule = rules.get(at);
        if (rule instanceof IntegerValue count) {
            return (int) count.magnitude();
        }
        return rule instanceof WordValue word && word.datatype() == Datatype.WORD
                ? countBehind(word)
                : null;
    }

    private Integer countBehind(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical())
                && target.slotFor(word.canonical()).value() instanceof IntegerValue count
                ? (int) count.magnitude()
                : null;
    }

    private int matchRepeat(List<Value> rules, int at) {
        int least = countInWhetherWrittenAsANumberOrHeldInAWord(rules,at);
        Integer second = countInWhetherWrittenAsANumberOrHeldInAWord(rules,at + 1);
        int ruleAt = second == null ? at + 1 : at + 2;
        return countedRepeat(rules, ruleAt, least,
                second == null ? least : second, ruleAt - at);
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
        return named instanceof BlockValue rule && rule.datatype() == Datatype.BLOCK
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

    private static final class RepeatEnded extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RepeatEnded() {
            super(null, null, false, false);
        }
    }

    private static final class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Rejected() {
            super(null, null, false, false);
        }
    }

    private static final class Returned extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient Value answer;

        Returned(Value answer) {
            super(null, null, false, false);
            this.answer = answer;
        }
    }

    private int answerWith(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "return has no rule after it to answer with");
        }
        if (rules.get(at + 1) instanceof BlockValue paren
                && paren.datatype() == Datatype.PAREN) {
            throw new Returned(evaluator.evaluateOrRaise(paren, context));
        }
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        throw new Returned(sliceFrom(before));
    }

    private int repeat(List<Value> rules, int at, int leastNeeded) {
        int matched = 0;
        while (true) {
            int before = position;
            int wasLong = codePoints.length;
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
            if (position == before && codePoints.length == wasLong) {
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

    private static final java.util.Set<String> PARSE_COMMANDS = java.util.Set.of(
            "skip", "to", "thru", "any", "some", "while", "opt", "and", "ahead",
            "not", "then", "break", "reject", "accept", "return", "limit",
            "case", "no-case", "change", "remove", "insert", "if", "set",
            "copy", "collect", "keep", "into");

    private Value whatTheWordHolds(Value wanted) {
        if (!(wanted instanceof WordValue named) || named.datatype() != Datatype.WORD) {
            return wanted;
        }
        Context target = named.isBound() ? named.binding() : context;
        return target.knows(named.canonical())
                ? target.slotFor(named.canonical()).value()
                : wanted;
    }

    private int seek(List<Value> rules, int at, boolean past) {
        Value wanted = rules.get(at + 1);
        if (wanted instanceof WordValue word && word.canonical().equals("end")) {
            position = codePoints.length;
            return 2;
        }
        if (wanted instanceof IntegerValue where) {
            long asked = where.magnitude() - (past ? 0 : 1);
            if (asked < 0 || asked > codePoints.length) {
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
        wanted = whatTheWordHolds(wanted);
        if (wanted instanceof BlockValue || wanted instanceof BitsetValue) {
            for (int from = position; from <= codePoints.length; from++) {
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
        int[] needle = textOf(wanted).codePoints().toArray();
        int found = firstMatchFrom(needle, position);
        if (found < 0) {
            return -1;
        }
        position = past ? found + needle.length : found;
        return 2;
    }

    private boolean bitsetHoldsFoldingCaseUnlessAskedNotTo(
            BitsetValue members, int character) {
        if (members.holds(character)) {
            return true;
        }
        if (mindingCase) {
            return false;
        }
        return members.holds(Character.toLowerCase(character))
                || members.holds(Character.toUpperCase(character));
    }

    private int insertValue(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "insert needs a value to put in");
        }
        Value added = rules.get(at + 1);
        if (added instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            added = evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        int[] units = unitsToLayInWhichTheSeriesRatherThanTheValueDecides(added);
        for (int step = 0; step < units.length; step++) {
            insertIntoSource(source.index() + position + step, units[step]);
        }
        this.codePoints = codePointsOfSeries(source);
        position += units.length;
        return 2;
    }

    private int[] unitsToLayInWhichTheSeriesRatherThanTheValueDecides(Value value) {
        return source instanceof BinaryValue
                ? SeriesContents.octetsContributedBy(value)
                : SeriesContents.charactersContributedBy(value);
    }

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
        if (rule instanceof NoneValue) {
            return true;
        }
        if (rule instanceof BlockValue nested && nested.datatype() == Datatype.PAREN) {
            evaluator.evaluateOrRaise(nested.as(Datatype.BLOCK), context);
            codePoints = codePointsOfSeries(source);
            position = Math.min(position, codePoints.length);
            return true;
        }
        if (rule instanceof BlockValue nested) {
            return matchSequence(nested.remaining());
        }
        if (rule instanceof BitsetValue members) {
            if (position >= codePoints.length || !bitsetHoldsFoldingCaseUnlessAskedNotTo(members,codePoints[position])) {
                return false;
            }
            position++;
            return true;
        }
        int[] wanted = textOf(rule).codePoints().toArray();
        if (wanted.length == 0 || !matchesAt(wanted, position)) {
            return false;
        }
        position += wanted.length;
        return true;
    }

    private String textBetween(int from, int to) {
        return new String(codePoints, from, to - from);
    }

    private boolean matchesAt(int[] wanted, int from) {
        if (from + wanted.length > codePoints.length) {
            return false;
        }
        for (int at = 0; at < wanted.length; at++) {
            if (!theSameLetter(codePoints[from + at], wanted[at])) {
                return false;
            }
        }
        return true;
    }

    private boolean theSameLetter(int one, int other) {
        return one == other || !mindingCase
                && Character.toLowerCase(one) == Character.toLowerCase(other);
    }

    private int firstMatchFrom(int[] needle, int from) {
        for (int at = from; at + needle.length <= codePoints.length; at++) {
            if (matchesAt(needle, at)) {
                return at;
            }
        }
        return -1;
    }

    private static String textOf(Value value) {
        if (value.datatype() == Datatype.TAG) {
            return Molder.mold(value);
        }
        if (value instanceof StringValue text) {
            return text.text();
        }
        if (value instanceof BinaryValue bytes) {
            return new String(codePointsOfSeries(bytes), 0, bytes.lengthFromHere());
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
