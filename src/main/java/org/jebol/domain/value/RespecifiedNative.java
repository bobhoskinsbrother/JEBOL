package org.jebol.domain.value;

import org.jebol.domain.eval.RefinedCallable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record RespecifiedNative(NativeValue original, BlockValue spec, List<Parameter> parameters)
        implements NativeValue {

    @Override
    public String nativeName() {
        return original.nativeName();
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
    public Optional<BlockValue> ownSpec() {
        return Optional.of(spec);
    }
}
