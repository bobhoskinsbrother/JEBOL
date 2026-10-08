package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract sealed class AnyPathValue extends AnyBlockValue
        permits PathValue, SetPathValue, GetPathValue, LitPathValue {

    AnyPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public static AnyPathValue path(List<Value> segments, Datatype pathDatatype) {
        if (!pathDatatype.isAnyPath()) {
            throw new IllegalArgumentException(
                    pathDatatype.literalSpelling() + " is not an any-path! datatype");
        }
        return (AnyPathValue) ofTheDatatype(new BlockStorage(segments), 1, pathDatatype);
    }

    @Override
    public String runTogether() {
        return remaining().stream()
                .map(Value::runTogether)
                .collect(Collectors.joining("/"));
    }

    @Override
    public Optional<ContextSlot> fieldThePathNames() {
        List<Value> segments = remaining();
        if (segments.size() < 2
                || !(segments.getFirst() instanceof AnyWordValue start)
                || !start.isBound() || !start.binding().knows(start.canonical())) {
            return Optional.empty();
        }
        Value reached = start.binding().slotFor(start.canonical()).value();
        for (Value between : segments.subList(1, segments.size() - 1)) {
            Optional<ContextSlot> field = theFieldNamed(reached, between);
            if (field.isEmpty()) {
                return Optional.empty();
            }
            reached = field.get().value();
        }
        return theFieldNamed(reached, segments.getLast());
    }

    private static Optional<ContextSlot> theFieldNamed(Value holder, Value name) {
        return holder instanceof ObjectValue(Context context)
                && name instanceof AnyWordValue word
                && context.holds(word.canonical())
                ? Optional.of(context.ownSlotFor(word.canonical()))
                : Optional.empty();
    }
}
