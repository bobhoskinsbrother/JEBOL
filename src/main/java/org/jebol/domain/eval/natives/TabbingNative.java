package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public abstract class TabbingNative extends DefaultNative {

    private static final int SPACES_TAB_STANDS_FOR = 4;

    static final byte TAB = '\t';

    static final byte SPACE = ' ';

    static final byte LINE_FEED = '\n';

    abstract byte[] tab(byte[] bytes, int stop);

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
            int stop = refinementArgument("size", 0, arguments, refinements)
                    .map(this::countSpacesTabStandsFor)
                    .orElse(SPACES_TAB_STANDS_FOR);
            return switch (arguments.getFirst()) {
                case BinaryValue octets -> BinaryValue.ofBytes(tab(octets.octetsFromHere(), stop));
                case AnyStringValue text -> text.wrap(new String(
                        tab(text.text().getBytes(StandardCharsets.UTF_8), stop), StandardCharsets.UTF_8));
                case Value anythingElse -> refuseDatatype(anythingElse);
            };
        };
    }

    private int countSpacesTabStandsFor(Value asked) {
        long spaces = ((IntegerValue) asked).magnitude();
        if (spaces <= 0 || spaces > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked);
        }
        return (int) spaces;
    }
}
