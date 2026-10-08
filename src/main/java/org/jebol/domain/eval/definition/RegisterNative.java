package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class RegisterNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "register";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.hardQuoted("name"),
                Parameter.required("value", Set.of(Datatype.STRUCT)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (!(arguments.getFirst() instanceof WordValue name)) {
                return refuseTheArgument(arguments.getFirst(), "name");
            }
            StructValue given = (StructValue) arguments.get(1);
            if (name.datatype() == Datatype.SET_WORD) {
                name.boundSlot().setValue(given);
            }
            return filedUnder(WordValue.of(name.spelling()), given, theCatalogueIn(evaluator));
        };
    }

    private MapValue theCatalogueIn(Evaluator evaluator) {
        return (MapValue) evaluator.systemContext().valueAt("system", "catalog", "structs");
    }

    private Value filedUnder(WordValue filedAs, StructValue given, MapValue catalogue) {
        Value alreadyThere = catalogue.select(filedAs);
        if (alreadyThere instanceof BlockValue held) {
            if (!held.equals(given.spec().declaration())) {
                throw Raised.of(EvaluationFailure.ALREADY_USED, filedAs.spelling());
            }
            return given;
        }
        catalogue.putWhetherProtectedOrNot(filedAs, given.spec().declaration(), false);
        return given;
    }
}
