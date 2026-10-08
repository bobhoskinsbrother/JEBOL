package org.jebol.domain.eval.natives;

import org.jebol.domain.value.*;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class ProtectingNative extends DefaultNative {

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("target");
    }

    protected void protectionChanged(Value target, boolean protectedNow,
            Set<String> refinements) {

        if (!protectFieldNamedBy(target, protectedNow, refinements)) {
            protectNamed(target, protectedNow, refinements);
            setProtection(target, protectedNow, refinements.contains("deep"),
                    refinements.contains("words"));
        }
    }

    private boolean protectFieldNamedBy(
            Value target, boolean protectedNow, Set<String> refinements) {

        if (!(target instanceof BlockValue path) || !path.datatype().isAnyPath()) {
            return false;
        }
        if (refinements.contains("values")) {
            return false;
        }
        Optional<ContextSlot> named = path.fieldThePathNames();
        if (named.isEmpty()) {
            return true;
        }
        ContextSlot field = named.get();
        if (refinements.contains("hide")) {
            field.hide(protectedNow);
            return true;
        }
        if (protectedNow) {
            field.protectFromAssignment();
        } else {
            field.allowAssignment();
        }
        if (refinements.contains("deep") && carriesProtection(field.value())) {
            setProtection(field.value(), protectedNow, true, refinements.contains("words"));
        }
        return true;
    }

    private void protectNamed(Value target, boolean protectedNow, Set<String> refinements) {
        boolean values = refinements.contains("values");
        boolean words = refinements.contains("words");
        if (!(values || words)) {
            return;
        }
        List<Value> items = switch (target) {
            case BlockValue block when !block.datatype().isAnyPath() -> block.remaining();
            case WordValue only -> List.of(only);
            default -> List.of();
        };
        for (Value item : items) {
            slotNamedInAList(item).ifPresent(slot ->
                    protectOneNamed(slot, protectedNow, values, words, refinements));
        }
    }

    private void protectOneNamed(ContextSlot slot, boolean protectedNow, boolean values,
            boolean words, Set<String> refinements) {

        if (values && carriesProtection(slot.value())) {
            setProtection(slot.value(), protectedNow, refinements.contains("deep"), false);
        }
        if (words && refinements.contains("deep") && carriesProtection(slot.value())) {
            setProtection(slot.value(), protectedNow, true, true);
        }
        if (!words) {
            return;
        }
        if (!protectedNow) {
            slot.allowAssignment();
        } else if (refinements.contains("lock")) {
            slot.protectForGood();
        } else {
            slot.protectFromAssignment();
        }
    }

    private Optional<ContextSlot> slotNamedInAList(Value item) {
        if (item instanceof BlockValue path && path.datatype().isAnyPath()) {
            return path.fieldThePathNames();
        }
        if (item instanceof WordValue word && word.isBound()
                && word.binding().knows(word.canonical())) {
            return Optional.of(word.binding().slotFor(word.canonical()));
        }
        return Optional.empty();
    }

    private void setProtection(
            Value target, boolean protectedNow, boolean deeply, boolean onlyTheWords) {
        setProtection(target, protectedNow, deeply, onlyTheWords,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void setProtection(Value target, boolean protectedNow, boolean deeply,
            boolean onlyTheWords, Set<Object> reached) {

        switch (target) {
            case BlockValue block -> {
                if (!reached.add(block.storage())) {
                    return;
                }
                block.storage().protectFromChange(protectedNow);
                if (deeply) {
                    block.remaining().stream()
                            .filter(this::isReachedByADeepProtection)
                            .forEach(item -> setProtection(item, protectedNow, true, false, reached));
                }
            }
            case AnyStringValue text -> text.storage().protectFromChange(protectedNow);
            case BinaryValue bytes -> bytes.storage().protectFromChange(protectedNow);
            case MapValue map -> map.protectFromChange(protectedNow);
            case ObjectValue object -> setTheProtectionOf(
                    object, protectedNow, deeply, onlyTheWords, reached);
            case WordValue word -> {
                if (protectedNow) {
                    word.boundSlot().protectFromAssignment();
                } else {
                    word.boundSlot().allowAssignment();
                }
                if (deeply && carriesProtection(word.boundSlot().value())) {
                    setProtection(word.boundSlot().value(), protectedNow, true, onlyTheWords, reached);
                }
            }
            case BitsetValue members -> members.protectFromChange(protectedNow);
            case VectorValue vector -> vector.storage().protectFromChange(protectedNow);
            default -> throw Raised.cannotUse(target, "protect");
        }
    }

    private void setTheProtectionOf(ObjectValue object, boolean protectedNow,
            boolean deeply, boolean onlyTheWords, Set<Object> reached) {

        if (!reached.add(object.context())) {
            return;
        }
        if (!onlyTheWords) {
            object.context().closeToNewNames(protectedNow);
        }
        object.context().slots().forEach(slot -> {
            if (protectedNow) {
                slot.protectFromAssignment();
            } else {
                slot.allowAssignment();
            }
            if (deeply && !slot.canonical().equals("self") && isReachedByADeepProtection(slot.value())) {
                setProtection(slot.value(), protectedNow, true, false, reached);
            }
        });
    }

    private boolean isReachedByADeepProtection(Value value) {
        return value instanceof BlockValue || value instanceof AnyStringValue || value instanceof BinaryValue
                || value instanceof MapValue || value instanceof ObjectValue
                || value instanceof BitsetValue || value instanceof VectorValue;
    }

    private boolean carriesProtection(Value value) {
        return value instanceof RebolSeries
                || value instanceof ObjectValue
                || value instanceof MapValue;
    }
}
