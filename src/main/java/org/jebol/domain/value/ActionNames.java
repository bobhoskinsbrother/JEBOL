package org.jebol.domain.value;

import java.util.List;
import java.util.Set;

/**
 * The sixty built-ins Rebol declares as actions, in the order it declares them.
 *
 * <p>An action is the polymorphic kind of built-in: one name with an arm per
 * datatype, where a native has one body for every caller. Both are written in
 * the host language, so nothing about the implementation tells them apart, and
 * this is the only thing that does.
 *
 * <p>It is a fact about Rebol's declarations rather than about this code, and
 * there is no rule to derive it from: {@code src/boot/actions.reb} declares
 * these and {@code src/boot/natives.reb} declares the rest. Kept beside the
 * value model rather than beside the registry, because a value has to answer
 * {@code type?} without asking the evaluator anything.
 *
 * <p>Without it {@code type? :append} answered {@code native!}, which is what
 * 120 of the 582 words in Rebol's library disagreed with a real R3 about. The
 * count is larger than sixty because a second spelling bound to the same
 * function is an action too.
 */
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

    /** In declaration order, which is what the catalogue answers. */
    public static List<String> inDeclarationOrder() {
        return DECLARED;
    }

    /**
     * Whether a built-in of this name is an action.
     *
     * <p>Two sources, and only the first is a list. The second is a rule:
     * {@code types.reb} generates one type-test per datatype, and every one of
     * them is an action -- {@code block?}, {@code integer?}, and {@code action?}
     * itself. Deriving it beats listing it, because the datatypes are already
     * enumerated and a list would be a second place to keep them in step.
     *
     * <p>The line is exactly at the datatypes. A predicate over a *typeset* is
     * a borrowed REBOL function and answers {@code function!}, which is why
     * {@code series?} and {@code any-block?} must not come through here, and
     * they do not: neither names a datatype.
     */
    public static boolean holds(String nativeName) {
        return LOOKUP.contains(nativeName) || testsADatatype(nativeName);
    }

    private static boolean testsADatatype(String nativeName) {
        return nativeName.endsWith("?")
                && Datatype.named(nativeName.substring(0, nativeName.length() - 1))
                        .isPresent();
    }
}
