package org.jebol.domain.parse;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.SeriesContents;
import org.jebol.domain.parse.keyword.ParseKeyword;
import org.jebol.domain.parse.keyword.WhenNothingFollowsIt;
import org.jebol.domain.value.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class Parser implements ParseWalk {


    private static final ParseKeyword THE_WORD_NOT = ParseKeyword.named("not").orElseThrow();

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

    @Override
    public boolean atEnd() {
        return position >= inputLength();
    }

    @Override
    public boolean advanceOne() {
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
        return refused.isPresent() ? refused.orElseThrow() : applyKeywordThatHasWhatItNeeds(keyword, rules, at);
    }

    private Optional<Integer> refusalForAMissingArgument(ParseKeyword keyword, List<Value> rules, int at) {

        if (!keyword.needsSomethingAfterIt()) {
            return Optional.empty();
        }
        if (at + 1 >= rules.size()) {
            return Optional.of(whatAKeywordWithNothingAfterItAnswers(keyword));
        }
        return keyword.ownsTheRuleAfterIt() && at + keyword.slotsBeforeTheRuleSpan() >= rules.size() ? Optional.of(NO_MATCH) : Optional.empty();
    }

    private int whatAKeywordWithNothingAfterItAnswers(ParseKeyword keyword) {
        return switch (keyword.whenNothingFollowsIt()) {
            case DOES_NOT_MATCH -> NO_MATCH;
            case RAISES_PARSE_END -> throw Raised.of(EvaluationFailure.PARSE_END, keyword.spelling());
            case RAISES_PARSE_VARIABLE -> throw Raised.of(EvaluationFailure.PARSE_VARIABLE, keyword.spelling());
        };
    }

    @Override
    public int ruleSpan(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return 1;
        }
        if (countIn(rules, at) != null) {
            int counts = countIn(rules, at + 1) != null ? 2 : 1;
            return counts + ruleSpan(rules, at + counts);
        }
        if (rules.get(at) instanceof WordValue word && word.datatype() == Datatype.WORD) {
            if (word.canonical().equals("collect") && at + 2 < rules.size() && rules.get(at + 1) instanceof WordValue keyword && keyword.datatype() == Datatype.WORD && ParseTargets.THE_WORDS_THAT_NAME_WHERE_COLLECT_PUTS_IT.contains(keyword.canonical()) && rules.get(at + 2) instanceof WordValue) {
                return 3 + ruleSpan(rules, at + 3);
            }
            return ParseKeyword.named(word.canonical()).map(keyword -> slotsTakenBy(keyword, rules, at)).orElse(1);
        }
        return 1;
    }

    private int slotsTakenBy(ParseKeyword keyword, List<Value> rules, int at) {
        int fixed = keyword.slotsBeforeTheRuleSpan();
        return keyword.ownsTheRuleAfterIt() ? fixed + ruleSpan(rules, at + fixed) : fixed;
    }

    Integer countIn(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return null;
        }
        if (rules.get(at) instanceof IntegerValue count) {
            return (int) count.magnitude();
        }
        return rules.get(at) instanceof WordValue word && word.datatype() == Datatype.WORD ? countBehind(word) : null;
    }

    Integer countBehind(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical()) && target.slotFor(word.canonical()).value() instanceof IntegerValue count ? (int) count.magnitude() : null;
    }

    @Override
    public Value whatTheWordHolds(Value wanted) {
        if (!(wanted instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return wanted;
        }
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical()) ? target.slotFor(word.canonical()).value() : wanted;
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
        return held instanceof BlockValue rule && rule.datatype() == Datatype.BLOCK ? matchSequence(rule.remaining()) : matchValue(held);
    }

    @Override
    public int mindCaseFromHereOn(boolean minding) {
        mindingCase = minding;
        return 1;
    }

    @Override
    public int negate(List<Value> rules, int at) {
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
        return negating && matched ? NO_MATCH : negations + ruleSpan(rules, ruleAt);
    }

    private static boolean isTheWordNot(List<Value> rules, int at) {
        return at < rules.size() && rules.get(at) instanceof WordValue word && word.datatype() == Datatype.WORD && ParseKeyword.named(word.canonical()).filter(THE_WORD_NOT::equals).isPresent();
    }

    @Override
    public int giveUpTheNextAlternative(List<Value> rules, int at) {
        if (matchOne(rules, at + 1) == NO_MATCH) {
            throw new AlternativeSkipped();
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    @Override
    public int matchWithoutConsuming(List<Value> rules, int at) {

        int before = position;
        boolean matched = matchOne(rules, at + 1) != NO_MATCH;
        position = before;
        return matched ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    @Override
    public int optional(List<Value> rules, int at) {
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    @Override
    public int repeat(List<Value> rules, int at, int leastNeeded) {
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
            this.input = new ArrayList<>(block != null ? block.remaining() : List.of(given));
        } else {
            this.source = (SeriesValue) given;
            this.walkingBytes = given instanceof BinaryValue;
            this.codePoints = codePointsOfSeries((SeriesValue) given);
        }
    }

    public static Parser over(Evaluator evaluator, Context context, Value given, boolean mindingCase) {

        Parser parser = new Parser(evaluator, context, given);
        parser.mindingCase = mindingCase || parser.walkingBytes;
        return parser;
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public void moveTo(int where) {
        position = where;
    }

    @Override
    public void assign(WordValue word, Value value) {
        if (walkingABlock) {
            assignOverABlock(word, value);
        } else {
            assignOverAString(word, value);
        }
    }

    @Override
    public Value firstItemBetween(int from, int to) {
        return firstOf(List.copyOf(input.subList(from, to)));
    }

    @Override
    public Integer sameStorageOffset(Value item) {
        if (source == null || !(item instanceof WordValue word) || (word.datatype() != Datatype.WORD && word.datatype() != Datatype.GET_WORD) || (word.datatype() == Datatype.WORD && ParseTargets.THE_WORDS_THE_DIALECT_RESERVES.contains(word.canonical()))) {
            return null;
        }
        Context holder = word.isBound() ? word.binding() : context;
        if (!holder.knows(word.canonical())) {
            return null;
        }
        return holder.slotFor(word.canonical()).value() instanceof SeriesValue marked && marked.sharesStorageWith(source) ? marked.index() - source.index() : null;
    }

    @Override
    public void removeBetween(int from, int howMany) {
        if (walkingABlock) {
            removeSpan(from, howMany);
            return;
        }
        for (int taken = from + howMany; taken > from; taken--) {
            removeFromSource(source.index() + taken - 1);
        }
        codePoints = codePointsOfSeries(source);
    }

    @Override
    public void putItemsIntoTheBlockAt(int where, List<Value> putting) {
        for (int added = putting.size(); added > 0; added--) {
            input.add(where, putting.get(added - 1));
            if (source != null) {
                theBlockBeingWalked().storage().insertAt(source.index() + where, putting.get(added - 1));
            }
        }
    }

    @Override
    public int putValueIntoTheTextAt(int where, Value added) {
        int[] units = unitsToLayInWhichTheSeriesRatherThanTheValueDecides(added);
        for (int step = 0; step < units.length; step++) {
            insertIntoSource(source.index() + where + step, units[step]);
        }
        codePoints = codePointsOfSeries(source);
        return units.length;
    }

    @Override
    public void deliverTheCollectedTo(WordValue target, List<Value> mine, boolean appending) {
        if (walkingABlock) {
            Value existing = valueOf(target);
            refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(existing);
            if (existing instanceof BlockValue block) {
                int where = appending ? block.storageLength() + 1 : block.index();
                for (int added = mine.size(); added > 0; added--) {
                    block.storage().insertAt(where, mine.get(added - 1));
                }
            }
            return;
        }
        deliver(target, mine, appending);
    }

    @Override
    public boolean noCollectionIsOpen() {
        return collectionsOpenInnermostLast.isEmpty();
    }

    @Override
    public void keep(Value gathering) {
        if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().add(gathering);
        }
    }

    @Override
    public void keepEachBetween(int from, int to) {
        if (collectionsOpenInnermostLast.isEmpty()) {
            return;
        }
        if (walkingABlock) {
            collectionsOpenInnermostLast.peek().addAll(input.subList(from, to));
            return;
        }
        for (int character = from; character < to; character++) {
            collectionsOpenInnermostLast.peek().add(walkingBytes ? IntegerValue.of(codePoints[character]) : CharacterValue.of(codePoints[character]));
        }
    }

    @Override
    public java.util.Optional<Value> whatMatchedBetween(int from, int to) {
        if (!walkingABlock) {
            return to > from ? java.util.Optional.of(oneOrSliceFrom(from)) : java.util.Optional.empty();
        }
        List<Value> matched = input.subList(from, to);
        if (matched.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(matched.size() == 1 ? matched.getFirst() : BlockValue.block(matched));
    }

    @Override
    public void startCollecting() {
        collectionsOpenInnermostLast.push(new ArrayList<>());
    }

    @Override
    public List<Value> stopCollecting() {
        return collectionsOpenInnermostLast.pop();
    }

    @Override
    public void deliverTheCollected(List<Value> mine) {
        if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().add(BlockValue.block(mine));
        } else if (gathered == null) {
            gathered = mine;
        } else {
            gathered.add(BlockValue.block(mine));
        }
    }

    @Override
    public Value inputPositionedHere() {
        return source.atIndex(source.index() + position);
    }

    @Override
    public Value evaluateParen(BlockValue paren) {
        return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
    }

    @Override
    public ParseWalk walkingOver(SeriesValue nested) {
        return over(evaluator, context, nested, mindingCase);
    }

    @Override
    public Value sliceBetween(int from, int to) {
        return walkingABlock ? sliceOfTheInputKeepingItsOwnDatatype(List.copyOf(input.subList(from, to))) : textSliceBetween(from, to);
    }

    private BlockValue theBlockBeingWalked() {
        return (BlockValue) source;
    }

    @Override
    public int inputLength() {
        return walkingABlock ? inputLengthOverABlock() : inputLengthOverAString();
    }

    @Override
    public int matchOne(List<Value> rules, int at) {
        return walkingABlock ? matchOneOverABlock(rules, at) : matchOneOverAString(rules, at);
    }

    @Override
    public boolean matchValue(Value rule) {
        return walkingABlock ? matchValueOverABlock(rule) : matchValueOverAString(rule);
    }

    private int applyKeywordThatHasWhatItNeeds(ParseKeyword keyword, List<Value> rules, int at) {
        return walkingABlock ? keyword.applyToBlock(this, rules, at) : keyword.applyToString(this, rules, at);
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


    @Override
    public Value current() {
        return input.get(position);
    }

    private int seekToMark(WordValue back) {
        Context holder = back.isBound() ? back.binding() : context;
        Value held = holder.knows(back.canonical()) ? holder.slotFor(back.canonical()).value() : NoneValue.none();
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
            assignOverABlock(mark, source == null ? BlockValue.block(input.subList(position, input.size())) : source.atIndex(source.index() + position));
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
            Value resolved = evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context);
            return matchValueOverABlock(resolved) ? 1 : NO_MATCH;
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.GET_PATH) {
            ParseTargets.refuseAnInputThatIsNotASeries(path, evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context));
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
        return keyword.isPresent() ? OptionalInt.of(applyKeyword(keyword.get(), rules, at)) : OptionalInt.empty();
    }


    @Override
    public Value theValueToInsert(Value written) {
        if (written instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        if (written instanceof WordValue word) {
            return switch (word.datatype()) {
                case LIT_WORD -> word.as(Datatype.WORD);
                case WORD -> evaluator.evaluateOrRaise(BlockValue.block(List.of(word)), context);
                default -> word;
            };
        }
        if (written instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            return evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context);
        }
        return written;
    }


    void removeSpan(int begin, int count) {
        for (int taken = begin + count; taken > begin; taken--) {
            input.remove(taken - 1);
            if (source != null) {
                theBlockBeingWalked().storage().removeAt(source.index() + taken - 1);
            }
        }
    }


    private void refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(Value target) {
        Datatype kind = target.datatype();
        boolean holdsWhatWeParse = kind == Datatype.BINARY && parsing == Datatype.BINARY;
        if (!holdsWhatWeParse && kind != Datatype.BLOCK && kind != Datatype.PAREN && kind != Datatype.HASH) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE);
        }
    }


    @Override
    public WordValue theWordToWriteInto(List<Value> rules, int at) {
        return ParseTargets.refuseAnythingSetAndCopyCannotWriteInto(at + 1 < rules.size() ? rules.get(at + 1) : null);
    }

    Value sliceOfTheInputKeepingItsOwnDatatype(List<Value> taken) {
        BlockValue slice = BlockValue.block(taken);
        return source instanceof BlockValue whole ? slice.as(whole.datatype()) : slice;
    }

    Value firstOf(List<Value> taken) {
        return taken.isEmpty() ? NoneValue.none() : taken.getFirst();
    }

    private Value valueOf(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical()) ? target.slotFor(word.canonical()).value() : NoneValue.none();
    }

    private void assignOverABlock(WordValue word, Value value) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.knows(word.canonical())) {
            target.define(word.spelling());
        }
        ContextSlot slot = target.knows(word.canonical()) ? target.slotFor(word.canonical()) : target.define(word.spelling());
        slot.setValue(value);
    }

    Value following(List<Value> rules, int at, String keyword) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END, keyword + " has no rule after it to apply to");
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
            case BlockValue nested when nested.datatype() == Datatype.BLOCK -> matchSequence(nested.remaining());
            case BitsetValue members ->
                    !atEnd() && current() instanceof CharacterValue character && members.holds(character.codepoint()) && advanceOne();
            case DatatypeValue wanted -> matchesDatatype(wanted.represents());
            case TypesetValue wanted -> !atEnd() && wanted.holds(current().datatype()) && advanceOne();
            case WordValue word when word.datatype() == Datatype.LIT_WORD -> matchesLiteral(word.as(Datatype.WORD));
            case WordValue word when word.datatype() == Datatype.WORD -> switch (word.canonical()) {
                case "end" -> atEnd();
                case "skip" -> advanceOne();
                default -> matchNamedRule(word);
            };
            case BlockValue path when path.datatype() == Datatype.LIT_PATH -> matchesLiteral(path.as(Datatype.PATH));
            default -> matchesLiteral(rule);
        };
    }


    private boolean samePath(BlockValue here, BlockValue wanted) {
        List<Value> ours = here.remaining();
        List<Value> theirs = wanted.remaining();
        if (ours.size() != theirs.size()) {
            return false;
        }
        for (int at = 0; at < ours.size(); at++) {
            boolean same = mindingCase ? ours.get(at).equals(theirs.get(at)) : looselyEqual(ours.get(at), theirs.get(at));
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

    @Override
    public boolean matchesLiteral(Value wanted) {
        if (atEnd()) {
            return false;
        }
        if (wanted instanceof IntegerValue count && !(current() instanceof IntegerValue)) {
            return false;
        }
        boolean fits = wanted instanceof BlockValue path && path.datatype() == Datatype.PATH && current() instanceof BlockValue here && here.datatype() == Datatype.PATH ? samePath(here, path) : mindingCase ? current().equals(wanted) : looselyEqual(current(), wanted);
        if (!fits) {
            return false;
        }
        position++;
        return true;
    }

    private static boolean looselyEqual(Value left, Value right) {
        if (left instanceof StringValue leftText && right instanceof StringValue rightText) {
            return leftText.datatype() == rightText.datatype() && leftText.equalsIgnoringCase(rightText);
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
            default -> {
            }
        }
    }

    private void insertIntoSource(int oneBasedIndex, int item) {
        switch (source) {
            case StringValue text0 -> text0.storage().insertAt(oneBasedIndex, item);
            case BinaryValue bytes -> bytes.storage().insertAt(oneBasedIndex, item);
            default -> {
            }
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


    @Override
    public Value replacementFor(Value replacement) {
        if (replacement instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        if (replacement instanceof WordValue word && word.datatype() == Datatype.LIT_WORD) {
            return word.as(Datatype.WORD);
        }
        if (replacement instanceof WordValue word && word.datatype() == Datatype.WORD) {
            Context holder = word.isBound() ? word.binding() : context;
            if (!holder.knows(word.canonical()) || holder.slotFor(word.canonical()).value() instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return holder.slotFor(word.canonical()).value();
        }
        return replacement;
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
                    throw Raised.of(EvaluationFailure.PARSE_RULE, ":" + back.spelling() + " is not a position in what is " + "being parsed");
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
            return countBehind(word) != null ? matchRepeat(rules, at) : (matchNamedRule(word) ? 1 : NO_MATCH);
        }
        if (rule instanceof IntegerValue) {
            return matchRepeat(rules, at);
        }
        if (rule instanceof UnsetValue || rule.datatype().isAnyFunction()) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, rule);
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            Value resolved = evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context);
            return matchValueOverAString(resolved) ? 1 : -1;
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.GET_PATH) {
            return switchTheInputToWhatThisNames(path);
        }
        return matchValueOverAString(rule) ? 1 : -1;
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
        return holder.knows(word.canonical()) ? holder.slotFor(word.canonical()).value() : NoneValue.none();
    }

    private int countedRepeat(List<Value> rules, int ruleAt, int least, int most, int countWidth) {

        if (ruleAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END, rules.get(ruleAt - countWidth));
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


    @Override
    public Value firstCharacterMatchedFrom(int before) {
        if (position == before) {
            return NoneValue.none();
        }
        return walkingBytes ? IntegerValue.of(codePoints[before]) : CharacterValue.of(codePoints[before]);
    }


    Value oneOrSliceFrom(int before) {
        if (position - before != 1) {
            return textSliceBetween(before, position);
        }
        return walkingBytes ? IntegerValue.of(codePoints[before]) : CharacterValue.of(codePoints[before]);
    }

    @Override
    public Value textSliceBetween(int from, int to) {
        String taken = textBetween(from, to);
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
        boolean suits = kind == Datatype.BLOCK || kind == Datatype.PAREN || kind == Datatype.HASH || (kind.isAnyString() && (parsing == null || parsing.isAnyString())) || (kind == Datatype.BINARY && (parsing == null || parsing == Datatype.BINARY));
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
            default -> {
            }
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
        return countedRepeat(rules, ruleAt, least, second == null ? least : second, ruleAt - at);
    }

    boolean endOfInput() {
        return atEnd();
    }


    private boolean bitsetHoldsFoldingCaseUnlessAskedNotTo(BitsetValue members, int character) {
        if (members.holds(character)) {
            return true;
        }
        if (mindingCase) {
            return false;
        }
        return members.holds(Character.toLowerCase(character)) || members.holds(Character.toUpperCase(character));
    }


    private int[] unitsToLayInWhichTheSeriesRatherThanTheValueDecides(Value value) {
        return source instanceof BinaryValue ? SeriesContents.octetsContributedBy(value) : SeriesContents.charactersContributedBy(value);
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
            if (position >= codePoints.length || !bitsetHoldsFoldingCaseUnlessAskedNotTo(members, codePoints[position])) {
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
        return one == other || !mindingCase && Character.toLowerCase(one) == Character.toLowerCase(other);
    }

    @Override
    public int firstMatchFrom(int[] needle, int from) {
        for (int at = from; at + needle.length <= codePoints.length; at++) {
            if (matchesAt(needle, at)) {
                return at;
            }
        }
        return -1;
    }

    @Override
    public String theTextOf(Value value) {
        return textOf(value);
    }

    static String textOf(Value value) {
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
