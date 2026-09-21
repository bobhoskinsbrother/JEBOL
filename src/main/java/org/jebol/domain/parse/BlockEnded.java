package org.jebol.domain.parse;

final class BlockEnded extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean asAMatch;

    private BlockEnded(boolean asAMatch) {
        super(null, null, false, false);
        this.asAMatch = asAMatch;
    }

    static BlockEnded asAMatch() {
        return new BlockEnded(true);
    }

    static BlockEnded asAFailure() {
        return new BlockEnded(false);
    }

    boolean endedAsAMatch() {
        return asAMatch;
    }
}
