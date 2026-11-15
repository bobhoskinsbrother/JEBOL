package org.jebol.domain.eval;

import java.io.Serial;

/**
 * What a {@link CompressionMethod} throws where its C would answer FALSE, carrying the error number it would set, or
 * where its C raises {@code bad-press} naming the method it was asked for, as Crush's decoder does with too few bytes.
 */
public final class CompressionRefused extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    private final boolean namesMethod;

    public CompressionRefused(int code) {
        this(code, false);
    }

    private CompressionRefused(int code, boolean namesMethod) {
        super(namesMethod ? "compression refused naming its method" : "compression refused with " + code,
                null, false, false);
        this.code = code;
        this.namesMethod = namesMethod;
    }

    public static CompressionRefused namingMethod() {
        return new CompressionRefused(0, true);
    }

    public int code() {
        return code;
    }

    public boolean namesMethod() {
        return namesMethod;
    }
}
