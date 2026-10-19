package org.jebol.domain.value.sets;

final class Exclude implements SetOperation {

    @Override
    public String spelling() {
        return "exclude";
    }

    @Override
    public boolean keepsFromTheFirstSet(boolean inTheirs) {
        return !inTheirs;
    }

    @Override
    public boolean takesFromTheSecondSet() {
        return false;
    }

    @Override
    public boolean keepsFromTheSecondSet(boolean inOurs) {
        return false;
    }

    @Override
    public int combineTheBits(int mine, int yours) {
        return mine & ~yours;
    }
}
