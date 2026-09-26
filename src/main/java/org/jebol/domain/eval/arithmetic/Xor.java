package org.jebol.domain.eval.arithmetic;

final class Xor implements BitwiseOperation {

    @Override
    public String spelling() {
        return "xor";
    }

    @Override
    public long onWholeElements(long ours, long theirs) {
        return ours ^ theirs;
    }

    @Override
    public boolean onLogics(boolean ours, boolean theirs) {
        return ours ^ theirs;
    }
}
