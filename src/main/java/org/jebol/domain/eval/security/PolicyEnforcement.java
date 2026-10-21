package org.jebol.domain.eval.security;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.QuitRequested;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;

public final class PolicyEnforcement {

    private static final int QUIT_POLICY_EXIT_CODE = 101;

    private static final String CASE_FOLDING_PLATFORM = "windows";

    private final Evaluator evaluator;

    public PolicyEnforcement(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void require(PolicyClass kind, PolicyAccess access, Optional<Value> subject) {
        Optional<Context> state = findTheSystemState();
        if (state.isEmpty()) {
            return;
        }
        PolicyLevel level = new SecurityPolicies(readField(state.get(), "policies"), fileNamesFoldCase())
                .findLevelFor(kind, access, subject);
        switch (level) {
            case ALLOW -> letAccessGoAhead();
            case ASK -> refuseUnlessConfirmed(state.get(), kind, access, subject);
            case THROW -> throw refuseAsViolation(kind, subject);
            case QUIT -> throw new QuitRequested(IntegerValue.of(QUIT_POLICY_EXIT_CODE));
        }
    }

    private void letAccessGoAhead() {
    }

    private Optional<Context> findTheSystemState() {
        Context lib = evaluator.systemContext();
        if (!lib.contains("system")
                || !(lib.readValueAt("system", "state") instanceof ObjectValue(Context state))) {
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private Value readField(Context state, String field) {
        return state.hasItsOwnSlotFor(field) ? state.ownSlotFor(field).value() : NoneValue.none();
    }

    private boolean fileNamesFoldCase() {
        return evaluator.systemContext().readValueAt("system", "platform") instanceof AnyWordValue platform
                && platform.canonical().equals(CASE_FOLDING_PLATFORM);
    }

    private void refuseUnlessConfirmed(
            Context state, PolicyClass kind, PolicyAccess access, Optional<Value> subject) {

        Value confirming = readField(state, "confirm-policy");
        Value answer = evaluator.applyFunction(confirming, List.of(
                WordValue.of(kind.word()),
                IntegerValue.of(access.asConfirmPolicyCountsIt()),
                subject.orElseGet(NoneValue::none)));
        if (!(answer instanceof LogicValue confirmed && confirmed.isTruthy())) {
            throw refuseAsViolation(kind, subject);
        }
    }

    private Raised refuseAsViolation(PolicyClass kind, Optional<Value> subject) {
        return Raised.of(EvaluationFailure.SECURITY, subject.orElseGet(() -> WordValue.of(kind.word())));
    }
}
