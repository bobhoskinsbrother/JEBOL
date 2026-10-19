package org.jebol.domain.value.sets;

final class Difference implements SetOperation {

    @Override
    public String spelling() {
        return "difference";
    }

    @Override
    public boolean keepsFromTheFirstSet(boolean inTheirs) {
        return !inTheirs;
    }

    @Override
    public boolean takesFromTheSecondSet() {
        return true;
    }

    @Override
    public boolean keepsFromTheSecondSet(boolean inOurs) {
        return !inOurs;
    }

    @Override
    public int combineTheBits(int mine, int yours) {
        return mine ^ yours;
    }
}
