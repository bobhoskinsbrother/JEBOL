package org.jebol.domain.value;

import org.jebol.domain.eval.RefinedCallable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public sealed interface NativeValue extends DeclaresParameters
        permits DefaultNative, ActionValue, AskedNative, RespecifiedNative {

    String nativeName();

    RefinedCallable behaviour();

    default Set<String> askedRefinements() {
        return Set.of();
    }

    default Optional<AnyBlockValue> ownSpec() {
        return Optional.empty();
    }

    default NativeValue askFor(Set<String> refinements) {
        return new AskedNative(this, Set.copyOf(refinements));
    }

    default NativeValue deriveWith(AnyBlockValue spec, List<Parameter> declared) {
        return new RespecifiedNative(this, spec, List.copyOf(declared));
    }

    default boolean declares(String refinement) {
        return refinementsDeclaredApart().contains(refinement);
    }

    default int countArity() {
        long base = parameters().stream()
                .filter(parameter -> parameter.owningRefinement().isEmpty())
                .filter(Parameter::consumesArgument)
                .count();
        long forRefinements = parameters().stream()
                .filter(parameter -> parameter.owningRefinement()
                        .map(askedRefinements()::contains).orElse(false))
                .filter(Parameter::consumesArgument)
                .count();
        return (int) (base + forRefinements);
    }

    Datatype TYPE = new AnyFunctionDatatype("native") {
    };

    @Override
    default Datatype datatype() {
        return TYPE;
    }
}
