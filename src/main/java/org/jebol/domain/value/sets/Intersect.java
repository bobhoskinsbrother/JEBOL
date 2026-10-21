package org.jebol.domain.value.sets;

final class Intersect implements SetOperation {

    @Override
    public String spelling() {
        return "intersect";
    }

    @Override
    public boolean keepsFromFirstSet(boolean inTheirs) {
        return inTheirs;
    }

    @Override
    public boolean takesFromSecondSet() {
        return false;
    }

    @Override
    public boolean keepsFromSecondSet(boolean inOurs) {
        return false;
    }

    @Override
    public int combineBits(int mine, int yours) {
        return mine & yours;
    }
}
