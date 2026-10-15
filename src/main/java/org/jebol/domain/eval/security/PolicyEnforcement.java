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
        Optional<Context> state = theSystemState();
        if (state.isEmpty()) {
            return;
        }
        PolicyLevel level = new SecurityPolicies(theField(state.get(), "policies"), fileNamesFoldCase())
                .levelFor(kind, access, subject);
        switch (level) {
            case ALLOW -> theAccessGoesAhead();
            case ASK -> refuseUnlessConfirmed(state.get(), kind, access, subject);
            case THROW -> throw violation(kind, subject);
            case QUIT -> throw new QuitRequested(IntegerValue.of(WHAT_A_QUIT_POLICY_LEAVES_WITH));
        }
    }

    private void theAccessGoesAhead() {
    }

    private Optional<Context> theSystemState() {
        Context lib = evaluator.systemContext();
        if (!lib.knows("system")
                || !(lib.valueAt("system", "state") instanceof ObjectValue(Context state))) {
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private Value theField(Context state, String field) {
        return state.hasItsOwnSlotFor(field) ? state.ownSlotFor(field).value() : NoneValue.none();
    }

    private boolean fileNamesFoldCase() {
        return evaluator.systemContext().valueAt("system", "platform") instanceof AnyWordValue platform
                && platform.canonical().equals(THE_PLATFORM_WHOSE_FILE_NAMES_FOLD_CASE);
    }

    private void refuseUnlessConfirmed(
            Context state, PolicyClass kind, PolicyAccess access, Optional<Value> subject) {

        Value confirming = theField(state, "confirm-policy");
        Value answer = evaluator.applyFunction(confirming, List.of(
                WordValue.of(kind.word()),
                IntegerValue.of(access.asConfirmPolicyCountsIt()),
                subject.orElseGet(NoneValue::none)));
        if (!(answer instanceof LogicValue confirmed && confirmed.isTruthy())) {
            throw violation(kind, subject);
        }
    }

    private Raised violation(PolicyClass kind, Optional<Value> subject) {
        return Raised.of(EvaluationFailure.SECURITY, subject.orElseGet(() -> WordValue.of(kind.word())));
    }
}
