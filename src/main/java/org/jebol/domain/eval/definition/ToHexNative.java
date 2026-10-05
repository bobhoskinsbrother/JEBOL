package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.HexWidth;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

public class ToHexNative extends DefaultNative {

    @Override
    public String name() {
        return "to-hex";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("value", Set.of(Datatype.INTEGER, Datatype.CHAR, Datatype.TUPLE)),
                Parameter.belongingTo("size", "len", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
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
