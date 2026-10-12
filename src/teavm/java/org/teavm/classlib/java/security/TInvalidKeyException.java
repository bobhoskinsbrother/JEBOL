package org.teavm.classlib.java.security;

public class TInvalidKeyException extends TGeneralSecurityException {

    private static final long serialVersionUID = 1L;

    public TInvalidKeyException() {
    }

    public TInvalidKeyException(String message) {
        super(message);
    }

    public TInvalidKeyException(String message, org.teavm.classlib.java.lang.TThrowable cause) {
        super(message, cause);
    }

    public TInvalidKeyException(org.teavm.classlib.java.lang.TThrowable cause) {
        super(cause);
    }
}
