package org.jebol.domain.parse;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * PARSE: matching input against a rule, and pulling it apart.
 *
 * <p>A rule block is not evaluated, it is matched. That is what makes PARSE
 * a sub-language rather than a function: the same block that would run as
 * code reads instead as a grammar, and a word in it names a rule rather than
 * a value.
 *
 * <p>The matcher is recursive over the rule, and the rule comes from source
 * whose nesting the reader already bounds, so a rule cannot nest deeply
 * enough to trouble the host stack. Input, which can be arbitrarily long, is
 * walked with a position rather than recursed over.
 */
public final class Parser {

    private final Evaluator evaluator;
    private final Context context;
    private final List<Value> input;

    private final BlockValue source;

    private Datatype parsing = Datatype.BLOCK;

    private int position;

    private boolean mindingCase;

    private final Deque<List<Value>> collectionsOpenInnermostLast = new ArrayDeque<>();

    private List<Value> gathered;

    private Parser(Evaluator evaluator, Context context, List<Value> input,
            BlockValue source) {
        this.evaluator = evaluator;
        this.context = context;
        this.input = new ArrayList<>(input);
        this.source = source;
    }

    private void refreshInputFromSource() {
        if (source == null) {
            return;
        }
        input.clear();
        input.addAll(source.remaining());
        position = Math.min(position, input.size());
    }

    /** Whether the whole of the input matches the whole of the rule. */
    public static boolean matches(
            Evaluator evaluator, Context context, List<Value> input, BlockValue rule) {

        Parser parser = new Parser(evaluator, context, input, null);
        return parser.matchSequence(rule.remaining()) && parser.atEnd();
    }

    /** What a parse answered: a logic, or the block COLLECT gathered. */
    public static Value answer(
            Evaluator evaluator, Context context, Value input, BlockValue rule) {
        return answer(evaluator, context, input, rule, false);
    }

    /** The same, with case either minded from the start or not. */
    public static Value answer(
            Evaluator evaluator, Context context, Value input, BlockValue rule,
            boolean mindingCase) {

        BlockValue source = input instanceof BlockValue block ? block : null;
        Parser parser = new Parser(evaluator, context,
                source != null ? source.remaining() : List.of(input), source);
        parser.parsing = input.datatype();
        parser.mindingCase = mindingCase;
        boolean matched;
        try {
            matched = parser.matchSequence(rule.remaining());
        } catch (Returned returned) {
            return returned.value();
        }
        if (parser.gathered != null) {
            return BlockValue.block(parser.gathered);
        }
        return LogicValue.of(matched && parser.atEnd());
    }

    private boolean atEnd() {
        return position >= input.size();
    }

    private Value current() {
        return input.get(position);
    }

    private boolean matchSequence(List<Value> rules) {
        List<List<Value>> alternatives = splitOnAlternatives(rules);
        int startedAt = position;

        for (List<Value> alternative : alternatives) {
            position = startedAt;
            if (matchAllOf(alternative)) {
                return true;
            }
        }
        position = startedAt;
        return false;
    }

