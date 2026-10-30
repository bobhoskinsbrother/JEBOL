package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract sealed class AnyPathValue extends AnyBlockValue
        permits PathValue, SetPathValue, GetPathValue, LitPathValue {

    AnyPathValue(BlockStorage storage, int index) {
        super(storage, index);
    }

    public abstract static class AnyPathDatatype extends AnyBlockDatatype {

        AnyPathDatatype(String spelling) {
            super(spelling);
        }

        @Override
        public abstract AnyPathValue wrap(BlockStorage storage, int index);

        @Override
        public AnyPathValue wrap(List<Value> segments) {
            return wrap(new BlockStorage(segments), 1);
        }
    }

    @Override
    boolean moldsInBrackets() {
        return false;
    }

    @Override
    String moldedWhenAlreadyInsideItself() {
        return "...";
    }

    String markBefore(boolean forReading) {
        return "";
    }

    String markAfter(boolean forReading) {
        return "";
    }

    @Override
    public String runTogether() {
        return remaining().stream()
                .map(Value::runTogether)
                .collect(Collectors.joining("/"));
    }

    @Override
    public Optional<ContextSlot> fieldPathNames() {
        List<Value> segments = remaining();
        if (segments.size() < 2
                || !(segments.getFirst() instanceof AnyWordValue start)
                || !start.isBound() || !start.binding().contains(start.canonical())) {
            return Optional.empty();
        }
        Value reached = start.binding().findSlotFor(start.canonical()).value();
        for (Value between : segments.subList(1, segments.size() - 1)) {
            Optional<ContextSlot> field = findField(reached, between);
            if (field.isEmpty()) {
                return Optional.empty();
            }
            reached = field.get().value();
        }
        return findField(reached, segments.getLast());
    }

    private static Optional<ContextSlot> findField(Value holder, Value name) {
        return holder instanceof PathTargetWithFields fielded
                && name instanceof AnyWordValue word
                && fielded.context().holds(word.canonical())
                ? Optional.of(fielded.context().ownSlotFor(word.canonical()))
                : Optional.empty();
    }
}
