package org.jebol.domain.value;

import org.jebol.domain.eval.RefinedCallable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record RespecifiedNative(NativeValue original, AnyBlockValue spec, List<Parameter> parameters)
        implements NativeValue {

    @Override
    public String nativeName() {
        return original.nativeName();
    }

    @Override
    public Datatype datatype() {
        return original.datatype();
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return original.refinementsDeclaredApart();
    }

    @Override
    public RefinedCallable behaviour() {
        return original.behaviour();
    }

    @Override
    public Optional<AnyBlockValue> ownSpec() {
        return Optional.of(spec);
    }
}