    private boolean matchAllOf(List<Value> rules) {
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

    private static final int NO_MATCH = -1;

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

    private int matchOne(List<Value> rules, int at) {
        Value rule = rules.get(at);

        if (rule instanceof IntegerValue) {
            return matchCountedRule(rules, at);
        }
        if (rule instanceof WordValue mark && mark.datatype() == Datatype.SET_WORD) {
            assign(mark, source == null
                    ? BlockValue.block(input.subList(position, input.size()))
                    : source.atIndex(source.index() + position));
            return 1;
        }
        if (rule instanceof WordValue back && back.datatype() == Datatype.GET_WORD) {
            return seekToMark(back);
        }
        if (rule instanceof WordValue word && word.datatype() == Datatype.WORD) {
            Integer consumed = matchKeyword(word.canonical(), rules, at);
            if (consumed != null) {
                return consumed;
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
            return matchValue(resolved) ? 1 : NO_MATCH;
        }
        if (rule instanceof BlockValue path && path.datatype() == Datatype.GET_PATH) {
            ParseTargets.refuseAnInputThatIsNotASeries(path, evaluator.evaluateOrRaise(
                    BlockValue.block(List.of(path)), context));
            return NO_MATCH;
        }
        return matchValue(rule) ? 1 : NO_MATCH;
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
            if (matchOne(rules, at + countItems) == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
        }
        return matched >= least ? countItems + ruleSpan(rules, at + countItems) : NO_MATCH;
    }

    private int ruleSpan(List<Value> rules, int at) {
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
            return switch (word.canonical()) {
                case "any", "some", "opt", "to", "thru", "into", "collect", "keep",
                     "and", "ahead", "remove", "if", "insert", "while" ->
                        1 + ruleSpan(rules, at + 1);
                case "quote" -> 2;
                case "set", "copy", "change" -> 2 + ruleSpan(rules, at + 2);
                default -> 1;
            };
        }
        return 1;
    }

    private Integer matchKeyword(String keyword, List<Value> rules, int at) {
        return switch (keyword) {
            case "end" -> atEnd() ? 1 : NO_MATCH;
            case "skip" -> advanceOne() ? 1 : NO_MATCH;
            case "any", "while" -> repeat(rules, at, 0);
            case "case" -> setCaseModeWhichLastsUntilTheOtherWordAppears(true);
            case "no-case" -> setCaseModeWhichLastsUntilTheOtherWordAppears(false);
            case "some" -> repeat(rules, at, 1);
            case "opt" -> optional(rules, at);
            case "to" -> seek(rules, at, false);
            case "thru" -> seek(rules, at, true);
            case "into" -> into(rules, at);
            case "set" -> capture(rules, at, false);
            case "copy" -> capture(rules, at, true);
            case "collect" -> collect(rules, at);
            case "keep" -> keep(rules, at);
            case "quote" -> quoted(rules, at);
            case "and", "ahead" -> matchTheNextRuleThenPutThePositionBack(rules, at);
            case "if" -> guard(rules, at);
            case "remove" -> removeMatched(rules, at);
            case "change" -> changeMatched(rules, at);
            case "insert" -> insertValue(rules, at);
            case "return" -> returnFrom(rules, at);
            case "fail" -> NO_MATCH;
            default -> null;
        };
    }

    private int returnFrom(List<Value> rules, int at) {
        Value following = following(rules, at, "return");
        if (following instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            throw new Returned(evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context));
        }
        int begin = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            return NO_MATCH;
        }
        throw new Returned(BlockValue.block(List.copyOf(input.subList(begin, position))));
    }

    private int matchTheNextRuleThenPutThePositionBack(List<Value> rules, int at) {
        following(rules, at, "ahead");
        int before = position;
        boolean matched = matchOne(rules, at + 1) != NO_MATCH;
        position = before;
        return matched ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    private int changeMatched(List<Value> rules, int at) {
        Value rule = following(rules, at, "change");
        if (rule instanceof WordValue misplaced && misplaced.datatype() == Datatype.WORD
                && misplaced.canonical().equals("only")) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "only says how to put the replacement in, so it goes "
                            + "before the replacement and not before the rule");
        }
        Integer markOffset = sameStorageOffset(rule);
        if (markOffset != null) {
            return changedSpan(rules, at, markOffset);
        }
        int replacementAt = at + 1 + ruleSpan(rules, at + 1);
        if (replacementAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "change needs a value to put where the match was");
        }
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
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
                source.storage().removeAt(source.index() + taken - 1);
            }
        }
        for (int added = putting.size(); added > 0; added--) {
            input.add(before, putting.get(added - 1));
            if (source != null) {
                source.storage().insertAt(source.index() + before, putting.get(added - 1));
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

    private int insertValue(List<Value> rules, int at) {
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
                source.storage().insertAt(source.index() + position, putting.get(added0 - 1));
            }
        }
        position += putting.size();
        return valueAt + 1 - at;
    }

    private int removeMatched(List<Value> rules, int at) {
        Integer markOffset = sameStorageOffset(following(rules, at, "remove"));
        if (markOffset != null) {
            int begin = Math.min(position, markOffset);
            removeSpan(begin, Math.abs(position - markOffset));
            position = begin;
            return 2;
        }
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
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
                source.storage().removeAt(source.index() + taken - 1);
            }
        }
    }

    private Integer sameStorageOffset(Value item) {
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
                source.storage().insertAt(source.index() + begin, putting.get(added - 1));
            }
        }
        position = begin + putting.size();
        return lastRuleAt + 1 - at;
    }

    private int guard(List<Value> rules, int at) {
        Value condition = following(rules, at, "if");
        if (!(condition instanceof BlockValue paren)
                || paren.datatype() != Datatype.PAREN) {
            return NO_MATCH;
        }
        return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context).isTruthy()
                ? 2
                : NO_MATCH;
    }

    private void refuseATargetThatCannotHoldWhatThisParseYields(Value target) {
        Datatype kind = target.datatype();
        boolean holdsWhatWeParse = kind == Datatype.BINARY && parsing == Datatype.BINARY;
        if (!holdsWhatWeParse
                && kind != Datatype.BLOCK && kind != Datatype.PAREN && kind != Datatype.HASH) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE);
        }
    }

    private int collect(List<Value> rules, int at) {
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
            assign(into, destination);
        }
        collectionsOpenInnermostLast.push(new ArrayList<>());
        int consumed = matchOne(rules, ruleAt);
        List<Value> mine = collectionsOpenInnermostLast.pop();

        if (appendTo != null) {
            refuseATargetThatCannotHoldWhatThisParseYields(valueOf(appendTo));
            if (valueOf(appendTo) instanceof BlockValue existing) {
                mine.forEach(gathered -> existing.storage().insertAt(
                        existing.storageLength() + 1, gathered));
            }
        } else if (insertInto != null) {
            Value target = valueOf(insertInto);
            refuseATargetThatCannotHoldWhatThisParseYields(target);
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

    private int keep(List<Value> rules, int at) {
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
            return keepIndividually(rules, at + 2);
        }
        if (kept instanceof WordValue capture && capture.datatype() == Datatype.WORD
                && capture.canonical().equals("copy")) {
            return keepTheCapture(rules, at + 1);
        }

        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
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

    private int keepIndividually(List<Value> rules, int at) {
        int before = position;
        if (matchOne(rules, at) == NO_MATCH) {
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
        if (matchOne(rules, at + 2) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        BlockValue captured = BlockValue.block(input.subList(before, position));
        if (rules.get(at + 1) instanceof WordValue name) {
            assign(name, captured);
        }
        if (!collectionsOpenInnermostLast.isEmpty()) {
            collectionsOpenInnermostLast.peek().add(captured);
        }
        return 3 + ruleSpan(rules, at + 2);
    }

    private boolean advanceOne() {
        if (atEnd()) {
            return false;
        }
        position++;
        return true;
    }

    private int repeat(List<Value> rules, int at, int leastNeeded) {
        following(rules, at, "any or some");
        int matched = 0;
        while (true) {
            int before = position;
            int wasLong = input.size();
            if (matchOne(rules, at + 1) == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
            if (position == before && input.size() == wasLong) {
                break;
            }
        }
        return matched >= leastNeeded ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    private int optional(List<Value> rules, int at) {
        following(rules, at, "opt");
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    private Value whatTheWordHolds(Value wanted) {
        if (!(wanted instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return wanted;
        }
        Context target = word.isBound() ? word.binding() : context;
        return target.knows(word.canonical())
                ? target.slotFor(word.canonical()).value()
                : wanted;
    }

    private int seek(List<Value> rules, int at, boolean past) {
        Value wanted = following(rules, at, "to or thru");

        if (wanted instanceof WordValue word && word.canonical().equals("end")) {
            position = input.size();
            return 2;
        }
        wanted = whatTheWordHolds(wanted);
        while (position <= input.size()) {
            int before = position;
            if (matchValue(wanted)) {
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
        if (nested instanceof BlockValue block) {
            return matches(evaluator, context, block.remaining(), innerRule);
        }
        if (nested instanceof StringValue || nested instanceof BinaryValue) {
            return StringParser.matches(evaluator, context, nested, innerRule, mindingCase);
        }
        return false;
    }

    private int capture(List<Value> rules, int at, boolean everything) {
        WordValue word = theVariableSetOrCopyWritesInto(rules, at);
        if (at + 2 >= rules.size()) {
            return NO_MATCH;
        }

        int startedAt = position;
        int consumed = matchOne(rules, at + 2);
        if (consumed == NO_MATCH) {
            return NO_MATCH;
        }
        List<Value> taken = new ArrayList<>(input.subList(startedAt, position));
        assign(word, everything
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

    private void assign(WordValue word, Value value) {
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

    private Integer countIn(List<Value> rules, int at) {
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

    private boolean matchNamedRule(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.knows(word.canonical())) {
            return false;
        }
        Value held = target.slotFor(word.canonical()).value();
        if (held instanceof UnsetValue || held.datatype().isAnyFunction()) {
            throw Raised.of(EvaluationFailure.PARSE_RULE, (Value) word);
        }
        return held instanceof BlockValue rule && rule.datatype() == Datatype.BLOCK
                ? matchSequence(rule.remaining())
                : matchValue(held);
    }

    private boolean matchValue(Value rule) {
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

    private int setCaseModeWhichLastsUntilTheOtherWordAppears(boolean minding) {
        mindingCase = minding;
        return 1;
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

    private static final class Returned extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Value value;

        Returned(Value value) {
            super(null, null, false, false);
            this.value = value;
        }

        Value value() {
            return value;
        }
    }
}
