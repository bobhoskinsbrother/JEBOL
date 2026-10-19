package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public abstract class TabbingNative extends DefaultNative {

    private static final int SPACES_A_TAB_STANDS_FOR = 4;

    abstract String tab(String text, String aTabsWorthOfSpaces);

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("string", TypesetValue.ANY_STRING.membersAnd(BinaryValue.TYPE)),
                Parameter.belongTo("size", "number", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("size");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String aTabsWorthOfSpaces = writeATabsWorthOf(readTheArgumentOf("size", 0, arguments, refinements)
                    .map(this::countTheSpacesATabStandsFor)
                    .orElse(SPACES_A_TAB_STANDS_FOR));
            return switch (arguments.getFirst()) {
                case BinaryValue octets -> BinaryValue.ofBytes(tab(
                        new String(octets.octetsFromHere(), StandardCharsets.ISO_8859_1),
                        aTabsWorthOfSpaces).getBytes(StandardCharsets.ISO_8859_1));
                case AnyStringValue text -> text.wrap(tab(text.text(), aTabsWorthOfSpaces));
                case Value anythingElse -> refuseTheDatatype(anythingElse);
            };
        };
    }

    private String writeATabsWorthOf(int spaces) {
        return " ".repeat(spaces);
    }

    private int countTheSpacesATabStandsFor(Value asked) {
        long spaces = ((IntegerValue) asked).magnitude();
        if (spaces <= 0 || spaces > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked);
        }
        return (int) spaces;
    }
}
