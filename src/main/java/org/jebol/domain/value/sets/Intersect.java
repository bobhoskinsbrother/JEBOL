package org.jebol.domain.value.sets;

final class Intersect implements SetOperation {

    @Override
    public String spelling() {
        return "intersect";
    }

    @Override
    public boolean keepsFromTheFirstSet(boolean inTheirs) {
        return inTheirs;
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
    public int combineBits(int mine, int yours) {
        return mine & yours;
    }
}
