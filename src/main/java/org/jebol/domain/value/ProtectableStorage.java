package org.jebol.domain.value;

public abstract class ProtectableStorage {

    private boolean isProtected;

    public boolean isProtected() {
        return isProtected;
    }

    public void protectFromChange(boolean protectedNow) {
        this.isProtected = protectedNow;
    }

    private long changesAdmitted;

    public long changesAdmitted() {
        return changesAdmitted;
    }

    protected void admitChange() {
        if (isProtected) {
            throw new ProtectedFromChange();
        }
        changesAdmitted++;
    }
}
