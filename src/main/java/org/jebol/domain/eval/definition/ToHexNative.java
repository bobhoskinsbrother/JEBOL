package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

public class ToHexNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "to-hex";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("value", Set.of(Datatype.INTEGER, Datatype.CHAR, Datatype.TUPLE)),
                Parameter.belongingTo("size", "len", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("size");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            HexWidth width = new HexWidth(argumentOf("size", 0, arguments, refinements)
                    .map(asked -> OptionalLong.of(((IntegerValue) asked).magnitude()))
                    .orElseGet(OptionalLong::empty));
            return WordValue.of(arguments.getFirst().writtenInHex(width), Datatype.ISSUE);
        };
    }
}
