package org.jebol.domain.eval.sets;

final class Exclude implements SetOperation {

    @Override
    public String spelling() {
        return "exclude";
    }

    @Override
    public boolean theFirstSetKeeps(boolean inTheirs) {
        return !inTheirs;
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
        return mine & ~yours;
    }
}
