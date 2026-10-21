package org.jebol.domain.value.sets;

final class Union implements SetOperation {

    @Override
    public String spelling() {
        return "union";
    }

    @Override
    public boolean keepsFromTheFirstSet(boolean inTheirs) {
        return true;
    }

    @Override
    public boolean takesFromTheSecondSet() {
        return true;
    }

    @Override
    public boolean keepsFromTheSecondSet(boolean inOurs) {
        return true;
    }

    @Override
    public int combineBits(int mine, int yours) {
        return mine | yours;
    }
}
