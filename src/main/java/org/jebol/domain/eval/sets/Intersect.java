package org.jebol.domain.eval.sets;

final class Intersect implements SetOperation {

    @Override
    public String spelling() {
        return "intersect";
    }

    @Override
    public boolean theFirstSetKeeps(boolean inTheirs) {
        return inTheirs;
    }

    @Override
    public boolean theSecondSetContributes() {
        return false;
    }

    @Override
    public boolean theSecondSetKeeps(boolean inOurs) {
        return false;
    }

    @Override
    public int combinedBits(int mine, int yours) {
        return mine & yours;
    }
}
