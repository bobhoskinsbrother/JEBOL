package org.jebol.domain.parse;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.Raised;
import org.jebol.domain.eval.SeriesContents;
import org.jebol.domain.parse.keyword.ParseKeyword;
import org.jebol.domain.value.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class Parser implements ParseWalk {


    private static final ParseKeyword THE_WORD_NOT = ParseKeyword.findTheKeywordNamed("not").orElseThrow();

    Evaluator evaluator;
    Context context;

    RebolSeries source;
    int position;
    boolean mindingCase;
    boolean blockEndedInAMatch;

    Deque<List<Value>> collectionsOpenInnermostLast = new ArrayDeque<>();
    List<Value> gathered;

    public Value answerFor(AnyBlockValue rule) {
        boolean matched;
        try {
            matched = matchSequence(rule.remaining());
        } catch (Returned decided) {
            return decided.answer();
        }
        if (gathered != null) {
            return BlockValue.of(gathered);
        }
        return LogicValue.of(matched && atEnd());
    }

    public boolean matchesTheWholeOf(AnyBlockValue rule) {
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
                if (ended.endedAsMatch()) {
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
            if (rule instanceof AnyWordValue word && word.spelling().equals("|")) {
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
        Optional<Integer> refused = refuseAMissingArgument(keyword, rules, at);
        return refused.isPresent() ? refused.orElseThrow() : applyKeywordThatHasWhatItNeeds(keyword, rules, at);
    }

    private Optional<Integer> refuseAMissingArgument(ParseKeyword keyword, List<Value> rules, int at) {

        if (!keyword.needsSomethingAfterIt()) {
            return Optional.empty();
        }
        if (at + 1 >= rules.size()) {
            return Optional.of(answerForKeywordAlone(keyword));
        }
        return keyword.ownsTheRuleAfterIt() && at + keyword.slotsBeforeTheRuleSpan() >= rules.size() ? Optional.of(NO_MATCH) : Optional.empty();
    }

    private int answerForKeywordAlone(ParseKeyword keyword) {
        return switch (keyword.describeWhenNothingFollows()) {
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
        if (rules.get(at) instanceof WordValue word) {
            if (word.canonical().equals("collect") && at + 2 < rules.size() && rules.get(at + 1) instanceof WordValue keyword && ParseTargets.THE_WORDS_THAT_NAME_WHERE_COLLECT_PUTS_IT.contains(keyword.canonical()) && rules.get(at + 2) instanceof AnyWordValue) {
                return 3 + ruleSpan(rules, at + 3);
            }
            return ParseKeyword.findTheKeywordNamed(word.canonical()).map(keyword -> countSlotsTakenBy(keyword, rules, at)).orElse(1);
        }
        return 1;
    }

    private int countSlotsTakenBy(ParseKeyword keyword, List<Value> rules, int at) {
        int fixed = keyword.slotsBeforeTheRuleSpan();
        return keyword.ownsTheRuleAfterIt() ? fixed + ruleSpan(rules, at + fixed) : fixed;
    }

    Integer countIn(List<Value> rules, int at) {
        if (at >= rules.size()) {
            return null;
        }
        if (rules.get(at) instanceof IntegerValue(long magnitude)) {
            return (int) magnitude;
        }
        return rules.get(at) instanceof WordValue word ? countBehind(word) : null;
    }

    Integer countBehind(AnyWordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.contains(word.canonical()) && target.findSlotFor(word.canonical()).value() instanceof IntegerValue(
                long magnitude
        ) ? (int) magnitude : null;
    }

    @Override
    public Value readWhatTheWordHolds(Value wanted) {
        if (!(wanted instanceof WordValue word)) {
            return wanted;
        }
        Context target = word.isBound() ? word.binding() : context;
        return target.contains(word.canonical()) ? target.findSlotFor(word.canonical()).value() : wanted;
    }

    boolean matchNamedRule(AnyWordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.contains(word.canonical())) {
            return false;
        }
        Value held = target.findSlotFor(word.canonical()).value();
        if (held instanceof UnsetValue || held instanceof AnyFunctionValue) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, word);
        }
        return held instanceof BlockValue rule ? matchSequence(rule.remaining()) : matchValue(held);
    }

    @Override
    public int mindCaseFromHereOn(boolean minding) {
        mindingCase = minding;
        return 1;
    }

    @Override
    public int negate(List<Value> rules, int at) {
        int negations = 1;
        while (isWordNot(rules, at + negations)) {
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

    private static boolean isWordNot(List<Value> rules, int at) {
        return at < rules.size() && rules.get(at) instanceof WordValue word && ParseKeyword.findTheKeywordNamed(word.canonical()).filter(THE_WORD_NOT::equals).isPresent();
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
        this.walkingABlock = !(given instanceof AnyStringValue || given instanceof BinaryValue);
        this.parsing = given.datatype();
        if (walkingABlock) {
            AnyBlockValue block = given instanceof AnyBlockValue whole ? whole : null;
            this.source = block;
            this.input = new ArrayList<>(block != null ? block.remaining() : List.of(given));
        } else {
            this.source = (RebolSeries) given;
            this.walkingBytes = given instanceof BinaryValue;
            this.codePoints = readTheCodePointsOf((RebolSeries) given);
        }
    }

    public static Parser parseOver(Evaluator evaluator, Context context, Value given, boolean mindingCase) {

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
    public void assign(AnyWordValue word, Value value) {
        if (walkingABlock) {
            assignOverBlock(word, value);
        } else {
            assignOverString(word, value);
        }
    }

    @Override
    public Value readFirstItemBetween(int from, int to) {
        return takeTheFirstOf(List.copyOf(input.subList(from, to)));
    }

    @Override
    public Integer findTheSameStorageOffset(Value item) {
        if (source == null || !(item instanceof AnyWordValue word) || !word.looksUpItsDeclaration() || (word instanceof WordValue && ParseTargets.THE_WORDS_THE_DIALECT_RESERVES.contains(word.canonical()))) {
            return null;
        }
        Context holder = word.isBound() ? word.binding() : context;
        if (!holder.contains(word.canonical())) {
            return null;
        }
        return holder.findSlotFor(word.canonical()).value() instanceof RebolSeries marked && marked.sharesStorageWith(source) ? marked.index() - source.index() : null;
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
        codePoints = readTheCodePointsOf(source);
    }

    @Override
    public void putItemsIntoTheBlockAt(int where, List<Value> putting) {
        for (int added = putting.size(); added > 0; added--) {
            input.add(where, putting.get(added - 1));
            if (source != null) {
                readBlockBeingWalked().storage().insertAt(source.index() + where, putting.get(added - 1));
            }
        }
    }

    @Override
    public int putValueIntoTheTextAt(int where, Value added) {
        int[] units = chooseUnitsToLayIn(added);
        for (int step = 0; step < units.length; step++) {
            insertIntoSource(source.index() + where + step, units[step]);
        }
        codePoints = readTheCodePointsOf(source);
        return units.length;
    }

    @Override
    public void deliverCollectedTo(AnyWordValue target, List<Value> mine, boolean appending) {
        if (walkingABlock) {
            Value existing = valueOf(target);
            refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(existing);
            if (existing instanceof AnyBlockValue block) {
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
    public boolean hasNoCollectionOpen() {
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
    public java.util.Optional<Value> readWhatMatchedBetween(int from, int to) {
        if (!walkingABlock) {
            return to > from ? java.util.Optional.of(takeOneOrASliceFrom(from)) : java.util.Optional.empty();
        }
        List<Value> matched = input.subList(from, to);
        if (matched.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(matched.size() == 1 ? matched.getFirst() : BlockValue.of(matched));
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
    public void deliverCollected(List<Value> mine) {
        if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().add(BlockValue.of(mine));
        } else if (gathered == null) {
            gathered = mine;
        } else {
            gathered.add(BlockValue.of(mine));
        }
    }

    @Override
    public Value inputPositionedHere() {
        return source.atIndex(source.index() + position);
    }

    @Override
    public Value evaluateParen(AnyBlockValue paren) {
        return evaluator.evaluateOrRaise(paren.asBlock(), context);
    }

    @Override
    public ParseWalk walkOver(RebolSeries nested) {
        return parseOver(evaluator, context, nested, mindingCase);
    }

    @Override
    public Value sliceBetween(int from, int to) {
        return walkingABlock ? sliceOfTheInputKeepingItsOwnDatatype(List.copyOf(input.subList(from, to))) : textSliceBetween(from, to);
    }

    private AnyBlockValue readBlockBeingWalked() {
        return (AnyBlockValue) source;
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


    private Datatype parsing = BlockValue.TYPE;


    int inputLengthOverABlock() {
        return input.size();
    }

    private void refreshInputFromSource() {
        if (source == null) {
            return;
        }
        input.clear();
        input.addAll(readBlockBeingWalked().remaining());
        position = Math.min(position, input.size());
    }


    @Override
    public Value current() {
        return input.get(position);
    }

    private int seekToMark(AnyWordValue back) {
        Context holder = back.isBound() ? back.binding() : context;
        Value held = holder.contains(back.canonical()) ? holder.findSlotFor(back.canonical()).value() : NoneValue.none();
        ParseTargets.refuseAnInputThatIsNotASeries(back, held);
        if (!(held instanceof AnyBlockValue marked)) {
            return NO_MATCH;
        }
        if (!marked.sharesStorageWith(source)) {
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
        if (rule instanceof SetWordValue mark) {
            assignOverBlock(mark, source == null ? BlockValue.of(input.subList(position, input.size())) : source.atIndex(source.index() + position));
            return 1;
        }
        if (rule instanceof GetWordValue back) {
            return seekToMark(back);
        }
        if (rule instanceof WordValue word) {
            OptionalInt consumed = matchKeyword(word.canonical(), rules, at);
            if (consumed.isPresent()) {
                return consumed.getAsInt();
            }
            if (countBehind(word) != null) {
                return matchCountedRule(rules, at);
            }
            return matchNamedRule(word) ? 1 : NO_MATCH;
        }
        if (rule instanceof UnsetValue || rule instanceof AnyFunctionValue) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, rule);
        }
        if (rule instanceof PathValue path) {
            Value resolved = evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context);
            return matchValueOverABlock(resolved) ? 1 : NO_MATCH;
        }
        if (rule instanceof GetPathValue path) {
            ParseTargets.refuseAnInputThatIsNotASeries(path, evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context));
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
        Optional<ParseKeyword> keyword = ParseKeyword.findTheKeywordNamed(spelling);
        return keyword.isPresent() ? OptionalInt.of(applyKeyword(keyword.get(), rules, at)) : OptionalInt.empty();
    }


    @Override
    public Value chooseValueToInsert(Value written) {
        if (written instanceof ParenValue paren) {
            return evaluator.evaluateOrRaise(paren.asBlock(), context);
        }
        if (written instanceof AnyWordValue word) {
            return switch (word) {
                case LitWordValue quoted -> quoted.asWord();
                case WordValue plain -> evaluator.evaluateOrRaise(BlockValue.of(List.of(plain)), context);
                default -> word;
            };
        }
        if (written instanceof PathValue path) {
            return evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context);
        }
        return written;
    }


    void removeSpan(int begin, int count) {
        for (int taken = begin + count; taken > begin; taken--) {
            input.remove(taken - 1);
            if (source != null) {
                readBlockBeingWalked().storage().removeAt(source.index() + taken - 1);
            }
        }
    }


    private void refuseATargetThatCannotHoldWhatThisParseYieldsOverABlock(Value target) {
        Datatype kind = target.datatype();
        boolean holdsWhatWeParse = kind == BinaryValue.TYPE && parsing == BinaryValue.TYPE;
        if (!holdsWhatWeParse && kind != BlockValue.TYPE && kind != ParenValue.TYPE && kind != HashValue.TYPE) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE);
        }
    }


    @Override
    public AnyWordValue findWordToWriteInto(List<Value> rules, int at) {
        return ParseTargets.refuseAnythingSetAndCopyCannotWriteInto(at + 1 < rules.size() ? rules.get(at + 1) : null);
    }

    Value sliceOfTheInputKeepingItsOwnDatatype(List<Value> taken) {
        AnyBlockValue slice = BlockValue.of(taken);
        return source instanceof AnyBlockValue whole ? whole.wrap(taken) : slice;
    }

    Value takeTheFirstOf(List<Value> taken) {
        return taken.isEmpty() ? NoneValue.none() : taken.getFirst();
    }

    private Value valueOf(AnyWordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        return target.contains(word.canonical()) ? target.findSlotFor(word.canonical()).value() : NoneValue.none();
    }

    private void assignOverBlock(AnyWordValue word, Value value) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.contains(word.canonical())) {
            target.register(word.spelling());
        }
        ContextSlot slot = target.contains(word.canonical()) ? target.findSlotFor(word.canonical()) : target.register(word.spelling());
        slot.setValue(value);
    }

    Value readFollowing(List<Value> rules, int at, String keyword) {
        if (at + 1 >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END, keyword + " has no rule after it to apply to");
        }
        return rules.get(at + 1);
    }

    boolean matchValueOverABlock(Value rule) {
        return switch (rule) {
            case ParenValue nested -> {
                evaluator.evaluateOrRaise(nested.asBlock(), context);
                refreshInputFromSource();
                yield true;
            }
            case BlockValue nested -> matchSequence(nested.remaining());
            case BitsetValue members ->
                    !atEnd() && current() instanceof CharacterValue(int codepoint) && members.holds(codepoint) && advanceOne();
            case Datatype wanted -> matchesDatatype(wanted);
            case TypesetValue wanted -> !atEnd() && wanted.holds(current().datatype()) && advanceOne();
            case LitWordValue quoted -> matchesLiteral(quoted.asWord());
            case WordValue word -> switch (word.canonical()) {
                case "end" -> atEnd();
                case "skip" -> advanceOne();
                default -> matchNamedRule(word);
            };
            case LitPathValue path -> matchesLiteral(path.asPath());
            default -> matchesLiteral(rule);
        };
    }


    private boolean isSamePath(AnyBlockValue here, AnyBlockValue wanted) {
        List<Value> ours = here.remaining();
        List<Value> theirs = wanted.remaining();
        if (ours.size() != theirs.size()) {
            return false;
        }
        for (int at = 0; at < ours.size(); at++) {
            boolean same = mindingCase ? ours.get(at).equals(theirs.get(at)) : areLooselyEqual(ours.get(at), theirs.get(at));
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
        boolean fits = wanted instanceof PathValue path && current() instanceof PathValue here ? isSamePath(here, path) : mindingCase ? current().equals(wanted) : areLooselyEqual(current(), wanted);
        if (!fits) {
            return false;
        }
        position++;
        return true;
    }

    private static boolean areLooselyEqual(Value left, Value right) {
        if (left instanceof AnyStringValue leftText && right instanceof AnyStringValue rightText) {
            return leftText.datatype() == rightText.datatype() && leftText.equalsIgnoringCase(rightText);
        }
        if (left instanceof AnyWordValue leftWord && right instanceof AnyWordValue rightWord) {
            return leftWord.namesSameAs(rightWord);
        }
        return left.equals(right);
    }


    private int[] codePoints;

    private boolean walkingBytes;


    int inputLengthOverAString() {
        return codePoints.length;
    }

    private void adoptInput(RebolSeries newInput) {
        this.source = newInput;
        this.walkingBytes = newInput instanceof BinaryValue;
        this.codePoints = readTheCodePointsOf(newInput);
        this.position = 0;
    }

    private void removeFromSource(int oneBasedIndex) {
        switch (source) {
            case AnyStringValue text0 -> text0.storage().removeAt(oneBasedIndex);
            case BinaryValue bytes -> bytes.storage().removeAt(oneBasedIndex);
            default -> {
            }
        }
    }

    private void insertIntoSource(int oneBasedIndex, int item) {
        switch (source) {
            case AnyStringValue text0 -> text0.storage().insertAt(oneBasedIndex, item);
            case BinaryValue bytes -> bytes.storage().insertAt(oneBasedIndex, item);
            default -> {
            }
        }
    }

    private static int[] readTheCodePointsOf(RebolSeries series) {
        if (series instanceof AnyStringValue text) {
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
        if (replacement instanceof ParenValue paren) {
            return evaluator.evaluateOrRaise(paren.asBlock(), context);
        }
        if (replacement instanceof LitWordValue word) {
            return word.asWord();
        }
        if (replacement instanceof WordValue word) {
            Context holder = word.isBound() ? word.binding() : context;
            if (!holder.contains(word.canonical()) || holder.findSlotFor(word.canonical()).value() instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return holder.findSlotFor(word.canonical()).value();
        }
        return replacement;
    }


    int matchOneOverAString(List<Value> rules, int at) {
        Value rule = rules.get(at);
        if (rule instanceof SetWordValue mark) {
            assignOverString(mark, source.atIndex(source.index() + position));
            return 1;
        }
        if (rule instanceof GetWordValue back) {
            Context holder = back.isBound() ? back.binding() : context;
            ParseTargets.refuseAnInputThatIsNotASeries(back, readWhatTheSlotHolds(holder, back));
            if (holder.findSlotFor(back.canonical()).value() instanceof AnyStringValue marked) {
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
        if (rule instanceof WordValue word) {
            Optional<ParseKeyword> keyword = ParseKeyword.findTheKeywordNamed(word.canonical());
            if (keyword.isPresent()) {
                return applyKeyword(keyword.get(), rules, at);
            }
            return countBehind(word) != null ? matchRepeat(rules, at) : (matchNamedRule(word) ? 1 : NO_MATCH);
        }
        if (rule instanceof IntegerValue) {
            return matchRepeat(rules, at);
        }
        if (rule instanceof UnsetValue || rule instanceof AnyFunctionValue) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, rule);
        }
        if (rule instanceof PathValue path) {
            Value resolved = evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context);
            return matchValueOverAString(resolved) ? 1 : -1;
        }
        if (rule instanceof GetPathValue path) {
            return switchTheInputToWhatThisNames(path);
        }
        return matchValueOverAString(rule) ? 1 : -1;
    }


    private int switchTheInputToWhatThisNames(AnyBlockValue path) {
        Value held = evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context);
        ParseTargets.refuseAnInputThatIsNotASeries(path, held);
        if (held instanceof AnyStringValue marked) {
            adoptInput(marked);
            return 1;
        }
        return NO_MATCH;
    }

    private static Value readWhatTheSlotHolds(Context holder, AnyWordValue word) {
        return holder.contains(word.canonical()) ? holder.findSlotFor(word.canonical()).value() : NoneValue.none();
    }

    private int repeatACount(List<Value> rules, int ruleAt, int least, int most, int countWidth) {

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
    public Value readFirstCharacterMatchedFrom(int before) {
        if (position == before) {
            return NoneValue.none();
        }
        return walkingBytes ? IntegerValue.of(codePoints[before]) : CharacterValue.of(codePoints[before]);
    }


    Value takeOneOrASliceFrom(int before) {
        if (position - before != 1) {
            return textSliceBetween(before, position);
        }
        return walkingBytes ? IntegerValue.of(codePoints[before]) : CharacterValue.of(codePoints[before]);
    }

    @Override
    public Value textSliceBetween(int from, int to) {
        String taken = textBetween(from, to);
        if (!walkingBytes) {
            return source instanceof AnyStringValue text ? text.wrap(taken) : StringValue.of(taken);
        }
        int[] octets = new int[taken.length()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = taken.charAt(at);
        }
        return BinaryValue.of(octets);
    }

    private void refuseATargetThatCannotHoldWhatThisParseYieldsOverAString(Value target) {
        boolean suits = target instanceof BlockValue
                || target instanceof ParenValue
                || target instanceof HashValue
                || target instanceof AnyStringValue && (source == null || source instanceof AnyStringValue)
                || target instanceof BinaryValue && (source == null || source instanceof BinaryValue);
        if (!suits) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE);
        }
    }

    private void deliver(AnyWordValue word, List<Value> gathered, boolean past) {
        Context holder = word.isBound() ? word.binding() : context;
        Value target = readWhatTheSlotHolds(holder, word);
        refuseATargetThatCannotHoldWhatThisParseYieldsOverAString(target);
        switch (target) {
            case AnyBlockValue existing -> {
                int where = past ? existing.storageLength() + 1 : existing.index();
                for (int added = gathered.size(); added > 0; added--) {
                    existing.storage().insertAt(where, gathered.get(added - 1));
                }
            }
            case AnyStringValue existing -> {
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
                    if (item instanceof IntegerValue(long magnitude)) {
                        octets.add((int) magnitude);
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

    private void assignOverString(AnyWordValue word, Value value) {
        Context target = word.isBound() ? word.binding() : context;
        target.register(word.canonical(), value);
    }

    private int matchRepeat(List<Value> rules, int at) {
        int least = countIn(rules, at);
        Integer second = countIn(rules, at + 1);
        int ruleAt = second == null ? at + 1 : at + 2;
        return repeatACount(rules, ruleAt, least, second == null ? least : second, ruleAt - at);
    }

    boolean endOfInput() {
        return atEnd();
    }


    private boolean holdsFoldingCase(BitsetValue members, int character) {
        if (members.holds(character)) {
            return true;
        }
        if (mindingCase) {
            return false;
        }
        return members.holds(Character.toLowerCase(character)) || members.holds(Character.toUpperCase(character));
    }


    private int[] chooseUnitsToLayIn(Value value) {
        return source instanceof BinaryValue ? SeriesContents.collectOctetsContributedBy(value) : SeriesContents.collectCharactersContributedBy(value);
    }


    boolean matchValueOverAString(Value rule) {
        if (rule instanceof NoneValue) {
            return true;
        }
        if (rule instanceof ParenValue nested) {
            evaluator.evaluateOrRaise(nested.asBlock(), context);
            codePoints = readTheCodePointsOf(source);
            position = Math.min(position, codePoints.length);
            return true;
        }
        if (rule instanceof AnyBlockValue nested) {
            return matchSequence(nested.remaining());
        }
        if (rule instanceof BitsetValue members) {
            if (position >= codePoints.length || !holdsFoldingCase(members, codePoints[position])) {
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
            if (!isSameLetter(codePoints[from + at], wanted[at])) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameLetter(int one, int other) {
        return one == other || !mindingCase && Character.toLowerCase(one) == Character.toLowerCase(other);
    }

    @Override
    public int findFirstMatchFrom(int[] needle, int from) {
        for (int at = from; at + needle.length <= codePoints.length; at++) {
            if (matchesAt(needle, at)) {
                return at;
            }
        }
        return -1;
    }

    @Override
    public String readTheTextOf(Value value) {
        return textOf(value);
    }

    static String textOf(Value value) {
        if (value instanceof TagValue) {
            return Molder.mold(value);
        }
        if (value instanceof AnyStringValue text) {
            return text.text();
        }
        if (value instanceof BinaryValue bytes) {
            return new String(readTheCodePointsOf(bytes), 0, bytes.lengthFromHere());
        }
        return Molder.form(value);
    }

}
