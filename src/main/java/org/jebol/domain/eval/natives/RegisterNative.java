package org.jebol.domain.eval.natives;

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
        return List.of(Parameter.quoteLiterally("name"),
                Parameter.of("value", Set.of(StructValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (!(arguments.getFirst() instanceof AnyWordValue name)) {
                return refuseArgument(arguments.getFirst(), "name");
            }
            StructValue given = (StructValue) arguments.get(1);
            if (name instanceof SetWordValue) {
                name.findBoundSlot().setValue(given);
            }
            return fileUnder(WordValue.of(name.spelling()), given, findCatalogueIn(evaluator));
        };
    }

    private MapValue findCatalogueIn(Evaluator evaluator) {
        return (MapValue) evaluator.systemContext().readValueAt("system", "catalog", "structs");
    }

    private Value fileUnder(AnyWordValue filedAs, StructValue given, MapValue catalogue) {
        Value alreadyThere = catalogue.select(filedAs);
        if (alreadyThere instanceof AnyBlockValue held) {
            if (!held.equals(given.spec().declaration())) {
                throw Raised.of(EvaluationFailure.ALREADY_USED, filedAs.spelling());
            }
            return given;
        }
        catalogue.putWhetherProtectedOrNot(filedAs, given.spec().declaration(), false);
        return given;
    }
}
