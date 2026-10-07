package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DeclaredArguments {

    private final Map<Optional<String>, List<Parameter>> byRefinement = new HashMap<>();

    DeclaredArguments(BlockValue spec) {
        for (Parameter declared : FunctionSpec.parametersIn(spec)) {
            if (declared.consumesAnArgument()) {
                byRefinement.computeIfAbsent(declared.owningRefinement(), none -> new ArrayList<>())
                        .add(declared);
            }
        }
    }

    List<Parameter> inPlaceOf(List<Parameter> parameters) {
        Map<Optional<String>, Integer> reached = new HashMap<>();
        List<Parameter> declaredInstead = new ArrayList<>(parameters.size());
        for (Parameter parameter : parameters) {
            if (!parameter.consumesAnArgument()) {
                declaredInstead.add(parameter);
                continue;
            }
            int at = reached.merge(parameter.owningRefinement(), 1, Integer::sum) - 1;
            List<Parameter> declared = byRefinement.getOrDefault(parameter.owningRefinement(), List.of());
            declaredInstead.add(at < declared.size() ? asDeclared(parameter, declared.get(at)) : parameter);
        }
        return declaredInstead;
    }

    private Parameter asDeclared(Parameter written, Parameter declared) {
        ParameterKind kind = written.kind() == ParameterKind.REFINEMENT_ARGUMENT
                ? ParameterKind.REFINEMENT_ARGUMENT
                : declared.kind();
        return new Parameter(declared.name(), kind, declared.acceptedTypes(), written.owningRefinement());
    }
}
