package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AjoinNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "ajoin";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("block", Set.of(BlockValue.TYPE)),
                Parameter.belongTo("with", "separator", TypesetValue.ANY_TYPE.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("all", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<Value> all = evaluator.evaluateEachOrRaise(
                    (AnyBlockValue) arguments.getFirst(), context);
            String separator = refinementArgument("with", 0, arguments, refinements)
                    .map(Molder::form)
                    .orElse("");
            return chooseKindFor(all).wrap(all.stream()
                    .filter(piece -> refinements.contains("all") || holdsSomething(piece))
                    .map(Value::runTogether)
                    .collect(Collectors.joining(separator)));
        };
    }

    private boolean holdsSomething(Value piece) {
        return !(piece instanceof NoneValue || piece instanceof UnsetValue);
    }

    private AnyStringValue.AnyStringDatatype chooseKindFor(List<Value> pieces) {
        if (pieces.isEmpty()) {
            return StringValue.TYPE;
        }
        return switch (pieces.getFirst()) {
            case FileValue file -> FileValue.TYPE;
            case UrlValue url -> UrlValue.TYPE;
            case EmailValue email -> EmailValue.TYPE;
            case RefValue ref -> RefValue.TYPE;
            default -> StringValue.TYPE;
        };
    }
}
