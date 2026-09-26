package org.jebol.domain.eval.sets;

final class Difference implements SetOperation {

    @Override
    public String spelling() {
        return "difference";
    }

    @Override
    public boolean theFirstSetKeeps(boolean inTheirs) {
        return !inTheirs;
    }

    @Override
    public boolean theSecondSetContributes() {
        return true;
    }

    @Override
    public boolean theSecondSetKeeps(boolean inOurs) {
        return !inOurs;
    }

    @Override
    public int combinedBits(int mine, int yours) {
        return mine ^ yours;
    }
}
