package org.jebol.domain.value.sets;

final class Union implements SetOperation {

    @Override
    public String spelling() {
        return "union";
    }

    @Override
    public boolean keepsFromFirstSet(boolean inTheirs) {
        return true;
    }

    @Override
    public boolean takesFromSecondSet() {
        return true;
    }

    @Override
    public boolean keepsFromSecondSet(boolean inOurs) {
        return true;
    }

    @Override
    public int combineBits(int mine, int yours) {
        return mine | yours;
    }
}
