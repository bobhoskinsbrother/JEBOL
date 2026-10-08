package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record NativeValue(
        String nativeName,
        List<Parameter> parameters,
        Set<String> declaredRefinements,
        Set<String> askedRefinements,
        Optional<BlockValue> ownSpec) implements DeclaresParameters {

    public NativeValue(String nativeName, List<Parameter> parameters) {
        this(nativeName, parameters, Set.of(), Set.of(), Optional.empty());
    }

    public NativeValue(
            String nativeName, List<Parameter> parameters,
            Set<String> declaredRefinements, Set<String> askedRefinements) {

        this(nativeName, parameters, declaredRefinements, askedRefinements,
                Optional.empty());
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return declaredRefinements;
    }

    public NativeValue askedFor(Set<String> refinements) {
        return new NativeValue(nativeName, parameters, declaredRefinements,
                refinements, ownSpec);
    }

    public NativeValue derivedWith(BlockValue spec, List<Parameter> declared) {
        return new NativeValue(nativeName, declared, declaredRefinements,
                askedRefinements, Optional.of(spec));
    }

    public boolean declares(String refinement) {
        return declaredRefinements.contains(refinement);
    }

    public NativeValue {
        if (nativeName == null || nativeName.isEmpty()) {
            throw new IllegalArgumentException("a native needs a name");
        }
        parameters = List.copyOf(parameters);
        declaredRefinements = Set.copyOf(declaredRefinements);
        askedRefinements = Set.copyOf(askedRefinements);
    }

    public int arity() {
        long base = parameters.stream()
                .filter(parameter -> parameter.owningRefinement().isEmpty())
                .filter(Parameter::consumesAnArgument)
                .count();
        long forRefinements = parameters.stream()
                .filter(parameter -> parameter.owningRefinement()
                        .map(askedRefinements::contains).orElse(false))
                .filter(Parameter::consumesAnArgument)
                .count();
        return (int) (base + forRefinements);
    }

    @Override
    public Datatype datatype() {
        return ActionNames.holds(nativeName) ? Datatype.ACTION : Datatype.NATIVE;
    }

    @Override
    public String toString() {
        return "native " + nativeName + "/" + arity();
    }
}
