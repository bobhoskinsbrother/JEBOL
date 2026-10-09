package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

public abstract class SteppingThroughNative extends LoopingNative {

    protected Value walkBySteps(Evaluator evaluator, AnyWordValue word, int step, AnyBlockValue body) {
        ContextSlot slot = word.boundSlot();
        if (slot.value() instanceof NoneValue nothing) {
            return nothing;
        }
        if (!(slot.value() instanceof RebolSeries start)) {
            throw Raised.cannotUse(slot.value(), "forall");
        }
        if (step < 0 && start.index() > start.storageLength()) {
            slot.setValue(start.atIndex(start.storageLength() + 1 + step));
        }
        return answerOfTheLoop(() -> {
            Value last = walkedFrom(evaluator, slot, start.datatype(), step, body);
            slot.setValue(start);
            return last;
        });
    }

    private Value walkedFrom(Evaluator evaluator, ContextSlot slot, Datatype walkingA,
            int step, AnyBlockValue body) {

        Value last = NoneValue.none();
        while (slot.value() instanceof RebolSeries here
                && here.index() >= 1 && here.index() <= here.storageLength()) {
            last = oneRoundCatchingContinue(evaluator, body, evaluator.systemContext());
            if (!(slot.value() instanceof RebolSeries moved)
                    || moved.datatype() != walkingA) {
                throw Raised.cannotUse(slot.value(), "forall");
            }
            if (!steppedOnwards(slot, moved, step)) {
                break;
            }
        }
        return last;
    }

    private boolean steppedOnwards(ContextSlot slot, RebolSeries moved, int step) {
        int next = moved.index() + step;
        if (next > moved.storageLength() && step < 0) {
            next = moved.storageLength() + 1 + step;
        }
        if (next < 1 || next > moved.storageLength()) {
            return false;
        }
        slot.setValue(moved.atIndex(next));
        return true;
    }
}
