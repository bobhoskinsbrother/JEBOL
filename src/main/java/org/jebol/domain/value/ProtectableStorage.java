package org.jebol.domain.value;

public abstract class ProtectableStorage {

    private boolean isProtected;

    public boolean isProtected() {
        return isProtected;
    }

    public void protectFromChange(boolean protectedNow) {
        this.isProtected = protectedNow;
    }

    protected void refuseIfProtected() {
        if (isProtected) {
            throw new ProtectedFromChange();
        }
    }
}
