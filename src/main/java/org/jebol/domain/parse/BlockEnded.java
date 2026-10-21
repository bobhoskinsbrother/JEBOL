package org.jebol.domain.parse;

public final class BlockEnded extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean asMatch;

    private BlockEnded(boolean asMatch) {
        super(null, null, false, false);
        this.asMatch = asMatch;
    }

    public static BlockEnded asMatch() {
        return new BlockEnded(true);
    }

    public static BlockEnded asFailure() {
        return new BlockEnded(false);
    }

    public boolean endedAsMatch() {
        return asMatch;
    }
}
