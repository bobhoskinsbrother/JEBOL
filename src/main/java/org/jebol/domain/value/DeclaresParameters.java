package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public sealed interface DeclaresParameters extends AnyFunctionValue
        permits NativeValue, DefinedFunctionValue {

    List<Parameter> parameters();

    Set<String> refinementsDeclaredApart();

    default AnyBlockValue typesets() {
        List<Value> types = new ArrayList<>();
        Set<String> woven = new LinkedHashSet<>();
        for (Parameter parameter : parameters()) {
            parameter.owningRefinement().ifPresent(owner -> {
                if (refinementsDeclaredApart().contains(owner) && woven.add(owner)) {
                    types.add(TypesetValue.of(Parameter.A_REFINEMENTS_SLOT));
                }
            });
            types.add(parameter.typeset());
        }
        for (String leftover : refinementsDeclaredApart()) {
            if (!woven.contains(leftover)) {
                types.add(TypesetValue.of(Parameter.A_REFINEMENTS_SLOT));
            }
        }
        return BlockValue.block(types);
    }
}
