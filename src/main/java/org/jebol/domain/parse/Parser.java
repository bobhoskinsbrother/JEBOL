package org.jebol.domain.parse;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.SeriesContents;
import org.jebol.domain.value.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class Parser {


    static final int NO_MATCH = -1;

    Evaluator evaluator;
    Context context;

    SeriesValue source;
    int position;
    boolean mindingCase;
    boolean blockEndedInAMatch;

    Deque<List<Value>> collectionsOpenInnermostLast = new ArrayDeque<>();
    List<Value> gathered;

    public Value answerFor(BlockValue rule) {
        boolean matched;
        try {
            matched = matchSequence(rule.remaining());
        } catch (Returned decided) {
            return decided.answer();
        }
        if (gathered != null) {
            return BlockValue.block(gathered);
        }
        return LogicValue.of(matched && atEnd());
    }

    public boolean matchesTheWholeOf(BlockValue rule) {
        return matchSequence(rule.remaining()) && atEnd();
    }

    boolean atEnd() {
        return position >= inputLength();
    }

    boolean advanceOne() {
        if (atEnd()) {
            return false;
        }
        position++;
        return true;
    }

    boolean matchSequence(List<Value> rules) {
        List<List<Value>> alternatives = splitOnAlternatives(rules);
        int startedAt = position;

        int at = 0;
        while (at < alternatives.size()) {
            position = startedAt;
            try {
                if (matchAllOf(alternatives.get(at))) {
                    return true;
                }
                at++;
            } catch (AlternativeSkipped committed) {
                at += 2;
            } catch (BlockEnded ended) {
                if (ended.endedAsAMatch()) {
                    blockEndedInAMatch = true;
                    return true;
                }
                position = startedAt;
                return false;
            }
        }
        position = startedAt;
        return false;
    }

    boolean matchAllOf(List<Value> rules) {
        int at = 0;
        while (at < rules.size()) {
            int consumed = matchOne(rules, at);
            if (consumed == NO_MATCH) {
                return false;
            }
            at += consumed;
        }
        return true;
    }

    static List<List<Value>> splitOnAlternatives(List<Value> rules) {
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

    int applyKeyword(ParseKeyword keyword, List<Value> rules, int at) {
        Optional<Integer> refused = refusalForAMissingArgument(keyword, rules, at);
        return refused.isPresent()
                ? refused.orElseThrow()
                : applyKeywordThatHasWhatItNeeds(keyword, rules, at);
    }

    private Optional<Integer> refusalForAMissingArgument(
            ParseKeyword keyword, List<Value> rules, int at) {

        if (!keyword.needsSomethingAfterIt()) {
            return Optional.empty();
        }
        if (at + 1 >= rules.size()) {
            return Optional.of(whatAKeywordWithNothingAfterItAnswers(keyword));
        }
        return keyword.ownsTheRuleAfterIt()
                && at + keyword.slotsBeforeTheRuleSpan() >= rules.size()
                ? Optional.of(NO_MATCH)
                : Optional.empty();
    }

    private int whatAKeywordWithNothingAfterItAnswers(ParseKeyword keyword) {
        return switch (keyword.whenNothingFollowsIt()) {
            case DOES_NOT_MATCH -> NO_MATCH;
            case RAISES_PARSE_END ->
                    throw Raised.of(EvaluationFailure.PARSE_END, keyword.spelling());
            case RAISES_PARSE_VARIABLE ->
                    throw Raised.of(EvaluationFailure.PARSE_VARIABLE, keyword.spelling());
        };
    }

    int ruleSpan(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return 1;
        }
        if (countIn(rules, at) != null) {
            int counts = countIn(rules, at + 1) != null ? 2 : 1;
            return counts + ruleSpan(rules, at + counts);
        }
        if (rules.get(at) instanceof WordValue word && word.datatype() == Datatype.WORD) {
            if (word.canonical().equals("collect")
                    && at + 2 < rules.size()
                    && rules.get(at + 1) instanceof WordValue keyword
                    && keyword.datatype() == Datatype.WORD
                    && ParseTargets.THE_WORDS_THAT_NAME_WHERE_COLLECT_PUTS_IT
                            .contains(keyword.canonical())
                    && rules.get(at + 2) instanceof WordValue) {
                return 3 + ruleSpan(rules, at + 3);
            }
            return ParseKeyword.named(word.canonical())
                    .map(keyword -> slotsTakenBy(keyword, rules, at))
                    .orElse(1);
        }
        return 1;
    }

    private int slotsTakenBy(ParseKeyword keyword, List<Value> rules, int at) {
        int fixed = keyword.slotsBeforeTheRuleSpan();
        return keyword.ownsTheRuleAfterIt()
                ? fixed + ruleSpan(rules, at + fixed)
                : fixed;
    }

    Integer countIn(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return null;
        }
        if (rules.get(at) instanceof IntegerValue count) {
            return (int) count.magnitude();
        }
        return rules.get(at) instanceof WordValue word
                && word.datatype() == Datatype.WORD
                ? countBehind(word)
                : null;
    }

    Integer countBehind(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical())
                && target.slotFor(word.canonical()).value() instanceof IntegerValue count
                ? (int) count.magnitude()
                : null;
    }

    Value whatTheWordHolds(Value wanted) {
        if (!(wanted instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return wanted;
        }
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical())
                ? target.slotFor(word.canonical()).value()
                : wanted;
    }

    boolean matchNamedRule(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.knows(word.canonical())) {
            return false;
        }
        Value held = target.slotFor(word.canonical()).value();
        if (held instanceof UnsetValue || held.datatype().isAnyFunction()) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, word);
        }
        return held instanceof BlockValue rule && rule.datatype() == Datatype.BLOCK
                ? matchSequence(rule.remaining())
                : matchValue(held);
    }

    int setCaseModeWhichLastsUntilTheOtherWordAppears(boolean minding) {
        mindingCase = minding;
        return 1;
    }

    int negate(List<Value> rules, int at) {
        int negations = 1;
        while (isTheWordNot(rules, at + negations)) {
            negations++;
        }
        int ruleAt = at + negations;
        if (ruleAt >= rules.size()) {
            return NO_MATCH;
        }
        int before = position;
        boolean matched = matchOne(rules, ruleAt) != NO_MATCH;
        position = before;
        boolean negating = negations % 2 == 1;
        return negating && matched
                ? NO_MATCH
                : negations + ruleSpan(rules, ruleAt);
    }

    private static boolean isTheWordNot(List<Value> rules, int at) {
        return at < rules.size()
                && rules.get(at) instanceof WordValue word
                && word.datatype() == Datatype.WORD
                && ParseKeyword.named(word.canonical())
                        .filter(ParseKeyword.NOT::equals)
                        .isPresent();
    }

    int commitPastTheNextAlternative(List<Value> rules, int at) {
        if (matchOne(rules, at + 1) == NO_MATCH) {
            throw new AlternativeSkipped();
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    int matchTheNextRuleThenPutThePositionBack(
            List<Value> rules, int at) {

        int before = position;
        boolean matched = matchOne(rules, at + 1) != NO_MATCH;
        position = before;
        return matched ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    int optional(List<Value> rules, int at) {
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    int repeat(List<Value> rules, int at, int leastNeeded) {
        int matched = 0;
        while (true) {
            int before = position;
            int wasLong = inputLength();
            blockEndedInAMatch = false;
            if (matchOne(rules, at + 1) == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
            if (blockEndedInAMatch) {
                blockEndedInAMatch = false;
                return 1 + ruleSpan(rules, at + 1);
            }
            if (position == before && inputLength() == wasLong) {
                break;
            }
        }
        return matched >= leastNeeded ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    static final class Returned extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient Value answer;

        Returned(Value answer) {
            super(null, null, false, false);
            this.answer = answer;
        }

        Value answer() {
            return answer;
        }
    }

    private final boolean walkingABlock;

    private List<Value> input;

    private Parser(Evaluator evaluator, Context context, Value given) {
        this.evaluator = evaluator;
        this.context = context;
        this.walkingABlock = !(given instanceof StringValue || given instanceof BinaryValue);
        this.parsing = given.datatype();
        if (walkingABlock) {
            BlockValue block = given instanceof BlockValue whole ? whole : null;
            this.source = block;
            this.input = new ArrayList<>(
                    block != null ? block.remaining() : List.of(given));
        } else {
            this.source = (SeriesValue) given;
            this.walkingBytes = given instanceof BinaryValue;
            this.codePoints = codePointsOfSeries((SeriesValue) given);
        }
    }

    public static Parser over(Evaluator evaluator, Context context, Value given,
            boolean mindingCase) {

        Parser parser = new Parser(evaluator, context, given);
        parser.mindingCase = mindingCase || parser.walkingBytes;
        return parser;
    }

    private BlockValue theBlockBeingWalked() {
        return (BlockValue) source;
    }

    int inputLength() {
        return walkingABlock ? inputLengthOverABlock() : inputLengthOverAString();
    }

    int matchOne(List<Value> rules, int at) {
        return walkingABlock
                ? matchOneOverABlock(rules, at)
                : matchOneOverAString(rules, at);
    }

    boolean matchValue(Value rule) {
        return walkingABlock ? matchValueOverABlock(rule) : matchValueOverAString(rule);
    }

    int applyKeywordThatHasWhatItNeeds(
            ParseKeyword keyword, List<Value> rules, int at) {
        return walkingABlock
                ? applyKeywordThatHasWhatItNeedsOverABlock(keyword, rules, at)
                : applyKeywordThatHasWhatItNeedsOverAString(keyword, rules, at);
    }



    private Datatype parsing = Datatype.BLOCK;



    int inputLengthOverABlock() {
        return input.size();
    }

    private void refreshInputFromSource() {
        if (source == null) {
            return;
        }
        input.clear();
        input.addAll(theBlockBeingWalked().remaining());
        position = Math.min(position, input.size());
    }



    private Value current() {
        return input.get(position);
    }

    private int seekToMark(WordValue back) {
        Context holder = back.isBound() ? back.binding() : context;
        Value held = holder.knows(back.canonical())
                ? holder.slotFor(back.canonical()).value()
                : NoneValue.none();
        ParseTargets.refuseAnInputThatIsNotASeries(back, held);
        if (!(held instanceof BlockValue marked)) {
            return NO_MATCH;
        }
        if (source == null || !marked.sharesStorageWith(source)) {
            return NO_MATCH;
        }
        int sought = marked.index() - source.index();
        if (sought < 0 || sought > input.size()) {
            return NO_MATCH;
        }
        position = sought;
        return 1;
    }

    int matchOneOverABlock(List<Value> rules, int at) {
        Value rule = rules.get(at);

        if (rule instanceof IntegerValue) {
            return matchCountedRule(rules, at);
        }
        if (rule instanceof WordValue mark && mark.datatype() == Datatype.SET_WORD) {
            assignOverABlock(mark, source == null
                    ? BlockValue.block(input.subList(position, input.size()))
                    : source.atIndex(source.index() + position));
            return 1;
        }
        if (rule instanceof WordValue back && back.datatype() == Datatype.GET_WORD) {
            return seekToMark(back);
        }
        if (rule instanceof WordValue word && word.datatype() == Datatype.WORD) {
            OptionalInt consumed = matchKeyword(word.canonical(), rules, at);
            if (consumed.isPresent()) {
                return consumed.getAsInt();
            }
            if (countBehind(word) != null) {
                return matchCountedRule(rules, at);
            }
            return matchNamedRule(word) ? 1 : NO_MATCH;
        }
        if (rule instanceof UnsetValue || rule.datatype().isAnyFunction()) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, rule);
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            Value resolved = evaluator.evaluateOrRaise(
                    BlockValue.block(List.of(path)), context);
            return matchValueOverABlock(resolved) ? 1 : NO_MATCH;
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.GET_PATH) {
            ParseTargets.refuseAnInputThatIsNotASeries(path, evaluator.evaluateOrRaise(
                    BlockValue.block(List.of(path)), context));
            return NO_MATCH;
        }
        return matchValueOverABlock(rule) ? 1 : NO_MATCH;
    }

    private int matchCountedRule(List<Value> rules, int at) {
        long least = countIn(rules, at);
        int countItems = 1;
        long most = least;

        Integer upper = countIn(rules, at + 1);
        if (upper != null) {
            most = upper;
            countItems = 2;
        }
        if (at + countItems >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END, rules.get(at));
        }

        int matched = 0;
        while (matched < most) {
            int before = position;
            if (matchOneOverABlock(rules, at + countItems) == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
        }
        return matched >= least ? countItems + ruleSpan(rules, at + countItems) : NO_MATCH;
    }

    private OptionalInt matchKeyword(String spelling, List<Value> rules, int at) {
        Optional<ParseKeyword> keyword = ParseKeyword.named(spelling);
        return keyword.isPresent()
                ? OptionalInt.of(applyKeyword(keyword.get(), rules, at))
                : OptionalInt.empty();
    }

    int applyKeywordThatHasWhatItNeedsOverABlock(
            ParseKeyword keyword, List<Value> rules, int at) {
        return switch (keyword) {
            case END -> atEnd() ? 1 : NO_MATCH;
            case SKIP -> advanceOne() ? 1 : NO_MATCH;
            case ANY, WHILE -> repeat(rules, at, 0);
            case CASE -> setCaseModeWhichLastsUntilTheOtherWordAppears(true);
            case NO_CASE -> setCaseModeWhichLastsUntilTheOtherWordAppears(false);
            case SOME -> repeat(rules, at, 1);
            case OPT -> optional(rules, at);
            case TO -> seekOverABlock(rules, at, false);
            case THRU -> seekOverABlock(rules, at, true);
            case INTO -> into(rules, at);
            case SET -> captureOverABlock(rules, at, false);
            case COPY -> captureOverABlock(rules, at, true);
            case COLLECT -> collectOverABlock(rules, at);
            case KEEP -> keepOverABlock(rules, at);
            case QUOTE -> quoted(rules, at);
            case AND, AHEAD -> matchTheNextRuleThenPutThePositionBack(rules, at);
            case NOT -> negate(rules, at);
            case IF -> guardOverABlock(rules, at);
            case REMOVE -> removeMatchedOverABlock(rules, at);
            case CHANGE -> changeMatchedOverABlock(rules, at);
            case INSERT -> insertValueOverABlock(rules, at);
            case RETURN -> returnFrom(rules, at);
            case THEN -> commitPastTheNextAlternative(rules, at);
            case BREAK, ACCEPT -> throw BlockEnded.asAMatch();
            case REJECT -> throw BlockEnded.asAFailure();
            case FAIL -> NO_MATCH;
            case LIMIT -> throw Raised.of(EvaluationFailure.NOT_DONE,
                    "limit is a parse command reserved for future use");
        };
    }

    private int returnFrom(List<Value> rules, int at) {
        Value following = following(rules, at, "return");
        if (following instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            throw new Returned(evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context));
        }
        int begin = position;
        if (matchOneOverABlock(rules, at + 1) == NO_MATCH) {
            return NO_MATCH;
        }
        throw new Returned(BlockValue.block(List.copyOf(input.subList(begin, position))));
    }

    private int changeMatchedOverABlock(List<Value> rules, int at) {
        Value rule = following(rules, at, "change");
        if (rule instanceof WordValue misplaced && misplaced.datatype() == Datatype.WORD
                && misplaced.canonical().equals("only")) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "only says how to put the replacement in, so it goes "
                            + "before the replacement and not before the rule");
        }
        Integer markOffset = sameStorageOffsetOverABlock(rule);
        if (markOffset != null) {
            return changedSpan(rules, at, markOffset);
        }
        int replacementAt = at + 1 + ruleSpan(rules, at + 1);
        if (replacementAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "change needs a value to put where the match was");
        }
        int before = position;
        if (matchOneOverABlock(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        int lastRuleAt = replacementAt;
        boolean wholeBlock = false;
        if (rules.get(replacementAt) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("only")
                && replacementAt + 1 < rules.size()) {
            wholeBlock = true;
            lastRuleAt = replacementAt + 1;
        }
        Value replacement = valueToPutInLookedUpFirst(rules.get(lastRuleAt));
        List<Value> putting = !wholeBlock && replacement instanceof BlockValue spread
                && spread.datatype() == Datatype.BLOCK
                ? spread.remaining()
                : List.of(replacement);
        for (int taken = position; taken > before; taken--) {
            input.remove(taken - 1);
            if (source != null) {
                theBlockBeingWalked().storage().removeAt(source.index() + taken - 1);
            }
        }
        for (int added = putting.size(); added > 0; added--) {
            input.add(before, putting.get(added - 1));
            if (source != null) {
                theBlockBeingWalked().storage().insertAt(source.index() + before, putting.get(added - 1));
            }
        }
        position = before + putting.size();
        return lastRuleAt + 1 - at;
    }

    private Value valueToPutInLookedUpFirst(Value written) {
        if (written instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        if (written instanceof WordValue word) {
            return switch (word.datatype()) {
                case LIT_WORD -> word.as(Datatype.WORD);
                case WORD -> evaluator.evaluateOrRaise(
                        BlockValue.block(List.of(word)), context);
                default -> word;
            };
        }
        if (written instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            return evaluator.evaluateOrRaise(
                    BlockValue.block(List.of(path)), context);
        }
        return written;
    }

    private int insertValueOverABlock(List<Value> rules, int at) {
        following(rules, at, "insert");
        int valueAt = at + 1;
        boolean wholeBlock = false;
        if (rules.get(valueAt) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("only")
                && valueAt + 1 < rules.size()) {
            wholeBlock = true;
            valueAt++;
        }
        Value added = valueToPutInLookedUpFirst(rules.get(valueAt));
        List<Value> putting = !wholeBlock && added instanceof BlockValue spread
                && spread.datatype() == Datatype.BLOCK
                ? spread.remaining()
                : List.of(added);
        for (int added0 = putting.size(); added0 > 0; added0--) {
            input.add(position, putting.get(added0 - 1));
            if (source != null) {
                theBlockBeingWalked().storage().insertAt(source.index() + position, putting.get(added0 - 1));
            }
        }
        position += putting.size();
        return valueAt + 1 - at;
    }

    private int removeMatchedOverABlock(List<Value> rules, int at) {
        Integer markOffset = sameStorageOffsetOverABlock(following(rules, at, "remove"));
        if (markOffset != null) {
            int begin = Math.min(position, markOffset);
            removeSpan(begin, Math.abs(position - markOffset));
            position = begin;
            return 2;
        }
        int before = position;
        if (matchOneOverABlock(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        removeSpan(before, position - before);
        position = before;
        return 1 + ruleSpan(rules, at + 1);
    }

    private void removeSpan(int begin, int count) {
        for (int taken = begin + count; taken > begin; taken--) {
            input.remove(taken - 1);
            if (source != null) {
                theBlockBeingWalked().storage().removeAt(source.index() + taken - 1);
            }
        }
    }

    private Integer sameStorageOffsetOverABlock(Value item) {
        if (source == null
                || !(item instanceof WordValue word)
                || (word.datatype() != Datatype.WORD
                        && word.datatype() != Datatype.GET_WORD)
                || (word.datatype() == Datatype.WORD
                        && ParseTargets.THE_WORDS_THE_DIALECT_RESERVES
                                .contains(word.canonical()))) {
            return null;
        }
        Context holder = word.isBound() ? word.binding() : context;
        if (!holder.knows(word.canonical())) {
            return null;
        }
        return holder.slotFor(word.canonical()).value() instanceof BlockValue marked
                && marked.sharesStorageWith(source)
                ? marked.index() - source.index()
                : null;
    }

    private int changedSpan(List<Value> rules, int at, int markOffset) {
        if (at + 2 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "change needs a value to put where the match was");
        }
        int begin = Math.min(position, markOffset);
        int count = Math.abs(position - markOffset);
        int replacementSlot = at + 2;
        boolean wholeBlock = false;
        int lastRuleAt = replacementSlot;
        if (rules.get(replacementSlot) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("only")
                && replacementSlot + 1 < rules.size()) {
            wholeBlock = true;
            lastRuleAt = replacementSlot + 1;
        }
        Value replacement = valueToPutInLookedUpFirst(rules.get(lastRuleAt));
        List<Value> putting = !wholeBlock && replacement instanceof BlockValue spread
                && spread.datatype() == Datatype.BLOCK
                ? spread.remaining()
                : List.of(replacement);
        removeSpan(begin, count);
        for (int added = putting.size(); added > 0; added--) {
            input.add(begin, putting.get(added - 1));
            if (source != null) {
                theBlockBeingWalked().storage().insertAt(source.index() + begin, putting.get(added - 1));
            }
        }
        position = begin + putting.size();
        return lastRuleAt + 1 - at;
    }

    private int guardOverABlock(List<Value> rules, int at) {
        Value condition = following(rules, at, "if");
        if (!(condition instanceof BlockValue paren)
                || paren.datatype() != Datatype.PAREN) {
            return NO_MATCH;
        }
        return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context).isTruthy()
                ? 2
                : NO_MATCH;
    }

    private void refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(Value target) {
        Datatype kind = target.datatype();
        boolean holdsWhatWeParse = kind == Datatype.BINARY && parsing == Datatype.BINARY;
        if (!holdsWhatWeParse
                && kind != Datatype.BLOCK && kind != Datatype.PAREN && kind != Datatype.HASH) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE);
        }
    }

    private int collectOverABlock(List<Value> rules, int at) {
        Value next = following(rules, at, "collect");

        WordValue into = null;
        WordValue insertInto = null;
        WordValue appendTo = null;
        int ruleAt = at + 1;
        if (next instanceof WordValue keyword && keyword.datatype() == Datatype.WORD) {
            Value name = at + 2 < rules.size() ? rules.get(at + 2) : null;
            if (keyword.canonical().equals("set")) {
                into = ParseTargets.refuseAnythingButAWordOrASetWord(name);
                ruleAt = at + 3;
            } else if (keyword.canonical().equals("into")) {
                insertInto = ParseTargets.refuseAnythingButAWordOrAGetWord(name);
                ruleAt = at + 3;
            } else if (keyword.canonical().equals("after")) {
                appendTo = ParseTargets.refuseAnythingButAWordOrAGetWord(name);
                ruleAt = at + 3;
            }
        }
        if (ruleAt >= rules.size()) {
            return NO_MATCH;
        }

        BlockValue destination = null;
        if (into != null) {
            destination = BlockValue.block(new ArrayList<>());
            assignOverABlock(into, destination);
        }
        collectionsOpenInnermostLast.push(new ArrayList<>());
        int consumed = matchOneOverABlock(rules, ruleAt);
        List<Value> mine = collectionsOpenInnermostLast.pop();

        if (appendTo != null) {
            refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(valueOf(appendTo));
            if (valueOf(appendTo) instanceof BlockValue existing) {
                mine.forEach(gathered -> existing.storage().insertAt(
                        existing.storageLength() + 1, gathered));
            }
        } else if (insertInto != null) {
            Value target = valueOf(insertInto);
            refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(target);
            if (target instanceof BlockValue existing) {
                for (int added = mine.size(); added > 0; added--) {
                    existing.storage().insertAt(existing.index(), mine.get(added - 1));
                }
            }
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
        return consumed == NO_MATCH
                ? NO_MATCH
                : (ruleAt - at) + ruleSpan(rules, ruleAt);
    }

    private int keepOverABlock(List<Value> rules, int at) {
        Value kept = following(rules, at, "keep");
        if (collectionsOpenInnermostLast.isEmpty()) {
            throw Raised.of(EvaluationFailure.PARSE_NO_COLLECT,
                    "keep has no collect around it");
        }

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
            return keepIndividuallyOverABlock(rules, at + 2);
        }
        if (kept instanceof WordValue capture && capture.datatype() == Datatype.WORD
                && capture.canonical().equals("copy")) {
            return keepTheCapture(rules, at + 1);
        }

        int before = position;
        if (matchOneOverABlock(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        if (!collectionsOpenInnermostLast.isEmpty()) {
            List<Value> matched = input.subList(before, position);
            if (matched.size() == 1) {
                collectionsOpenInnermostLast.peek().add(matched.getFirst());
            } else if (!matched.isEmpty()) {
                collectionsOpenInnermostLast.peek().add(BlockValue.block(matched));
            }
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    private int keepIndividuallyOverABlock(List<Value> rules, int at) {
        int before = position;
        if (matchOneOverABlock(rules, at) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().addAll(input.subList(before, position));
        }
        return 2 + ruleSpan(rules, at);
    }

    private int keepTheCapture(List<Value> rules, int at) {
        if (at + 2 >= rules.size()) {
            return NO_MATCH;
        }
        int before = position;
        if (matchOneOverABlock(rules, at + 2) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        BlockValue captured = BlockValue.block(input.subList(before, position));
        if (rules.get(at + 1) instanceof WordValue name) {
            assignOverABlock(name, captured);
        }
        if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().add(captured);
        }
        return 3 + ruleSpan(rules, at + 2);
    }

    private int seekOverABlock(List<Value> rules, int at, boolean past) {
        Value wanted = following(rules, at, "to or thru");

        if (wanted instanceof WordValue word && word.canonical().equals("end")) {
            position = input.size();
            return 2;
        }
        wanted = whatTheWordHolds(wanted);
        while (position <= input.size()) {
            int before = position;
            if (matchValueOverABlock(wanted)) {
                position = past ? position : before;
                return 2;
            }
            position = before;
            if (atEnd()) {
                return NO_MATCH;
            }
            position++;
        }
        return NO_MATCH;
    }

    private int into(List<Value> rules, int at) {
        Value inner = whatTheWordHolds(following(rules, at, "into"));
        if (!(inner instanceof BlockValue innerRule)) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "into needs a block of rules to apply");
        }
        if (atEnd() || !(current() instanceof SeriesValue nested)
                || !steppedInto(nested, innerRule)) {
            return NO_MATCH;
        }
        position++;
        return 2;
    }

    private boolean steppedInto(SeriesValue nested, BlockValue innerRule) {
        return Parser.over(evaluator, context, nested, mindingCase)
                .matchesTheWholeOf(innerRule);
    }

    private int captureOverABlock(List<Value> rules, int at, boolean everything) {
        WordValue word = theVariableSetOrCopyWritesInto(rules, at);
        if (at + 2 >= rules.size()) {
            return NO_MATCH;
        }

        int startedAt = position;
        int consumed = matchOneOverABlock(rules, at + 2);
        if (consumed == NO_MATCH) {
            return NO_MATCH;
        }
        List<Value> taken = new ArrayList<>(input.subList(startedAt, position));
        assignOverABlock(word, everything
                ? sliceOfTheInputKeepingItsOwnDatatype(taken)
                : firstOf(taken));
        return 2 + consumed;
    }

    private WordValue theVariableSetOrCopyWritesInto(List<Value> rules, int at) {
        return ParseTargets.refuseAnythingSetAndCopyCannotWriteInto(
                at + 1 < rules.size() ? rules.get(at + 1) : null);
    }

    private Value sliceOfTheInputKeepingItsOwnDatatype(List<Value> taken) {
        BlockValue slice = BlockValue.block(taken);
        return source instanceof BlockValue whole
                ? slice.as(whole.datatype())
                : slice;
    }

    private Value firstOf(List<Value> taken) {
        return taken.isEmpty() ? NoneValue.none() : taken.getFirst();
    }

    private Value valueOf(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical())
                ? target.slotFor(word.canonical()).value()
                : NoneValue.none();
    }

    private void assignOverABlock(WordValue word, Value value) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.knows(word.canonical())) {
            target.define(word.spelling());
        }
        ContextSlot slot = target.knows(word.canonical())
                ? target.slotFor(word.canonical())
                : target.define(word.spelling());
        slot.setValue(value);
    }

    private Value following(List<Value> rules, int at, String keyword) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    keyword + " has no rule after it to apply to");
        }
        return rules.get(at + 1);
    }

    boolean matchValueOverABlock(Value rule) {
        return switch (rule) {
            case BlockValue nested when nested.datatype() == Datatype.PAREN -> {
                evaluator.evaluateOrRaise(nested.as(Datatype.BLOCK), context);
                refreshInputFromSource();
                yield true;
            }
            case BlockValue nested when nested.datatype() == Datatype.BLOCK ->
                    matchSequence(nested.remaining());
            case BitsetValue members -> !atEnd()
                    && current() instanceof CharacterValue character
                    && members.holds(character.codepoint())
                    && advanceOne();
            case DatatypeValue wanted -> matchesDatatype(wanted.represents());
            case TypesetValue wanted -> !atEnd()
                    && wanted.holds(current().datatype())
                    && advanceOne();
            case WordValue word when word.datatype() == Datatype.LIT_WORD ->
                    matchesLiteral(word.as(Datatype.WORD));
            case WordValue word when word.datatype() == Datatype.WORD ->
                    switch (word.canonical()) {
                        case "end" -> atEnd();
                        case "skip" -> advanceOne();
                        default -> matchNamedRule(word);
                    };
            case BlockValue path when path.datatype() == Datatype.LIT_PATH ->
                    matchesLiteral(path.as(Datatype.PATH));
            default -> matchesLiteral(rule);
        };
    }

    private int quoted(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "quote has no value after it to match");
        }
        Value wanted = rules.get(at + 1);
        if (wanted instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            wanted = evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        return matchesLiteral(wanted) ? 2 : NO_MATCH;
    }

    private boolean samePath(BlockValue here, BlockValue wanted) {
        List<Value> ours = here.remaining();
        List<Value> theirs = wanted.remaining();
        if (ours.size() != theirs.size()) {
            return false;
        }
        for (int at = 0; at < ours.size(); at++) {
            boolean same = mindingCase
                    ? ours.get(at).equals(theirs.get(at))
                    : looselyEqual(ours.get(at), theirs.get(at));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesDatatype(Datatype wanted) {
        if (atEnd() || current().datatype() != wanted) {
            return false;
        }
        position++;
        return true;
    }

    private boolean matchesLiteral(Value wanted) {
        if (atEnd()) {
            return false;
        }
        if (wanted instanceof IntegerValue count && !(current() instanceof IntegerValue)) {
            return false;
        }
        boolean fits = wanted instanceof BlockValue path
                && path.datatype() == Datatype.PATH
                && current() instanceof BlockValue here
                && here.datatype() == Datatype.PATH
                ? samePath(here, path)
                : mindingCase
                        ? current().equals(wanted)
                        : looselyEqual(current(), wanted);
        if (!fits) {
            return false;
        }
        position++;
        return true;
    }

    private static boolean looselyEqual(Value left, Value right) {
        if (left instanceof StringValue leftText && right instanceof StringValue rightText) {
            return leftText.datatype() == rightText.datatype()
                    && leftText.equalsIgnoringCase(rightText);
        }
        if (left instanceof WordValue leftWord && right instanceof WordValue rightWord) {
            return leftWord.namesSameAs(rightWord);
        }
        return left.equals(right);
    }



    private int[] codePoints;

    private boolean walkingBytes;



    int inputLengthOverAString() {
        return codePoints.length;
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



    private int changeMatchedOverAString(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            return NO_MATCH;
        }
        Integer markOffset = sameStorageOffsetOverAString(rules.get(at + 1));
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
        if (matchOneOverAString(rules, at + 1) == NO_MATCH) {
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

    private Integer sameStorageOffsetOverAString(Value item) {
        if (!(item instanceof WordValue word)
                || (word.datatype() != Datatype.WORD
                        && word.datatype() != Datatype.GET_WORD)
                || (word.datatype() == Datatype.WORD
                        && ParseTargets.THE_WORDS_THE_DIALECT_RESERVES.contains(word.canonical()))) {
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

    private int removeMatchedOverAString(List<Value> rules, int at) {
        if (at + 1 >= rules.size()) {
            return NO_MATCH;
        }
        Integer markOffset = sameStorageOffsetOverAString(rules.get(at + 1));
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
        if (matchOneOverAString(rules, at + 1) == NO_MATCH) {
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

    int matchOneOverAString(List<Value> rules, int at) {
        Value rule = rules.get(at);
        if (rule instanceof WordValue mark && mark.datatype() == Datatype.SET_WORD) {
            assignOverAString(mark, source.atIndex(source.index() + position));
            return 1;
        }
        if (rule instanceof WordValue back && back.datatype() == Datatype.GET_WORD) {
            Context holder = back.isBound() ? back.binding() : context;
            ParseTargets.refuseAnInputThatIsNotASeries(back, whatTheSlotHolds(holder, back));
            if (holder.slotFor(back.canonical()).value() instanceof StringValue marked) {
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
            Optional<ParseKeyword> keyword = ParseKeyword.named(word.canonical());
            if (keyword.isPresent()) {
                return applyKeyword(keyword.get(), rules, at);
            }
            return countBehind(word) != null
                    ? matchRepeat(rules, at)
                    : (matchNamedRule(word) ? 1 : NO_MATCH);
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
            return matchValueOverAString(resolved) ? 1 : -1;
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.GET_PATH) {
            return switchTheInputToWhatThisNames(path);
        }
        return matchValueOverAString(rule) ? 1 : -1;
    }

    int applyKeywordThatHasWhatItNeedsOverAString(
            ParseKeyword keyword, List<Value> rules, int at) {
        return switch (keyword) {
            case END -> endOfInput() ? 1 : NO_MATCH;
            case SKIP -> advanceOne() ? 1 : NO_MATCH;
            case TO -> seekOverAString(rules, at, false);
            case THRU -> seekOverAString(rules, at, true);
            case ANY, WHILE -> repeat(rules, at, 0);
            case SOME -> repeat(rules, at, 1);
            case OPT -> optional(rules, at);
            case AND, AHEAD -> matchTheNextRuleThenPutThePositionBack(rules, at);
            case BREAK, ACCEPT -> throw BlockEnded.asAMatch();
            case REJECT -> throw BlockEnded.asAFailure();
            case CASE -> setCaseModeWhichLastsUntilTheOtherWordAppears(true);
            case NO_CASE -> setCaseModeWhichLastsUntilTheOtherWordAppears(false);
            case CHANGE -> changeMatchedOverAString(rules, at);
            case REMOVE -> removeMatchedOverAString(rules, at);
            case INSERT -> insertValueOverAString(rules, at);
            case IF -> guardOverAString(rules, at);
            case SET -> captureOverAString(rules, at, false);
            case COPY -> captureOverAString(rules, at, true);
            case RETURN -> answerWith(rules, at);
            case COLLECT -> collectOverAString(rules, at);
            case KEEP -> keepOverAString(rules, at);
            case NOT -> negate(rules, at);
            case THEN -> commitPastTheNextAlternative(rules, at);
            case QUOTE, INTO -> aBlockOnlyKeywordNeverMatchesOneCharacter(keyword, rules, at);
            case FAIL -> NO_MATCH;
            case LIMIT -> throw Raised.of(EvaluationFailure.NOT_DONE,
                    "limit is a parse command reserved for future use");
        };
    }

    private int aBlockOnlyKeywordNeverMatchesOneCharacter(
            ParseKeyword keyword, List<Value> rules, int at) {

        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END, keyword.spelling());
        }
        return NO_MATCH;
    }

    private int switchTheInputToWhatThisNames(BlockValue path) {
        Value held = evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context);
        ParseTargets.refuseAnInputThatIsNotASeries(path, held);
        if (held instanceof StringValue marked) {
            adoptInput(marked);
            return 1;
        }
        return NO_MATCH;
    }

    private static Value whatTheSlotHolds(Context holder, WordValue word) {
        return holder.knows(word.canonical())
                ? holder.slotFor(word.canonical()).value()
                : NoneValue.none();
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
            if (matchOneOverAString(rules, ruleAt) == NO_MATCH) {
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

    private int captureOverAString(List<Value> rules, int at, boolean wholeSlice) {
        WordValue target = ParseTargets.refuseAnythingSetAndCopyCannotWriteInto(
                at + 1 < rules.size() ? rules.get(at + 1) : null);
        if (at + 2 >= rules.size()) {
            return NO_MATCH;
        }
        int ruleAt = at + 2;
        while (rules.get(ruleAt) instanceof WordValue mark
                && mark.datatype() == Datatype.SET_WORD) {
            assignOverAString(mark, source.atIndex(source.index() + position));
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
        if (matchOneOverAString(rules, ruleAt) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        assignOverAString(target, wholeSlice ? sliceFrom(before) : theFirstMatchedCharacterAloneHoweverLongTheSpanWas(before));
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

    private int collectOverAString(List<Value> rules, int at) {
        WordValue into = null;
        WordValue insertInto = null;
        WordValue appendTo = null;
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "collect has no rule after it to apply to");
        }
        int ruleAt = at + 1;
        if (rules.get(at + 1) instanceof WordValue keyword
                && keyword.datatype() == Datatype.WORD) {
            Value name = at + 2 < rules.size() ? rules.get(at + 2) : null;
            switch (keyword.canonical()) {
                case "set" -> {
                    into = ParseTargets.refuseAnythingButAWordOrASetWord(name);
                    ruleAt = at + 3;
                }
                case "into" -> {
                    insertInto = ParseTargets.refuseAnythingButAWordOrAGetWord(name);
                    ruleAt = at + 3;
                }
                case "after" -> {
                    appendTo = ParseTargets.refuseAnythingButAWordOrAGetWord(name);
                    ruleAt = at + 3;
                }
                default -> { }
            }
        }
        if (ruleAt >= rules.size()) {
            return NO_MATCH;
        }

        BlockValue destination = null;
        if (into != null) {
            destination = BlockValue.block(new ArrayList<>());
            assignOverAString(into, destination);
        }
        collectionsOpenInnermostLast.push(new ArrayList<>());
        int consumed = matchOneOverAString(rules, ruleAt);
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

    private int keepOverAString(List<Value> rules, int at) {
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
            return keepIndividuallyOverAString(rules, at + 2);
        }

        boolean keptViaCopy = kept instanceof WordValue copying
                && copying.datatype() == Datatype.WORD
                && copying.canonical().equals("copy");
        int before = position;
        if (matchOneOverAString(rules, at + 1) == NO_MATCH) {
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

    private int keepIndividuallyOverAString(List<Value> rules, int at) {
        int before = position;
        if (at >= rules.size() || matchOneOverAString(rules, at) == NO_MATCH) {
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

    private void refuseATargetThatCannotHoldWhatThisParseYieldsOverAString(Value target) {
        Datatype kind = target.datatype();
        Datatype parsing = source == null ? null : source.datatype();
        boolean suits = kind == Datatype.BLOCK || kind == Datatype.PAREN
                || kind == Datatype.HASH
                || (kind.isAnyString() && (parsing == null || parsing.isAnyString()))
                || (kind == Datatype.BINARY && (parsing == null
                        || parsing == Datatype.BINARY));
        if (!suits) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE);
        }
    }

    private void deliver(WordValue word, List<Value> gathered, boolean past) {
        Context holder = word.isBound() ? word.binding() : context;
        Value target = whatTheSlotHolds(holder, word);
        refuseATargetThatCannotHoldWhatThisParseYieldsOverAString(target);
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

    private void assignOverAString(WordValue word, Value value) {
        Context target = word.isBound() ? word.binding() : context;
        target.set(word.canonical(), value);
    }

    private int matchRepeat(List<Value> rules, int at) {
        int least = countIn(rules, at);
        Integer second = countIn(rules, at + 1);
        int ruleAt = second == null ? at + 1 : at + 2;
        return countedRepeat(rules, ruleAt, least,
                second == null ? least : second, ruleAt - at);
    }

    private boolean endOfInput() {
        return atEnd();
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
        if (matchOneOverAString(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        throw new Returned(sliceFrom(before));
    }

    private int seekOverAString(List<Value> rules, int at, boolean past) {
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
                        && ParseTargets.THE_WORDS_THE_DIALECT_RESERVES.contains(keyword.canonical()))) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "to and thru take a place or something to look for, not "
                            + Molder.mold(wanted));
        }
        wanted = whatTheWordHolds(wanted);
        if (wanted instanceof BlockValue || wanted instanceof BitsetValue) {
            for (int from = position; from <= codePoints.length; from++) {
                position = from;
                if (matchValueOverAString(wanted)) {
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

    private int insertValueOverAString(List<Value> rules, int at) {
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

    private int guardOverAString(List<Value> rules, int at) {
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

    boolean matchValueOverAString(Value rule) {
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

}
