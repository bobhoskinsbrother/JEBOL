package org.jebol.domain.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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

    /**
     * The series being matched, when there is one to change.
     *
     * <p>REMOVE takes what it matched out of the input, so the parser needs
     * the series and not only a snapshot of its items. Null when the caller
     * handed over a plain list, in which case REMOVE has nothing to shorten.
     */
    private final BlockValue source;

    /**
     * What kind of thing this parse was given.
     *
     * <p>Kept apart from {@link #source}, which is null for anything that
     * is not a block. A binary arrives as a single item in a list of one
     * rather than as a series being walked, so the input's own datatype is
     * the only record of what is being parsed.
     */
    private Datatype parsing = Datatype.BLOCK;

    private int position;

    /**
     * Whether the rules from here on mind case.
     *
     * <p>Off until CASE turns it on, because a parse folds case by
     * default. A mode rather than a refinement so one rule can mind case
     * in one place and not in another -- and the string walker has had
     * one all along, which is why the same rule behaved differently
     * depending on what was being parsed.
     */
    private boolean mindingCase;

    /**
     * The collections COLLECT has open, innermost last.
     *
     * <p>COLLECT changes what PARSE answers: ordinarily a parse is a
     * question with a logic for an answer, and with COLLECT it is an
     * extraction whose answer is the outermost of these.
     *
     * <p>A stack rather than one list, because COLLECT nests and an inner
     * one's block is kept in the enclosing collection rather than merged
     * into it: {@code [collect [collect [collect []]]]} answers
     * {@code [[[]]]}. One flat list cannot express that.
     */
    private final Deque<List<Value>> collecting = new ArrayDeque<>();

    /** The outermost collection, once the walk is over. */
    private List<Value> gathered;

    private Parser(Evaluator evaluator, Context context, List<Value> input,
            BlockValue source) {
        this.evaluator = evaluator;
        this.context = context;
        this.input = new ArrayList<>(input);
        this.source = source;
    }

    /** Whether the whole of the input matches the whole of the rule. */
    public static boolean matches(
            Evaluator evaluator, Context context, List<Value> input, BlockValue rule) {

        Parser parser = new Parser(evaluator, context, input, null);
        return parser.matchSequence(rule.remaining()) && parser.atEnd();
    }

    /**
     * What a parse answered: a logic, or the block COLLECT gathered.
     *
     * <p>Leftover input is not a failure once COLLECT is involved, because
     * the question is no longer whether the rule accounted for all of it.
     */
    public static Value answer(
            Evaluator evaluator, Context context, Value input, BlockValue rule) {
        return answer(evaluator, context, input, rule, false);
    }

    /**
     * The same, with case either minded from the start or not.
     *
     * <p>`if (IS_BINARY(item) || (parse->flags & PF_CASED)) parse->flags
     * |= PF_CASE` in the C: /CASE sets the mode the CASE and NO-CASE rule
     * words switch between, thus it needs no machinery of its own. It was
     * reaching the string walker and not this one, so `parse/case [a]
     * ['A]` folded case and matched.
     */
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

    /**
     * Matches a sequence of rule items in order, backtracking as a whole if
     * any of them fails. Alternatives split the sequence at {@code |}, and
     * the first that matches wins.
     */
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

    /**
     * Moves back to a place a set-word recorded, if it named one.
     *
     * <p>A mark from a different series, or from before this parse began,
     * is not a place this rule can reach. Answering no match rather than
     * moving keeps the rule honest instead of leaving the position
     * somewhere the walker cannot read.
     */
    private int seekToMark(WordValue back) {
        Context holder = back.isBound() ? back.binding() : context;
        if (!holder.knows(back.canonical())
                || !(holder.slotFor(back.canonical()).value() instanceof BlockValue marked)) {
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

    /**
     * Matches the rule item at {@code at}, and says how many rule items it
     * used up. A keyword such as {@code some} uses two: itself and what it
     * applies to.
     */
    private int matchOne(List<Value> rules, int at) {
        Value rule = rules.get(at);

        if (rule instanceof IntegerValue) {
            return matchCountedRule(rules, at);
        }
        // A set-word marks where the parse has reached and consumes
        // nothing; a get-word seeks back to what one recorded. The string
        // walker had both and this one had neither, so `p:` was taken as
        // a value to match and failed against whatever was there.
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
            return matchNamedRule(word) ? 1 : NO_MATCH;
        }
        return matchValue(rule) ? 1 : NO_MATCH;
    }

    /**
     * An integer in a rule is a repeat count, never a value to match.
     *
     * <p>This is the difference between the two languages that catches
     * people, and it caught this corpus: {@code [any 1]} is not "any number
     * of ones", it is ANY applied once with nothing left for it to repeat,
     * and it raises. One integer is an exact count and two are an inclusive
     * range, so {@code [2 3 integer!]} matches two or three of them.
     */
    private int matchCountedRule(List<Value> rules, int at) {
        long least = ((IntegerValue) rules.get(at)).magnitude();
        int countItems = 1;
        long most = least;

        if (at + 1 < rules.size() && rules.get(at + 1) instanceof IntegerValue upper) {
            most = upper.magnitude();
            countItems = 2;
        }
        if (at + countItems >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "a repeat count has no rule after it to repeat");
        }

        int matched = 0;
        while (matched < most) {
            int before = position;
            // No no-progress guard here, unlike ANY and SOME. The count
            // bounds this loop already, so it cannot run for ever, and
            // stopping early on a round that consumed nothing loses rounds
            // that were explicitly asked for: `collect 2 [collect [] (...)
            // keep (...)]` must run both although neither round moves.
            if (matchOne(rules, at + countItems) == NO_MATCH) {
                position = before;
                break;
            }
            matched++;
        }
        return matched >= least ? countItems + ruleSpan(rules, at + countItems) : NO_MATCH;
    }

    /**
     * How many rule items the rule starting at {@code at} occupies, without
     * matching anything.
     *
     * <p>Needed because a rule that matched nothing still has to be stepped
     * over. {@code [any integer!]} against empty input matches, and the walk
     * then has to know that ANY and its rule were two items rather than one.
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
            // COLLECT may be followed by SET, INTO or AFTER and a word
            // before its rule, and then it occupies two more items than
            // the plain form. Measuring it as two left the walk resuming
            // on the word and trying to match it as a rule, which is why
            // `collect into a [...]` collected nothing at all.
            if (word.canonical().equals("collect")
                    && at + 2 < rules.size()
                    && rules.get(at + 1) instanceof WordValue keyword
                    && keyword.datatype() == Datatype.WORD
                    && java.util.Set.of("set", "into", "after").contains(keyword.canonical())
                    && rules.get(at + 2) instanceof WordValue) {
                return 3 + ruleSpan(rules, at + 3);
            }
            return switch (word.canonical()) {
                case "any", "some", "opt", "to", "thru", "into", "collect", "keep",
                     "and", "ahead", "remove", "if", "insert", "while" ->
                        1 + ruleSpan(rules, at + 1);
                // CHANGE takes a rule and then the value to put in its
                // place, so it spans both.
                case "quote" -> 2;
                case "set", "copy", "change" -> 2 + ruleSpan(rules, at + 2);
                default -> 1;
            };
        }
        return 1;
    }

    /** The keywords, or null when the word is not one. */
    private Integer matchKeyword(String keyword, List<Value> rules, int at) {
        return switch (keyword) {
            case "end" -> atEnd() ? 1 : NO_MATCH;
            case "skip" -> advanceOne() ? 1 : NO_MATCH;
            // WHILE is ANY under another name, and every keyword the two
            // parsers share must be in both. Five were in one and not the
            // other, which made a rule that worked on a string fail on a
            // block and the reverse.
            case "any", "while" -> repeat(rules, at, 0);
            case "case" -> setCaseMode(true);
            case "no-case" -> setCaseMode(false);
            case "some" -> repeat(rules, at, 1);
            case "opt" -> optional(rules, at);
            case "to" -> seek(rules, at, false);
            case "thru" -> seek(rules, at, true);
            case "into" -> into(rules, at);
            case "set" -> capture(rules, at, false);
            case "copy" -> capture(rules, at, true);
            case "collect" -> collect(rules, at);
            case "keep" -> keep(rules, at);
            // `case SYM_QUOTE` in the C: the next rule item is a value
            // to match rather than a rule to run, and a paren there is
            // evaluated first. It is how a rule matches a word that would
            // otherwise name a rule.
            case "quote" -> quoted(rules, at);
            case "and", "ahead" -> lookahead(rules, at);
            case "if" -> guard(rules, at);
            case "remove" -> removeMatched(rules, at);
            case "change" -> changeMatched(rules, at);
            case "insert" -> insertValue(rules, at);
            case "return" -> returnFrom(rules, at);
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

    /**
     * AHEAD, spelled AND as well: match the rule after it, then put the
     * position back.
     *
     * <p>How a rule asks what comes next without taking it, so
     * {@code [ahead #"a" skip]} matches an "a" twice over: once to look
     * and once to consume.
     */
    private int lookahead(List<Value> rules, int at) {
        following(rules, at, "ahead");
        int before = position;
        boolean matched = matchOne(rules, at + 1) != NO_MATCH;
        position = before;
        return matched ? 1 + ruleSpan(rules, at + 1) : NO_MATCH;
    }

    /**
     * CHANGE: match the rule after it and put a value where the match was.
     *
     * <p>REMOVE and an insertion in one step. The replacement is one value
     * however many items the rule matched, and a paren replacement is
     * evaluated at the moment the change happens rather than when the rule
     * was written. That is what makes it useful with SET, where the paren
     * reads a word the very match being replaced has just set.
     */
    private int changeMatched(List<Value> rules, int at) {
        Value rule = following(rules, at, "change");
        // ONLY belongs before the replacement and not before the rule.
        // `change only ['a 'b] [z p]` reads as a rule called only, which
        // is no rule at all, and a real R3 says so.
        if (rule instanceof WordValue misplaced && misplaced.datatype() == Datatype.WORD
                && misplaced.canonical().equals("only")) {
            throw Raised.of(EvaluationFailure.PARSE_RULE,
                    "only says how to put the replacement in, so it goes "
                            + "before the replacement and not before the rule");
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
        // ONLY here means put the block in whole. Without it a block
        // replacement is spread, so `change some word! [z p]` leaves two
        // words where the match was and not one block holding them.
        int lastRuleAt = replacementAt;
        boolean wholeBlock = false;
        if (rules.get(replacementAt) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("only")
                && replacementAt + 1 < rules.size()) {
            wholeBlock = true;
            lastRuleAt = replacementAt + 1;
        }
        Value replacement = valueToPutIn(rules.get(lastRuleAt));
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

    /**
     * The value INSERT or CHANGE is about to put in, looked up first.
     *
     * <p>{@code Get_Parse_Value} in the C: a word that is not a parse keyword
     * is fetched, a path is evaluated, and everything else is taken as it
     * stands. A paren is evaluated too, at the moment the modification
     * happens rather than when the rule was written.
     *
     * <p>The word case is the one that matters and it was missing. Taking the
     * word as written leaves a rule that repeats one symbol rather than one
     * that builds anything: `v: 7  parse [a b] [some [word! insert v]]` left
     * [a v b v] instead of [a 7 b 7]. Rebol's own ENUM is built on this, and
     * with the word taken as written every name in an enumeration came out
     * holding the final count instead of its own.
     *
     * <p>A lit-word loses its tick, which the C does by hand after the
     * modification. It is the only way to put a plain word in: an unquoted one
     * would be fetched by the rule above and a quoted one would stay quoted.
     */
    private Value valueToPutIn(Value written) {
        if (written instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
        }
        if (written instanceof WordValue named) {
            return switch (named.datatype()) {
                case LIT_WORD -> named.as(Datatype.WORD);
                // No keyword test here, and the C does not have one either.
                // It deals with ONLY before this point and treats any other
                // parse command in the value position as a bad rule, so by
                // the time the value is fetched it is never a keyword.
                case WORD -> evaluator.evaluateOrRaise(
                        BlockValue.block(List.of(named)), context);
                default -> named;
            };
        }
        if (written instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            return evaluator.evaluateOrRaise(
                    BlockValue.block(List.of(path)), context);
        }
        return written;
    }

    /**
     * INSERT: put a value in at the position, consuming nothing.
     *
     * <p>The position ends up after what was inserted, which is what stops
     * an INSERT inside a repeat from running forever.
     */
    private int insertValue(List<Value> rules, int at) {
        following(rules, at, "insert");
        // ONLY puts a block in whole. Without it a block is spread, which is
        // the same rule CHANGE follows and for the same reason: the C passes
        // AN_ONLY to Modify_Block only when the word is there, and
        // Modify_Block spreads a block otherwise.
        //
        // JEBOL inserted a block whole either way, so `v: [1 2]` gave
        // [[1 2] a] where Rebol gives [1 2 a].
        int valueAt = at + 1;
        boolean wholeBlock = false;
        if (rules.get(valueAt) instanceof WordValue modifier
                && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("only")
                && valueAt + 1 < rules.size()) {
            wholeBlock = true;
            valueAt++;
        }
        Value added = valueToPutIn(rules.get(valueAt));
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

    /**
     * REMOVE: match the rule after it and take what matched out of the
     * input, so the series is shorter afterwards.
     */
    private int removeMatched(List<Value> rules, int at) {
        following(rules, at, "remove");
        int before = position;
        if (matchOne(rules, at + 1) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        for (int taken = position; taken > before; taken--) {
            input.remove(taken - 1);
            if (source != null) {
                source.storage().removeAt(source.index() + taken - 1);
            }
        }
        position = before;
        return 1 + ruleSpan(rules, at + 1);
    }

    /**
     * IF: a guard. The rule matched, and this decides whether that counts.
     *
     * <p>Without it a rule cannot depend on what it has just captured, so
     * {@code [set v integer! if (even? v)]} would have no way to reject the
     * odd ones.
     */
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

    /**
     * Refuses a COLLECT INTO target that cannot hold what this parse yields.
     *
     * <p>Parsing a block yields values, so the target has to be somewhere
     * values go: a block, a paren or a hash. A string cannot hold them and
     * neither can a number, and left unchecked the delivery quietly did
     * nothing -- so a rule collecting into the wrong kind of thing looked
     * like it worked and the target was simply never touched.
     */
    private void refuseWrongIntoTarget(Value target) {
        Datatype kind = target.datatype();
        boolean holdsWhatWeParse = kind == Datatype.BINARY && parsing == Datatype.BINARY;
        if (!holdsWhatWeParse
                && kind != Datatype.BLOCK && kind != Datatype.PAREN && kind != Datatype.HASH) {
            throw Raised.of(EvaluationFailure.PARSE_INTO_TYPE,
                    "a " + kind.literalSpelling() + " cannot hold what this parse yields");
        }
    }

    /**
     * Runs the rule that follows, gathering whatever KEEP matches.
     *
     * <p>The block it gathers is kept in the enclosing collection when
     * there is one, and becomes the parse's answer when there is not.
     */
    private int collect(List<Value> rules, int at) {
        Value next = following(rules, at, "collect");

        // COLLECT SET word [...] puts the collection in the word instead
        // of answering it, so the parse goes back to answering whether it
        // matched -- full-consumption rule and all, which a bare COLLECT
        // suspends.
        WordValue into = null;
        WordValue insertInto = null;
        WordValue appendTo = null;
        int ruleAt = at + 1;
        if (next instanceof WordValue keyword && keyword.datatype() == Datatype.WORD
                && at + 2 < rules.size()
                && rules.get(at + 2) instanceof WordValue name) {
            if (keyword.canonical().equals("set")) {
                into = name;
                ruleAt = at + 3;
            } else if (keyword.canonical().equals("into")) {
                // INTO an existing series rather than a fresh one, at its
                // position, so what was already there is pushed along.
                insertInto = name;
                ruleAt = at + 3;
            } else if (keyword.canonical().equals("after")) {
                // AFTER is the counterpart: past what is already there
                // rather than in front of it.
                appendTo = name;
                ruleAt = at + 3;
            }
        }
        if (ruleAt >= rules.size()) {
            throw Raised.of(EvaluationFailure.PARSE_END,
                    "collect has no rule after it to apply to");
        }

        collecting.push(new ArrayList<>());
        int consumed = matchOne(rules, ruleAt);
        List<Value> mine = collecting.pop();

        // The collection is handed over whether or not the rule matched.
        // Matching backtracks and collecting does not, so a COLLECT whose
        // rule fails still leaves its block behind:
        // `parse [] [collect [collect [keep 2 skip]]]` answers [[]], and
        // a SOME that attempts one round too many leaves a trailing empty
        // block for the round that failed. Discarding it here is what made
        // sixty-five of Rebol's own COLLECT assertions disagree.
        if (appendTo != null) {
            refuseWrongIntoTarget(valueOf(appendTo));
            if (valueOf(appendTo) instanceof BlockValue existing) {
                mine.forEach(gathered -> existing.storage().insertAt(
                        existing.storageLength() + 1, gathered));
            }
        } else if (insertInto != null) {
            Value target = valueOf(insertInto);
            refuseWrongIntoTarget(target);
            if (target instanceof BlockValue existing) {
                for (int added = mine.size(); added > 0; added--) {
                    existing.storage().insertAt(existing.index(), mine.get(added - 1));
                }
            }
        } else if (into != null) {
            assign(into, BlockValue.block(mine));
        } else if (!collecting.isEmpty()) {
            collecting.peek().add(BlockValue.block(mine));
        } else if (gathered == null) {
            // The first COLLECT establishes what the parse answers.
            gathered = mine;
        } else {
            // Any after it add their block to that rather than replacing
            // it or starting a list of their own, so two collects answer
            // [1 [2]] and not [[1] [2]].
            gathered.add(BlockValue.block(mine));
        }
        return consumed == NO_MATCH
                ? NO_MATCH
                : (ruleAt - at) + ruleSpan(rules, ruleAt);
    }

    /**
     * Adds what the rule that follows matched to the collection.
     *
     * <p>Four shapes. A paren keeps its value and consumes nothing, so a
     * collection can hold something computed rather than only something
     * matched. PICK gathers the matched items one by one instead of
     * together. COPY keeps the block it captured, however many items that
     * was. Anything else keeps one item as itself and several as a block.
     */
    private int keep(List<Value> rules, int at) {
        Value kept = following(rules, at, "keep");
        // KEEP needs somewhere to keep into. Without a COLLECT around it
        // the value was quietly dropped and the rule went on matching, so
        // a rule with the COLLECT left off looked like it worked.
        if (collecting.isEmpty()) {
            throw Raised.of(EvaluationFailure.PARSE_NO_COLLECT,
                    "keep has no collect around it");
        }

        if (kept instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            Value produced = evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
            if (!collecting.isEmpty()) {
                collecting.peek().add(produced);
            }
            return 2;
        }
        if (kept instanceof WordValue modifier && modifier.datatype() == Datatype.WORD
                && modifier.canonical().equals("pick")) {
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
        if (!collecting.isEmpty()) {
            // One item is kept as itself and several are kept together as a
            // block, so a caller can tell one run from two. The count
            // decides the shape, not the kind of rule: an ANY that matched
            // once keeps the value.
            List<Value> matched = input.subList(before, position);
            if (matched.size() == 1) {
                collecting.peek().add(matched.getFirst());
            } else if (!matched.isEmpty()) {
                collecting.peek().add(BlockValue.block(matched));
            }
        }
        return 1 + ruleSpan(rules, at + 1);
    }

    /** KEEP PICK: the matched items go in separately, not as a block. */
    private int keepIndividually(List<Value> rules, int at) {
        int before = position;
        if (matchOne(rules, at) == NO_MATCH) {
            position = before;
            return NO_MATCH;
        }
        if (!collecting.isEmpty()) {
            collecting.peek().addAll(input.subList(before, position));
        }
        return 2 + ruleSpan(rules, at);
    }

    /**
     * KEEP COPY: the block COPY captured is what goes in.
     *
     * <p>{@code at} is the COPY, so the word it binds is next and the rule
     * it captures is the one after that. The word is set as well, because
     * COPY still does its own job inside a KEEP.
     */
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
        if (!collecting.isEmpty()) {
            collecting.peek().add(captured);
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

    /**
     * ANY and SOME: match the following rule until it stops matching.
     *
     * <p>The rule that follows can itself be a counted one, which is why
     * this goes back through {@link #matchOne} rather than matching a
     * single item. {@code [any 1]} is ANY applied to a count of one with
     * nothing to count, and raises rather than matching a block of ones.
     */
    private int repeat(List<Value> rules, int at, int leastNeeded) {
        following(rules, at, "any or some");
        int matched = 0;
        while (true) {
            int before = position;
            int wasLong = input.size();
            // A rule that consumes nothing would repeat for ever, so the
            // loop stops when nothing moved. REMOVE moves nothing but
            // shortens the input, which is progress of a different kind and
            // has to count as progress or `some [remove x | skip]` removes
            // only the first match.
            if (matchOne(rules, at + 1) == NO_MATCH
                    || (position == before && input.size() == wasLong)) {
                position = before;
                break;
            }
            matched++;
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

    /**
     * TO and THRU: move forward until the rule matches. TO leaves the
     * position before what it found and THRU leaves it after, which is the
     * whole difference between them.
     */
    private int seek(List<Value> rules, int at, boolean past) {
        Value wanted = following(rules, at, "to or thru");

        if (wanted instanceof WordValue word && word.canonical().equals("end")) {
            position = input.size();
            return 2;
        }
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

    /** INTO: match a rule against the contents of the block at this position. */
    private int into(List<Value> rules, int at) {
        Value inner = following(rules, at, "into");
        if (atEnd() || !(current() instanceof BlockValue nested)
                || !(inner instanceof BlockValue innerRule)) {
            return NO_MATCH;
        }
        if (!matches(evaluator, context, nested.remaining(), innerRule)) {
            return NO_MATCH;
        }
        position++;
        return 2;
    }

    /**
     * SET and COPY: match the following rule and keep what it matched. SET
     * keeps the single value; COPY keeps everything the rule consumed.
     */
    private int capture(List<Value> rules, int at, boolean everything) {
        if (at + 2 >= rules.size() + 1) {
            throw Raised.of(EvaluationFailure.CANNOT_USE, "set or copy needs a word and a rule");
        }
        Value target = following(rules, at, "set or copy");
        if (!(target instanceof WordValue word)) {
            throw Raised.of(EvaluationFailure.CANNOT_USE, "set or copy needs a word");
        }
        if (at + 2 >= rules.size()) {
            throw Raised.of(EvaluationFailure.CANNOT_USE, "set or copy needs a rule to apply");
        }

        int startedAt = position;
        int consumed = matchOne(rules, at + 2);
        if (consumed == NO_MATCH) {
            return NO_MATCH;
        }
        List<Value> taken = new ArrayList<>(input.subList(startedAt, position));
        assign(word, everything ? BlockValue.block(taken) : firstOf(taken));
        return 2 + consumed;
    }

    private Value firstOf(List<Value> taken) {
        return taken.isEmpty() ? org.jebol.domain.value.NoneValue.none() : taken.get(0);
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

    /** A word in a rule names another rule, which is how a grammar is built. */
    private boolean matchNamedRule(WordValue word) {
        Context target = word.isBound() ? word.binding() : context;
        if (!target.knows(word.canonical())) {
            return false;
        }
        Value named = target.slotFor(word.canonical()).value();
        return named instanceof BlockValue rule
                ? matchSequence(rule.remaining())
                : matchValue(named);
    }

    /** Matches one value: a literal, a datatype, a nested rule, or an action. */
    private boolean matchValue(Value rule) {
        return switch (rule) {
            case BlockValue nested when nested.datatype() == Datatype.PAREN -> {
                // A paren runs while matching, and always "matches", which is
                // how PARSE does something as it goes rather than only after.
                evaluator.evaluateOrRaise(nested.as(Datatype.BLOCK), context);
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
            // `if (IS_WORD(blk) && !Compare_Word(blk, item, ...))` in the
            // C, under a comment that says "patch to search for word, not
            // lit". A lit-word in a rule matches a plain word in the
            // input, thus `parse [a] ['a]` holds.
            case WordValue word when word.datatype() == Datatype.LIT_WORD ->
                    matchesLiteral(word.as(Datatype.WORD));
            // A plain word inside SOME, TO or a repeat count names a rule,
            // exactly as it does at the top level. Without this a grammar
            // could not be built out of named parts, which is most of what
            // PARSE is for. The two keywords that stand alone have to be
            // recognised here too, so that `[3 skip]` counts skips rather
            // than looking for a rule named skip.
            case WordValue word when word.datatype() == Datatype.WORD ->
                    switch (word.canonical()) {
                        case "end" -> atEnd();
                        case "skip" -> advanceOne();
                        default -> matchNamedRule(word);
                    };
            // `case REB_LIT_PATH: if (IS_PATH(blk) && !Cmp_Block(...))`
            // in the C. A lit-path matches a path, the same way a
            // lit-word matches a word. Left out, it fell to the block
            // case and was read as a sub-rule.
            case BlockValue path when path.datatype() == Datatype.LIT_PATH ->
                    matchesLiteral(path.as(Datatype.PATH));
            default -> matchesLiteral(rule);
        };
    }

    /**
     * QUOTE: the next rule item is a value to match, not a rule.
     *
     * <p>`case SYM_QUOTE` in the C. A paren there is evaluated first and
     * its answer is what gets matched, thus a rule can look for a value
     * it works out as it goes.
     */
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

    /**
     * Whether two paths name the same thing, item for item.
     *
     * <p>Cmp_Block in the C, which walks both and compares each pair.
     * Case folds for each item unless the parse was told to mind it.
     */
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
        // A parse folds case unless CASE has been reached, and that holds
        // for a block parse as much as a string one: `parse ["A"]
        // [case ["A"]]` minds it and `[no-case ["a"]]` does not.
        // Cmp_Block in the C compares a path item for item, thus each
        // word inside it folds case the same way a bare word does.
        // Comparing the two paths with equals() minded case whatever the
        // parse had been told.
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

    /**
     * CASE and NO-CASE: change how the rules after them compare.
     *
     * <p>A mode rather than a refinement, which is what lets one rule
     * mind case in one place and not in another. Consuming nothing, so
     * they can sit anywhere a rule can.
     */
    private int setCaseMode(boolean minding) {
        mindingCase = minding;
        return 1;
    }

    private static boolean looselyEqual(Value left, Value right) {
        if (left instanceof StringValue leftText && right instanceof StringValue rightText) {
            // The datatype counts here, because matching a rule against an
            // item of a block follows Cmp_Value rather than Compare_Values,
            // and Cmp_Value exempts only the numbers and the words from the
            // datatype check. So a %a rule does not match a "a" item.
            return leftText.datatype() == rightText.datatype()
                    && leftText.equalsIgnoringCase(rightText);
        }
        if (left instanceof WordValue leftWord && right instanceof WordValue rightWord) {
            return leftWord.namesSameAs(rightWord);
        }
        return left.equals(right);
    }

    /** Splits a rule on {@code |}, which separates alternatives. */
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

    /** Signals that RETURN ended the parse early with a value to answer. */
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
