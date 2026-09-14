package org.jebol.domain.value;

import java.util.List;
import java.util.Set;


public final class ActionNames {

    private ActionNames() {
    }

    private static final List<String> DECLARED = List.of(
            "add", "subtract", "multiply", "divide", "remainder", "power",
            "and~", "or~", "xor~", "negate", "complement", "absolute", "round",
            "random", "odd?", "even?", "head", "tail", "head?", "tail?",
            "past?", "next", "back", "skip", "at", "atz", "index?", "indexz?",
            "length?", "pick", "find", "select", "reflect", "make", "to",
            "copy", "take", "put", "insert", "append", "remove", "change",
            "poke", "clear", "trim", "swap", "reverse", "sort", "create",
            "delete", "open", "close", "read", "write", "open?", "query",
            "modify", "update", "rename", "flush");

    private static final Set<String> LOOKUP = Set.copyOf(DECLARED);

    public static List<String> inDeclarationOrder() {
        return DECLARED;
    }

    public static boolean holds(String nativeName) {
        return LOOKUP.contains(nativeName) || testsADatatype(nativeName);
    }

    public static boolean testsADatatype(String nativeName) {
        return nativeName.endsWith("?")
                && Datatype.named(nativeName.substring(0, nativeName.length() - 1))
                        .isPresent();
    }
}
