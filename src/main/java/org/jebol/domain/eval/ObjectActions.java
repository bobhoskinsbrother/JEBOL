package org.jebol.domain.eval;

import org.jebol.domain.value.*;

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
            throw Raised.of(EvaluationFailure.PROTECTED);
        }
        object.refuseHiddenFieldsIn(asked.given());
        if (asked.given() instanceof AnyWordValue only) {
            if (object.context().hasItsOwnSelf()) {
                only.refuseToBeWrittenWhenItNamesSelf();
            }
            object.context().register(only.canonical(), UnsetValue.unset());
            return object;
        }
        List<Value> pairs = asked.duplicated() instanceof AnyBlockValue added
                ? asked.theWantedItemsOf(added)
                : List.of(asked.given());
        for (int at1 = 0; at1 + 1 < pairs.size(); at1 += 2) {
            if (object.context().hasItsOwnSelf()) {
                pairs.get(at1).refuseToBeWrittenWhenItNamesSelf();
            }
        }
        for (int at = 0; at + 1 < pairs.size(); at += 2) {
            if (pairs.get(at) instanceof AnyWordValue field) {
                object.context().register(field.canonical(), pairs.get(at + 1));
            }
        }
        return object;
    }
}
