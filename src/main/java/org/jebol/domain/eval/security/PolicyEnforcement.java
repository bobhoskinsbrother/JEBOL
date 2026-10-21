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

    private static final int WHAT_A_QUIT_POLICY_LEAVES_WITH = 101;

    private static final String THE_PLATFORM_WHOSE_FILE_NAMES_FOLD_CASE = "windows";

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
            case ALLOW -> letTheAccessGoAhead();
            case ASK -> refuseUnlessConfirmed(state.get(), kind, access, subject);
            case THROW -> throw refuseAsAViolation(kind, subject);
            case QUIT -> throw new QuitRequested(IntegerValue.of(WHAT_A_QUIT_POLICY_LEAVES_WITH));
        }
    }

    private void letTheAccessGoAhead() {
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
                && platform.canonical().equals(THE_PLATFORM_WHOSE_FILE_NAMES_FOLD_CASE);
    }

    private void refuseUnlessConfirmed(
            Context state, PolicyClass kind, PolicyAccess access, Optional<Value> subject) {

        Value confirming = readField(state, "confirm-policy");
        Value answer = evaluator.applyFunction(confirming, List.of(
                WordValue.of(kind.word()),
                IntegerValue.of(access.asConfirmPolicyCountsIt()),
                subject.orElseGet(NoneValue::none)));
        if (!(answer instanceof LogicValue confirmed && confirmed.isTruthy())) {
            throw refuseAsAViolation(kind, subject);
        }
    }

    private Raised refuseAsAViolation(PolicyClass kind, Optional<Value> subject) {
        return Raised.of(EvaluationFailure.SECURITY, subject.orElseGet(() -> WordValue.of(kind.word())));
    }
}
