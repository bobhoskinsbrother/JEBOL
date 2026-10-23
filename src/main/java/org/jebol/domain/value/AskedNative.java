package org.jebol.domain.value;

import org.jebol.domain.eval.RefinedCallable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record AskedNative(NativeValue asked, Set<String> askedRefinements) implements NativeValue {

    @Override
    public String nativeName() {
        return asked.nativeName();
    }

    @Override
    public Datatype datatype() {
        return asked.datatype();
    }

    @Override
    public List<Parameter> parameters() {
        return asked.parameters();
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return asked.refinementsDeclaredApart();
    }

    @Override
    public RefinedCallable behaviour() {
        return asked.behaviour();
    }

    @Override
    public NativeValue withoutAskedRefinements() {
        return asked.withoutAskedRefinements();
    }

    @Override
    public Optional<AnyBlockValue> ownSpec() {
        return asked.ownSpec();
    }

    @Override
    public NativeValue askFor(Set<String> refinements) {
        return asked.askFor(refinements);
    }

    @Override
    public NativeValue deriveWith(AnyBlockValue spec, List<Parameter> declared) {
        return asked.deriveWith(spec, declared).askFor(askedRefinements);
    }
}
