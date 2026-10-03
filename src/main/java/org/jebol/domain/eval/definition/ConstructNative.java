package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConstructNative extends DefaultNative {

    private static final int WHERE_THE_PROTOTYPE_ARRIVES = 1;

    @Override
    public String name() {
        return "construct";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("body",
                        Set.of(Datatype.BLOCK, Datatype.STRING, Datatype.BINARY)),
                Parameter.belongingTo("with", "object", Set.of(Datatype.OBJECT)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("only", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Context built = Context.childOf(evaluator.systemContext());
            if (refinements.contains("with")
                    && arguments.size() > WHERE_THE_PROTOTYPE_ARRIVES
                    && arguments.get(WHERE_THE_PROTOTYPE_ARRIVES)
                            instanceof ObjectValue(Context prototype)) {
                prototype.fieldsExcludingSelf().forEach(built::set);
            }
            constructInto(built, itemsOf(arguments.getFirst()), refinements.contains("only"));
            return new ObjectValue(built);
        };
    }

    private static List<Value> itemsOf(Value body) {
        return switch (body) {
            case BlockValue block -> block.remaining();
            case StringValue text -> headerFieldsIn(text.text());
            case BinaryValue bytes -> headerFieldsIn(
                    new String(bytes.octetsFromHere(), StandardCharsets.UTF_8));
            default -> List.of();
        };
    }

    private static void constructInto(Context built, List<Value> items, boolean asWritten) {
        List<WordValue> waiting = new ArrayList<>();
        for (Value item : items) {
            if (item instanceof WordValue name && name.datatype() == Datatype.SET_WORD) {
                waiting.add(name);
                continue;
            }
            Value held = asWritten ? item : namedConstant(item);
            if (!asWritten && held instanceof UnsetValue) {
                held = NoneValue.none();
            }
            for (WordValue name : waiting) {
                built.set(name.spelling(), held);
            }
            waiting.clear();
        }
        for (WordValue name : waiting) {
            if (!asWritten) {
                built.set(name.spelling(), NoneValue.none());
            } else if (!built.knows(name.canonical())) {
                built.define(name.spelling());
            }
        }
    }

    private static Value namedConstant(Value value) {
        if (!(value instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return value;
        }
        return switch (word.canonical()) {
            case "none" -> NoneValue.none();
            case "true", "on", "yes" -> LogicValue.of(true);
            case "false", "off", "no" -> LogicValue.of(false);
            default -> value;
        };
    }

    private static List<Value> headerFieldsIn(String header) {
        List<Value> fields = new ArrayList<>();
        String[] lines = header.split("\n", -1);
        for (int at = 0; at < lines.length; at++) {
            String line = withoutACarriageReturn(lines[at]);
            int colon = colonAfterAName(line);
            if (colon < 0) {
                continue;
            }
            StringBuilder value = new StringBuilder(line.substring(colon + 1).stripLeading());
            while (at + 1 < lines.length && startsWithSpaceOrTab(lines[at + 1])) {
                at++;
                value.append(' ').append(withoutACarriageReturn(lines[at]).stripLeading());
            }
            fields.add(WordValue.of(line.substring(0, colon).strip(), Datatype.SET_WORD));
            fields.add(StringValue.of(value.toString()));
        }
        return fields;
    }

    private static String withoutACarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static int colonAfterAName(String line) {
        String name = line.stripLeading();
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            return -1;
        }
        int at = 0;
        while (at < name.length() && isPartOfAName(name.charAt(at))) {
            at++;
        }
        return at < name.length() && name.charAt(at) == ':'
                ? at + (line.length() - name.length())
                : -1;
    }

    private static boolean isPartOfAName(char letter) {
        return Character.isLetterOrDigit(letter)
                || letter == '.' || letter == '-' || letter == '_';
    }

    private static boolean startsWithSpaceOrTab(String line) {
        return !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
    }
}
