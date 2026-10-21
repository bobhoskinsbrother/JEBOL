package org.jebol.domain.parse;

public final class BlockEnded extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean asAMatch;

    private BlockEnded(boolean asAMatch) {
        super(null, null, false, false);
        this.asAMatch = asAMatch;
    }

    public static BlockEnded asMatch() {
        return new BlockEnded(true);
    }

    public static BlockEnded asFailure() {
        return new BlockEnded(false);
    }

    public boolean endedAsMatch() {
        return asAMatch;
    }
}
