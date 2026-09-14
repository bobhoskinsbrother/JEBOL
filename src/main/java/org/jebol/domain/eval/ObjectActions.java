package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;

public final class ObjectActions implements Actions {

    private final ObjectValue object;

    public ObjectActions(ObjectValue object) {
        this.object = object;
    }

    @Override
    public Value subject() {
        return object;
    }

    @Override
    public int length() {
        return object.context().fieldCount();
    }

    @Override
    public Value append(Asked asked) {
        return gainingFields(asked, "append");
    }

    @Override
    public Value insert(Asked asked) {
        return gainingFields(asked, "insert");
    }

    private Value gainingFields(Asked asked, String nativeName) {
        if (object.context().isClosedToNewNames()) {
            throw Raised.of(EvaluationFailure.PROTECTED, nativeName);
        }
        Natives.refuseHiddenField(object, asked.given());
        if (asked.given() instanceof WordValue only) {
            Natives.refuseTheSelfTheObjectAlreadyHas(object, only);
            object.context().set(only.canonical(), UnsetValue.unset());
            return object;
        }
        List<Value> pairs = asked.duplicated() instanceof BlockValue added
                ? asked.theWantedItemsOf(added)
                : List.of(asked.given());
        Natives.refuseTheObjectsOwnSelfBeforeAnyFieldIsAdded(object, pairs);
        for (int at = 0; at + 1 < pairs.size(); at += 2) {
            if (pairs.get(at) instanceof WordValue field) {
                object.context().set(field.canonical(), pairs.get(at + 1));
            }
        }
        return object;
    }
}
