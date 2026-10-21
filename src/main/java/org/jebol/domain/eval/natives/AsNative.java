package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AsNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "as";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("type", listASeriesTypeAnd(Datatype.TYPE)),
                Parameter.of("spec", listASeriesTypeAnd()));
    }

    private Set<Datatype> listASeriesTypeAnd(Datatype... alsoTaken) {
        Set<Datatype> accepted = new LinkedHashSet<>(TypesetValue.ANY_BLOCK.members());
        accepted.addAll(TypesetValue.ANY_STRING.members());
        accepted.addAll(List.of(alsoTaken));
        return accepted;
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst().findDatatypeItStandsFor().as(arguments.get(1));
    }
}
