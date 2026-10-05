package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class PokeAction extends DefaultNative {

    @Override
    public String name() {
        return "poke";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("series", Typeset.SERIES.membersAnd(
                        Datatype.PORT, Datatype.MAP, Datatype.GOB, Datatype.BITSET)),
                Parameter.required("index"),
                Parameter.required("value", Typeset.ANY_TYPE.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer("poke", arguments, Set.of())
                .orElseGet(() -> Actions.of(arguments.getFirst())
                        .orElseThrow(() -> Raised.cannotUse(arguments.getFirst(), name()))
                        .poked(arguments.get(1), arguments.get(2)));
    }
}
