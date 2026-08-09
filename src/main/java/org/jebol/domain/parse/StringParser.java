package org.jebol.domain.parse;

import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * PARSE over a string, which matches substrings rather than characters.
 *
 * <p>{@code parse "a b c" ["a" "b" "c"]} matches, and it would not if a rule
 * matched one character at a time. Whitespace between rules is skipped
 * unless the caller asked otherwise, which is what makes the string form
 * useful for reading text written for people.
 *
 * <p>Kept apart from {@link Parser} rather than generalised into it. The two
 * share their keywords and nothing else: one walks values and the other walks
 * characters, and a single class doing both would spend most of its length
 * asking which it was.
 */
public final class StringParser {

    private final Evaluator evaluator;
    private final Context context;
    private final String text;
    private final boolean skipWhitespace;

    private int position;

    private StringParser(
            Evaluator evaluator, Context context, String text, boolean skipWhitespace) {
        this.evaluator = evaluator;
        this.context = context;
        this.text = text;
        this.skipWhitespace = skipWhitespace;
    }

    /** Whether the whole string matches the whole rule. */
    public static boolean matches(
            Evaluator evaluator, Context context, String text, BlockValue rule) {

        StringParser parser = new StringParser(evaluator, context, text, true);
        boolean matched = parser.matchSequence(rule.remaining());
        parser.skipAnyWhitespace();
        return matched && parser.atEnd();
    }

    private boolean atEnd() {
        return position >= text.length();
    }

    private void skipAnyWhitespace() {
        if (!skipWhitespace) {
            return;
        }
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean matchSequence(List<Value> rules) {
        int startedAt = position;
        for (List<Value> alternative : splitOnAlternatives(rules)) {
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
            if (consumed < 0) {
                return false;
            }
            at += consumed;
        }
        return true;
    }

    private int matchOne(List<Value> rules, int at) {
        Value rule = rules.get(at);
        if (rule instanceof WordValue word && word.datatype() == Datatype.WORD) {
            return switch (word.canonical()) {
                case "end" -> endOfInput() ? 1 : -1;
                case "skip" -> advanceOne() ? 1 : -1;
                case "to" -> seek(rules, at, false);
                case "thru" -> seek(rules, at, true);
                case "any" -> repeat(rules, at, 0);
                case "some" -> repeat(rules, at, 1);
                case "opt" -> optional(rules, at);
                default -> matchValue(rule) ? 1 : -1;
            };
        }
        return matchValue(rule) ? 1 : -1;
    }

    private boolean endOfInput() {
        skipAnyWhitespace();
        return atEnd();
    }

    private boolean advanceOne() {
        if (atEnd()) {
            return false;
        }
        position++;
        return true;
    }

    private int repeat(List<Value> rules, int at, int leastNeeded) {
        Value repeated = rules.get(at + 1);
        int matched = 0;
        while (true) {
            int before = position;
            if (!matchValue(repeated) || position == before) {
                position = before;
                break;
            }
            matched++;
        }
        return matched >= leastNeeded ? 2 : -1;
    }

    private int optional(List<Value> rules, int at) {
        int before = position;
        if (!matchValue(rules.get(at + 1))) {
            position = before;
        }
        return 2;
    }

    private int seek(List<Value> rules, int at, boolean past) {
        Value wanted = rules.get(at + 1);
        if (wanted instanceof WordValue word && word.canonical().equals("end")) {
            position = text.length();
            return 2;
        }
        String needle = textOf(wanted);
        int found = text.indexOf(needle, position);
        if (found < 0) {
            return -1;
        }
        position = past ? found + needle.length() : found;
        return 2;
    }

    private boolean matchValue(Value rule) {
        if (rule instanceof BlockValue nested && nested.datatype() == Datatype.PAREN) {
            evaluator.evaluateOrRaise(nested.as(Datatype.BLOCK), context);
            return true;
        }
        if (rule instanceof BlockValue nested) {
            return matchSequence(nested.remaining());
        }
        skipAnyWhitespace();
        String wanted = textOf(rule);
        if (wanted.isEmpty() || !text.regionMatches(true, position, wanted, 0, wanted.length())) {
            return false;
        }
        position += wanted.length();
        return true;
    }

    private static String textOf(Value value) {
        return value instanceof StringValue text ? text.text() : Molder.form(value);
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
