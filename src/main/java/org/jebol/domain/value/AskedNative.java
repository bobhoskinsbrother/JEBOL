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
    public Optional<BlockValue> ownSpec() {
        return asked.ownSpec();
    }

    @Override
    public NativeValue askedFor(Set<String> refinements) {
        return asked.askedFor(refinements);
    }

    @Override
    public NativeValue derivedWith(BlockValue spec, List<Parameter> declared) {
        return asked.derivedWith(spec, declared).askedFor(askedRefinements);
    }
}
