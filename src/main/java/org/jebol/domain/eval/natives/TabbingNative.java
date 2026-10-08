package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public abstract class TabbingNative extends DefaultNative {

    private static final int SPACES_A_TAB_STANDS_FOR = 4;

    abstract String tabbed(String text, String aTabsWorthOfSpaces);

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("string", Typeset.ANY_STRING.membersAnd(Datatype.BINARY)),
                Parameter.belongingTo("size", "number", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("size");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String aTabsWorthOfSpaces = aTabsWorthOf(argumentOf("size", 0, arguments, refinements)
                    .map(this::spacesATabStandsFor)
                    .orElse(SPACES_A_TAB_STANDS_FOR));
            return switch (arguments.getFirst()) {
                case BinaryValue octets -> BinaryValue.ofBytes(tabbed(
                        new String(octets.octetsFromHere(), StandardCharsets.ISO_8859_1),
                        aTabsWorthOfSpaces).getBytes(StandardCharsets.ISO_8859_1));
                case AnyStringValue text -> text.holding(tabbed(text.text(), aTabsWorthOfSpaces));
                case Value anythingElse -> refuseTheDatatype(anythingElse);
            };
        };
    }

    private String aTabsWorthOf(int spaces) {
        try {
            return " ".repeat(spaces);
        } catch (OutOfMemoryError nothingLeftToGive) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    private int spacesATabStandsFor(Value asked) {
        long spaces = ((IntegerValue) asked).magnitude();
        if (spaces <= 0 || spaces > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked);
        }
        return (int) spaces;
    }
}
