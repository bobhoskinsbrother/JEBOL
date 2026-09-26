package org.jebol.domain.eval.arithmetic;

final class And implements BitwiseOperation {

    @Override
    public String spelling() {
        return "and";
    }

    @Override
    public long onWholeElements(long ours, long theirs) {
        return ours & theirs;
    }

    @Override
    public boolean onLogics(boolean ours, boolean theirs) {
        return ours && theirs;
    }
}
