package org.jebol.domain.eval.sets;

final class Union implements SetOperation {

    @Override
    public String spelling() {
        return "union";
    }

    @Override
    public boolean theFirstSetKeeps(boolean inTheirs) {
        return true;
    }

    @Override
    public boolean theSecondSetContributes() {
        return true;
    }

    @Override
    public boolean theSecondSetKeeps(boolean inOurs) {
        return true;
    }

    @Override
    public int combinedBits(int mine, int yours) {
        return mine | yours;
    }
}
