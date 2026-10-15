package org.jebol.domain.eval.security;

public enum PolicyAccess {
    READ, WRITE, EXECUTE;

    public int asConfirmPolicyCountsIt() {
        return ordinal() + 1;
    }
}
