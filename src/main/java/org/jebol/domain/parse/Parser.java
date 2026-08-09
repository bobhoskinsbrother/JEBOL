package org.jebol.domain.parse;

import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.IntegerValue;
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

    private int position;

    private Parser(Evaluator evaluator, Context context, List<Value> input) {
        this.evaluator = evaluator;
        this.context = context;
        this.input = input;
    }

    /** Whether the whole of the input matches the whole of the rule. */
    public static boolean matches(
            Evaluator evaluator, Context context, List<Value> input, BlockValue rule) {

        Parser parser = new Parser(evaluator, context, input);
        return parser.matchSequence(rule.remaining()) && parser.atEnd();
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
     * Matches the rule item at {@code at}, and says how many rule items it
     * used up. A keyword such as {@code some} uses two: itself and what it
     * applies to.
     */
    private int matchOne(List<Value> rules, int at) {
        Value rule = rules.get(at);

        if (rule instanceof WordValue word && word.datatype() == Datatype.WORD) {
            Integer consumed = matchKeyword(word.canonical(), rules, at);
            if (consumed != null) {
                return consumed;
            }
            return matchNamedRule(word) ? 1 : NO_MATCH;
        }
        return matchValue(rule) ? 1 : NO_MATCH;
    }

    /** The keywords, or null when the word is not one. */
    private Integer matchKeyword(String keyword, List<Value> rules, int at) {
        return switch (keyword) {
            case "end" -> atEnd() ? 1 : NO_MATCH;
            case "skip" -> advanceOne() ? 1 : NO_MATCH;
            case "any" -> repeat(rules, at, 0);
            case "some" -> repeat(rules, at, 1);
            case "opt" -> optional(rules, at);
            case "to" -> seek(rules, at, false);
            case "thru" -> seek(rules, at, true);
            case "into" -> into(rules, at);
            case "set" -> capture(rules, at, false);
            case "copy" -> capture(rules, at, true);
            default -> null;
        };
    }

    private boolean advanceOne() {
        if (atEnd()) {
            return false;
        }
        position++;
        return true;
    }

    /** ANY and SOME: match the following rule until it stops matching. */
    private int repeat(List<Value> rules, int at, int leastNeeded) {
        Value repeated = following(rules, at, "any or some");
        int matched = 0;
        while (true) {
            int before = position;
            if (!matchValue(repeated) || position == before) {
                break;
            }
            matched++;
        }
        return matched >= leastNeeded ? 2 : NO_MATCH;
    }

    private int optional(List<Value> rules, int at) {
        Value optional = following(rules, at, "opt");
        int before = position;
        if (!matchValue(optional)) {
            position = before;
        }
        return 2;
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
            throw Raised.of(EvaluationFailure.CANNOT_USE, keyword + " needs something after it");
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
            case DatatypeValue wanted -> matchesDatatype(wanted.represents());
            case TypesetValue wanted -> !atEnd()
                    && wanted.represents().members().contains(current().datatype())
                    && advanceOne();
            case WordValue word when word.datatype() == Datatype.LIT_WORD ->
                    matchesLiteral(word.as(Datatype.WORD));
            // A plain word inside SOME or TO names a rule, exactly as it does
            // at the top level. Without this a grammar could not be built out
            // of named parts, which is most of what PARSE is for.
            case WordValue word when word.datatype() == Datatype.WORD ->
                    word.canonical().equals("end") ? atEnd() : matchNamedRule(word);
            default -> matchesLiteral(rule);
        };
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
        if (!looselyEqual(current(), wanted)) {
            return false;
        }
        position++;
        return true;
    }

    private static boolean looselyEqual(Value left, Value right) {
        if (left instanceof StringValue leftText && right instanceof StringValue rightText) {
            return leftText.equalsIgnoringCase(rightText);
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
}
