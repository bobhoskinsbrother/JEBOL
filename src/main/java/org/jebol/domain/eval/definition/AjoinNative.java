package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AjoinNative extends DefaultNative {

    private static final Set<Datatype> KEEPS_ITS_OWN_KIND =
            Set.of(Datatype.FILE, Datatype.URL, Datatype.EMAIL, Datatype.REF);

    @Override
    public String nativeName() {
        return "ajoin";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("with", "separator", Typeset.ANY_TYPE.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("all", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<Value> all = evaluator.evaluateEachOrRaise(
                    (BlockValue) arguments.getFirst(), context);
            String separator = argumentOf("with", 0, arguments, refinements)
                    .map(Molder::form)
                    .orElse("");
            return StringValue.of(all.stream()
                    .filter(piece -> refinements.contains("all") || holdsSomething(piece))
                    .map(Value::runTogether)
                    .collect(Collectors.joining(separator)), kindOf(all));
        };
    }

    private boolean holdsSomething(Value piece) {
        return !(piece instanceof NoneValue || piece instanceof UnsetValue);
    }

    private Datatype kindOf(List<Value> pieces) {
        return pieces.isEmpty() || !KEEPS_ITS_OWN_KIND.contains(pieces.getFirst().datatype())
                ? Datatype.STRING
                : pieces.getFirst().datatype();
    }
}
